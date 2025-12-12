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
    private List<ClientHandler> jugadoresEnRonda;
    private int indiceTurno = 0;
    private boolean juegoIniciado = false;
    private MotorReglas reglas;
    private Map<ClientHandler, List<Carta>> cartasJugadores;
    private Mazo mazo;
    private Timer timerTurno;

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
        jugadoresEnRonda = new ArrayList<>(jugadores);
        for(ClientHandler j : jugadores) cartasJugadores.get(j).clear();

        broadcast(new Mensaje(TipoMensaje.INICIO_JUEGO, "¡Empieza la ronda!"));
        enviarEstadoJuego(); // Limpia tableros visualmente
        iniciarTurno();
    }

    private void iniciarTurno() {
        if (esperandoObjetivo) return;

        // Si no queda nadie vivo, fin de ronda
        if (jugadoresEnRonda.isEmpty()) {
            new Thread(this::finDeRonda).start();
            return;
        }

        if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;

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

    // --- AQUÍ ESTÁ LA SOLUCIÓN DEL BUST ---
    private boolean ejecutarRobo(ClientHandler jugador) {
        Carta carta = mazo.robarCarta();
        if (carta == null) return false;

        List<Carta> susCartas = cartasJugadores.get(jugador);

        // 1. AGREGAMOS LA CARTA Y ACTUALIZAMOS (Para que veas el 4 repetido)
        susCartas.add(carta);
        enviarEstadoJuego();

        // PAUSA 1: Esperamos 1.5 seg para que veas qué carta salió
        try { Thread.sleep(1500); } catch (Exception e) {}

        // 2. VERIFICAMOS BUST
        boolean esBust = reglas.verificarBust(susCartas, carta);

        if (esBust) {
            Optional<Carta> secondChance = susCartas.stream()
                    .filter(c -> c.getAccion() == AccionEspecial.SECOND_CHANCE).findFirst();

            if (secondChance.isPresent()) {
                // SE SALVA
                susCartas.remove(secondChance.get());
                susCartas.remove(carta);
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡" + jugador.getNombreUsuario() + " se salvó con SECOND CHANCE!"));

                // Actualizamos mesa (se borran las cartas malas)
                enviarEstadoJuego();
                return true;
            } else {
                // --- BUST CONFIRMADO ---

                // 3. ENVIAMOS SEÑAL DE GRIS (Pero NO borramos cartas todavía)
                jugador.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "BUST"));
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡BUST! " + jugador.getNombreUsuario() + " sacó otro " + carta.getValor()));

                // PAUSA 2: Esperamos 3 SEGUNDOS viendo las cartas grises (Humillación)
                try { Thread.sleep(3000); } catch (Exception e) {}

                // 4. AHORA SÍ LIMPIAMOS TODO
                susCartas.clear();

                // Sacar de la ronda
                jugadoresEnRonda.remove(jugador);
                if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;

                // 5. Enviamos mesa vacía
                enviarEstadoJuego();

                return false; // Murió
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
        // IMPORTANTE: Hilo aparte para que los Thread.sleep NO congelen a los otros jugadores
        new Thread(() -> {
            boolean sigueVivo = ejecutarRobo(jugador);

            if (!sigueVivo) {
                // Si murió, pasamos al siguiente
                iniciarTurno();
            } else if (!esperandoObjetivo) {
                // Si vive y no hay popup, sigue su turno
                iniciarTurno();
            }
        }).start();
    }

    private void activarSeleccionObjetivo(ClientHandler jugador, Carta carta) {
        esperandoObjetivo = true;
        cartaEspecialPendiente = carta;
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
            broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "❄️ " + destino.getNombreUsuario() + " congelado."));
            if (destino.equals(origen)) {
                plantarse(origen);
            } else {
                plantarse(destino);
                iniciarTurno();
            }
        }
        else if (accion == AccionEspecial.FLIP_3) {
            final ClientHandler target = destino;
            new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    if (!jugadoresEnRonda.contains(target)) break;
                    boolean vivo = ejecutarRobo(target);
                    if (!vivo) break;
                }
                // Al terminar las 3 cartas, volvemos al flujo normal
                if (target.equals(getJugadorActual()) && jugadoresEnRonda.contains(target)) {
                    iniciarTurno();
                } else {
                    iniciarTurno();
                }
            }).start();
        }

        cartaEspecialPendiente = null;
    }

    private void plantarse(ClientHandler jugador) {
        if(timerTurno != null) timerTurno.cancel();
        int puntos = reglas.calcularPuntosMesa(cartasJugadores.get(jugador));
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, jugador.getNombreUsuario() + " se plantó con " + puntos));
        jugadoresEnRonda.remove(jugador);
        if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;

        // Hilo aparte para no bloquear si plantarse fue llamado desde un evento síncrono
        new Thread(this::iniciarTurno).start();
    }

    private void plantarseAutomatico() {
        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(TipoMensaje.ERROR, "¡Tiempo agotado!"));
        plantarse(actual);
    }

    private void finDeRonda() {
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "--- FIN DE LA RONDA ---"));
        // PAUSA LARGA DE 5 SEGUNDOS PARA VER RESULTADOS
        try { Thread.sleep(5000); } catch (Exception e) {}
        iniciarPartida();
    }

    private ClientHandler getJugadorActual() {
        if (jugadoresEnRonda.isEmpty()) return null;
        return jugadoresEnRonda.get(indiceTurno);
    }

    private void broadcast(Mensaje msg) {
        for (ClientHandler j : jugadores) j.enviarMensaje(msg);
    }

    private void enviarEstadoJuego() {
        Map<String, List<Carta>> estadoGlobal = new HashMap<>();
        for (ClientHandler h : jugadores) {
            estadoGlobal.put(h.getNombreUsuario(), new ArrayList<>(cartasJugadores.get(h)));
        }
        broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_OPONENTES, estadoGlobal));

        for(ClientHandler j : jugadores) {
            j.enviarMensaje(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, cartasJugadores.get(j)));
        }
    }
}