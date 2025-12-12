package com.flip7.server.logic;

import com.flip7.common.model.Carta;
import com.flip7.common.model.Carta.AccionEspecial;
import com.flip7.common.model.Carta.Tipo;
import com.flip7.common.network.Mensaje;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.server.network.ClientHandler;

import java.util.*;
import java.util.stream.Collectors;

public class GameManager {
    private String idSala;
    private List<ClientHandler> jugadores;
    private List<ClientHandler> jugadoresEnRonda; // Nueva lista para saber quién sigue vivo
    private int indiceTurno = 0;
    private boolean juegoIniciado = false;
    private MotorReglas reglas;
    private Map<ClientHandler, List<Carta>> cartasJugadores;
    private Mazo mazo;
    private Timer timerTurno;

    // Estado especial
    private boolean esperandoObjetivo = false;
    private Carta cartaEspecialPendiente = null;

    public GameManager(String idSala) {
        this.idSala = idSala;
        this.jugadores = new ArrayList<>();
        this.cartasJugadores = new HashMap<>();
        this.reglas = new MotorReglas();
        this.mazo = new Mazo();
    }

    public synchronized void agregarJugador(ClientHandler jugador) {
        jugadores.add(jugador);
        cartasJugadores.put(jugador, new ArrayList<>());
        if (jugadores.size() >= 2 && !juegoIniciado) {
            iniciarPartida();
        }
    }

    private void iniciarPartida() {
        juegoIniciado = true;
        mazo = new Mazo();
        jugadoresEnRonda = new ArrayList<>(jugadores); // Todos empiezan vivos
        for(ClientHandler j : jugadores) cartasJugadores.get(j).clear();

        broadcast(new Mensaje(TipoMensaje.INICIO_JUEGO, "¡Empieza la ronda!"));
        enviarEstadoJuego(); // IMPORTANTE: Enviar estado inicial
        iniciarTurno();
    }

