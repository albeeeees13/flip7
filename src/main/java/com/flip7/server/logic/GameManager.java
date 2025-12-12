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
        for(ClientHandler j : jugadores) cartasJugadores.get(j).clear();
        broadcast(new Mensaje(TipoMensaje.INICIO_JUEGO, "¡Empieza la ronda!"));
        iniciarTurno();
    }

    private void iniciarTurno() {
        if (esperandoObjetivo) return;

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

    // Lógica central de sacar carta (Retorna true si sigue vivo, false si Bust)
    private boolean ejecutarRobo(ClientHandler jugador) {
        Carta carta = mazo.robarCarta();
        if (carta == null) return false; // Fin mazo

        List<Carta> susCartas = cartasJugadores.get(jugador);

        // 1. REGLA BUST
        boolean esBust = reglas.verificarBust(susCartas, carta);

        // Agregar visualmente primero
        susCartas.add(carta);
        enviarEstadoJuego();

        if (esBust) {
            Optional<Carta> secondChance = susCartas.stream()
                    .filter(c -> c.getAccion() == AccionEspecial.SECOND_CHANCE).findFirst();

            if (secondChance.isPresent()) {
                // SE SALVA
                try { Thread.sleep(1000); } catch (Exception e) {}
                susCartas.remove(secondChance.get());
                susCartas.remove(carta);
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡" + jugador.getNombreUsuario() + " se salvó con SECOND CHANCE!"));
                enviarEstadoJuego();
                return true; // Sigue vivo
            } else {
                // PIERDE
                jugador.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "BUST"));
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡BUST! " + jugador.getNombreUsuario() + " sacó otro " + carta.getValor()));
                susCartas.clear();
                // No actualizamos estado inmediatamente para dejar ver el gris
                return false; // Murió
            }
        }

        // Si sale ESPECIAL que requiere objetivo, devolvemos true pero activamos flag
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
            siguienteTurno();
        } else if (!esperandoObjetivo) {
            // Si sigue vivo y no está eligiendo objetivo, su turno continúa
            iniciarTurno();
        }
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
                    destino = h;
                    break;
                }
            }
        }

        AccionEspecial accion = cartaEspecialPendiente.getAccion();
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, origen.getNombreUsuario() + " usó " + accion + " en " + destino.getNombreUsuario()));


        if (accion == AccionEspecial.FREEZE) {

            broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "❄️ " + destino.getNombreUsuario() + " ha sido CONGELADO y sale de la ronda."));

            // Si se lo aplicó a sí mismo, se planta y pasa turno.
            if (destino.equals(origen)) {
                plantarse(origen);
                return; // plantarse ya cambia de turno
            } else {
                // Si se lo aplicó a otro, ese otro se planta automáticamente ahora mismo
                // OJO: Esto es complejo si no es su turno. Simplificación: Lo marcamos plantado.
                plantarse(destino); // Forzamos el plantarse del rival
                // Y el turno sigue siendo del origen (si no era él mismo)
                iniciarTurno();
            }
        }
        else if (accion == AccionEspecial.FLIP_3) {


            final ClientHandler target = destino;
            new Thread(() -> {
                for (int i = 0; i < 3; i++) {
                    try { Thread.sleep(1000); } catch (Exception e) {} // Pausa dramática


                    boolean vivo = ejecutarRobo(target);

                    if (!vivo) {

                        siguienteTurno();
                        return;
                    }
                }

                if (target.equals(getJugadorActual())) {
                    iniciarTurno();
                } else {
                    // Si ataque a un rival, él comió cartas, pero sigue siendo MI turno
                    iniciarTurno();
                }
            }).start();
        }
        else {
            // Second chance y otros no requieren acción inmediata aquí
            cartaEspecialPendiente = null;
            iniciarTurno();
        }

        cartaEspecialPendiente = null;
    }

    private void plantarse(ClientHandler jugador) {
        timerTurno.cancel();
        int puntos = reglas.calcularPuntosMesa(cartasJugadores.get(jugador));
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, jugador.getNombreUsuario() + " se plantó con " + puntos));

        // TODO: Marcar jugador como inactivo para esta ronda
        siguienteTurno();
    }

    private void plantarseAutomatico() {
        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(TipoMensaje.ERROR, "¡Tiempo agotado!"));
        plantarse(actual);
    }

    private void siguienteTurno() {
        indiceTurno = (indiceTurno + 1) % jugadores.size();
        iniciarTurno();
    }

    private ClientHandler getJugadorActual() { return jugadores.get(indiceTurno); }

    private void broadcast(Mensaje msg) {
        for (ClientHandler j : jugadores) j.enviarMensaje(msg);
    }

    // --- ESTO ARREGLA QUE NO VEAS A LOS RIVALES ---
    private void enviarEstadoJuego() {
        // 1. Enviar mapa de TODOS para la vista de oponentes
        // Convertimos Map<ClientHandler, List> a Map<String, List> (Nombres)
        Map<String, List<Carta>> estadoGlobal = new HashMap<>();
        for (Map.Entry<ClientHandler, List<Carta>> entry : cartasJugadores.entrySet()) {
            estadoGlobal.put(entry.getKey().getNombreUsuario(), new ArrayList<>(entry.getValue()));
        }
        broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_OPONENTES, estadoGlobal));

        // 2. Enviar mesa personal a cada uno
        for(ClientHandler j : jugadores) {
            j.enviarMensaje(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, cartasJugadores.get(j)));
        }
    }
}