    private void iniciarTurno() {
        if (esperandoObjetivo) return;

        // Validar que queden jugadores en la ronda
        if (jugadoresEnRonda.isEmpty()) {
            finDeRonda();
            return;
        }

        // Asegurar índice válido
        if (indiceTurno >= jugadoresEnRonda.size()) {
            indiceTurno = 0;
        }

        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));

        if (timerTurno != null) timerTurno.cancel();
        timerTurno = new Timer();
        timerTurno.schedule(new TimerTask() {
            @Override
            public void run() { plantarseAutomatico(); }
        }, 30000);
    }

    public synchronized void procesarJugada(ClientHandler solicitante, Mensaje msj) {
        TipoMensaje tipo = msj.getTipo();

        // Si el jugador ya no está en la ronda (congelado o plantado), ignorar
        if (!jugadoresEnRonda.contains(solicitante)) return;

        if (!solicitante.equals(getJugadorActual()) && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) {
            solicitante.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "No es tu turno"));
            return;
        }
        if (esperandoObjetivo && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) return;

        switch (tipo) {
            case ACCION_SACAR: sacarCarta(solicitante); break;
            case ACCION_PLANTARSE: plantarse(solicitante); break;
            case SELECCIONAR_OBJETIVO:
                String objetivo = (String) msj.getContenido();
                aplicarEfectoEspecial(solicitante, objetivo);
                break;
        }
    }

    private boolean ejecutarRobo(ClientHandler jugador) {
        Carta carta = mazo.robarCarta();
        if (carta == null) return false;

        List<Carta> susCartas = cartasJugadores.get(jugador);

        // 1. PRIMERO AGREGAMOS LA CARTA Y ENVIAMOS ESTADO
        // Esto soluciona que "no aparezcan las cartas"
        susCartas.add(carta);
        enviarEstadoJuego();

        // Pausa pequeña para que se vea la carta salir
        try { Thread.sleep(500); } catch (Exception e) {}

        // 2. REGLA BUST
        boolean esBust = reglas.verificarBust(susCartas, carta);

        if (esBust) {
            Optional<Carta> secondChance = susCartas.stream()
                    .filter(c -> c.getAccion() == AccionEspecial.SECOND_CHANCE).findFirst();

            if (secondChance.isPresent()) {
                // SE SALVA
                try { Thread.sleep(1000); } catch (Exception e) {}
                susCartas.remove(secondChance.get());
                susCartas.remove(carta);
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡" + jugador.getNombreUsuario() + " se salvó con SECOND CHANCE!"));
                enviarEstadoJuego(); // Actualizar mesa limpia
                return true;
            } else {
                // PIERDE
                jugador.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "BUST"));
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡BUST! " + jugador.getNombreUsuario() + " sacó otro " + carta.getValor()));

                susCartas.clear(); // Pierde todo

                // Sacar de la ronda
                jugadoresEnRonda.remove(jugador);
                // Ajustar índice si es necesario
                if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;

                return false;
            }
        }

        // Si sale ESPECIAL
        if (carta.getTipo() == Tipo.ACCION &&
                (carta.getAccion() == AccionEspecial.FREEZE || carta.getAccion() == AccionEspecial.FLIP_3)) {
            activarSeleccionObjetivo(jugador, carta);
        }

        return true;
    }

    private void sacarCarta(ClientHandler jugador) {
        timerTurno.cancel();
        boolean sigueVivo = ejecutarRobo(jugador);

        if (!sigueVivo) {
            // Si murió, ya lo sacamos de la lista en ejecutarRobo, solo iniciamos turno del siguiente
            iniciarTurno();
        } else if (!esperandoObjetivo) {
            // Si sigue vivo y NO está eligiendo objetivo, sigue su turno
            iniciarTurno();
        }
    }

    private void activarSeleccionObjetivo(ClientHandler jugador, Carta carta) {
        esperandoObjetivo = true;
        cartaEspecialPendiente = carta;
        // Rivales disponibles (cualquiera en la partida, incluso los plantados, según reglas)
        List<String> rivales = jugadores.stream()
                .map(ClientHandler::getNombreUsuario)
                .filter(n -> !n.equals(jugador.getNombreUsuario()))
                .collect(Collectors.toList());

        List<String> payload = new ArrayList<>();
        payload.add(carta.getAccion().toString());
        payload.addAll(rivales);
        jugador.enviarMensaje(new Mensaje(TipoMensaje.SOLICITAR_OBJETIVO, payload.toArray(new String[0])));
    }

    private void aplicarEfectoEspecial(ClientHandler origen, String nombreDestino) {
        esperandoObjetivo = false;
        ClientHandler destino = origen;

        if (!nombreDestino.equals("SELF")) {
            for (ClientHandler h : jugadores) {
                if (h.getNombreUsuario().equals(nombreDestino)) {
                    destino = h; break;
                }
            }
        }

        AccionEspecial accion = cartaEspecialPendiente.getAccion();
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, origen.getNombreUsuario() + " usó " + accion + " en " + destino.getNombreUsuario()));

        if (accion == AccionEspecial.FREEZE) {

            if (destino.equals(origen)) {

                plantarse(origen);
            } else {

                int puntos = reglas.calcularPuntosMesa(cartasJugadores.get(destino));
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "❄️ " + destino.getNombreUsuario() + " fue CONGELADO con " + puntos + " puntos."));


                jugadoresEnRonda.remove(destino);


                if (jugadores.indexOf(destino) < indiceTurno) {
                    indiceTurno--;
                }
                
                enviarEstadoJuego();
                iniciarTurno();
            }
        }
        else if (accion == AccionEspecial.FLIP_3) {
            // FLIP 3 en hilo aparte
            final ClientHandler target = destino;
            new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    // Verificar si sigue vivo antes de cada carta
                    if (!jugadoresEnRonda.contains(target)) break;

                    boolean vivo = ejecutarRobo(target);
                    if (!vivo) break; // Si muere, para
                }
                // Al terminar las 3 cartas (si vivió):
                // Si me ataqué a mí mismo, sigue mi turno.
                // Si ataqué a otro, sigue MI turno (el origen).
                enviarEstadoJuego();
                iniciarTurno();
            }).start();
        }

        cartaEspecialPendiente = null;
    }

    private void plantarse(ClientHandler jugador) {
        timerTurno.cancel();
        int puntos = reglas.calcularPuntosMesa(cartasJugadores.get(jugador));
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, jugador.getNombreUsuario() + " se plantó con " + puntos));

        // Sacar de la ronda activa
        jugadoresEnRonda.remove(jugador);

        // Revisar índice
        if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;

        iniciarTurno(); // Pasa al siguiente que quede vivo
    }

    private void plantarseAutomatico() {
        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(TipoMensaje.ERROR, "¡Tiempo agotado!"));
        plantarse(actual);
    }

    private void finDeRonda() {
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "--- FIN DE LA RONDA ---"));
        // Aquí iría la lógica para reiniciar manos, ver quién ganó 200pts, etc.
        // Por ahora reiniciamos simple:
        try { Thread.sleep(3000); } catch (Exception e) {}
        iniciarPartida();
    }

    private ClientHandler getJugadorActual() {
        if (jugadoresEnRonda.isEmpty()) return null;
        if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;
        return jugadoresEnRonda.get(indiceTurno);
    }

    private void broadcast(Mensaje msg) {
        for (ClientHandler j : jugadores) j.enviarMensaje(msg);
    }

    private void enviarEstadoJuego() {

        Map<String, List<Carta>> estadoGlobal = new HashMap<>();
        for (ClientHandler h : jugadores) { // Mostramos cartas de TODOS (incluso plantados)
            estadoGlobal.put(h.getNombreUsuario(), new ArrayList<>(cartasJugadores.get(h)));
        }
        broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_OPONENTES, estadoGlobal));


        for(ClientHandler j : jugadores) {
            j.enviarMensaje(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, cartasJugadores.get(j)));
        }
    }
}