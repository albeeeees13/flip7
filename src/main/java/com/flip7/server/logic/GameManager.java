package com.flip7.server.logic;

import java.util.stream.Collectors;



import com.flip7.common.model.Carta;
import com.flip7.common.model.Carta.AccionEspecial;
import com.flip7.common.model.Carta.Tipo;
import com.flip7.common.network.Mensaje;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.server.network.ClientHandler;

import java.util.*;

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

    // IMPORTANTE: Variable volatile para controlar bloqueos entre hilos
    private volatile boolean procesandoAccion = false;

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
        System.out.println("[GAME] Iniciando partida...");
        juegoIniciado = true;
        mazo = new Mazo();
        jugadoresEnRonda = new ArrayList<>(jugadores);
        indiceTurno = 0;
        for(ClientHandler j : jugadores) cartasJugadores.get(j).clear();

        broadcast(new Mensaje(TipoMensaje.INICIO_JUEGO, "¡Empieza la ronda!"));
        enviarEstadoJuego();
        iniciarTurno();
    }

    private void iniciarTurno() {
        // --- DESBLOQUEO DE EMERGENCIA ---
        procesandoAccion = false;
        esperandoObjetivo = false;
        // -------------------------------

        if (jugadoresEnRonda.isEmpty()) {
            new Thread(this::finDeRonda).start();
            return;
        }

        // Asegurar índice válido (Matemática circular)
        if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;
        if (indiceTurno < 0) indiceTurno = 0;

        ClientHandler actual = getJugadorActual();
        if (actual != null) {
            System.out.println("[GAME] Turno de: " + actual.getNombreUsuario());
            broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));
        }

        // Reiniciar Timer
        if (timerTurno != null) timerTurno.cancel();
        timerTurno = new Timer();
        timerTurno.schedule(new TimerTask() {
            @Override
            public void run() { plantarseAutomatico(); }
        }, 30000);
    }

    public synchronized void procesarJugada(ClientHandler solicitante, Mensaje msj) {
        // Si el servidor está ocupado animando, ignoramos clics para evitar bugs
        if (procesandoAccion) {
            System.out.println("[GAME] Ignorando clic (Servidor ocupado)...");
            return;
        }

        TipoMensaje tipo = msj.getTipo();

        // Validaciones
        if (!jugadoresEnRonda.contains(solicitante)) return;
        if (!solicitante.equals(getJugadorActual()) && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) {
            solicitante.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "No es tu turno"));
            return;
        }
        if (esperandoObjetivo && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) return;

        // Bloque Try-Catch Global para evitar que el servidor muera
        try {
            switch (tipo) {
                case ACCION_SACAR: sacarCarta(solicitante); break;
                case ACCION_PLANTARSE:
                    sacarDeRonda(solicitante);
                    siguienteTurno();
                    break;
                case SELECCIONAR_OBJETIVO:
                    String objetivo = (String) msj.getContenido();
                    System.out.println("[GAME] Objetivo recibido: " + objetivo);
                    aplicarEfectoEspecial(solicitante, objetivo);
                    break;
            }
        } catch (Exception e) {
            System.err.println("[ERROR CRITICO] " + e.getMessage());
            e.printStackTrace();
            iniciarTurno(); // En caso de error, reiniciamos el turno para desbloquear
        }
    }

    private void sacarCarta(ClientHandler jugador) {
        if (timerTurno != null) timerTurno.cancel();
        procesandoAccion = true; // Bloqueamos inputs

        new Thread(() -> {
            try {
                boolean sigueVivo = ejecutarRoboSeguro(jugador);

                // Volvemos al hilo principal para cambiar turno
                synchronized (this) {
                    if (!sigueVivo) {
                        iniciarTurno(); // Si murió, recalcular turno
                    } else if (!esperandoObjetivo) {
                        siguienteTurno(); // Si vive y es normal, pasa turno (Uno y Uno)
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                synchronized(this) { iniciarTurno(); }
            }
        }).start();
    }

    private boolean ejecutarRoboSeguro(ClientHandler jugador) {
        Carta cartaNueva;
        List<Carta> susCartas;
        boolean esBust;

        // Fase 1: Datos
        synchronized (this) {
            cartaNueva = mazo.robarCarta();
            if (cartaNueva == null) return false;
            susCartas = cartasJugadores.get(jugador);
            esBust = reglas.verificarBust(susCartas, cartaNueva);
            susCartas.add(cartaNueva);
            enviarEstadoJuego();
        }

        // Fase 2: Animación
        try { Thread.sleep(800); } catch (Exception e) {}

        // Fase 3: Resolución
        synchronized (this) {
            if (esBust) {
                // Buscamos Second Chance MANUALMENTE
                int indexSC = -1;
                for (int i = 0; i < susCartas.size(); i++) {
                    if (susCartas.get(i).getAccion() == AccionEspecial.SECOND_CHANCE) {
                        indexSC = i; break;
                    }
                }

                if (indexSC != -1) { // ¡SE SALVA!
                    try { this.wait(1000); } catch (Exception e) {}

                    susCartas.remove(indexSC); // Borramos Second Chance
                    susCartas.remove(cartaNueva); // Borramos la carta mala

                    broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡" + jugador.getNombreUsuario() + " usó SECOND CHANCE!"));
                    enviarEstadoJuego();
                    return true;
                } else { // ¡MUERE!
                    jugador.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "BUST"));
                    broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡BUST! " + jugador.getNombreUsuario() + " sacó otro " + cartaNueva.getValor()));
                }
            } else {
                // Checar especiales
                if (cartaNueva.getTipo() == Tipo.ACCION &&
                        (cartaNueva.getAccion() == AccionEspecial.FREEZE || cartaNueva.getAccion() == AccionEspecial.FLIP_3)) {
                    activarSeleccionObjetivo(jugador, cartaNueva);
                }
                return true;
            }
        }

        // Si llegamos aquí es BUST confirmado
        try { Thread.sleep(2000); } catch (Exception e) {}

        synchronized(this) {
            susCartas.clear();
            sacarDeRonda(jugador);
            enviarEstadoJuego();
        }
        return false;
    }

    private void sacarDeRonda(ClientHandler jugador) {
        if(timerTurno != null) timerTurno.cancel();

        int puntos = reglas.calcularPuntosMesa(cartasJugadores.get(jugador));
        if (puntos > 0 && !cartasJugadores.get(jugador).isEmpty()) {
            broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, jugador.getNombreUsuario() + " termina con " + puntos + " puntos."));
        }

        int indexJugador = jugadoresEnRonda.indexOf(jugador);
        boolean estabaAntesDeMi = indexJugador < indiceTurno;

        jugadoresEnRonda.remove(jugador);

        // Ajustamos el índice si borramos a alguien que ya pasó
        if (estabaAntesDeMi) {
            indiceTurno--;
        }
        if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;
    }

    private void activarSeleccionObjetivo(ClientHandler jugador, Carta carta) {
        if (timerTurno != null) timerTurno.cancel();
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
        // Bloque de seguridad para garantizar desbloqueo
        try {
            esperandoObjetivo = false;
            procesandoAccion = true;

            ClientHandler destino = origen;
            // Buscar destino
            if (nombreDestino != null && !nombreDestino.equals("SELF")) {
                for (ClientHandler h : jugadores) {
                    if (h.getNombreUsuario().equals(nombreDestino)) {
                        destino = h; break;
                    }
                }
            }

            AccionEspecial accion = cartaEspecialPendiente.getAccion();
            broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, origen.getNombreUsuario() + " usó " + accion + " en " + destino.getNombreUsuario()));

            if (accion == AccionEspecial.FREEZE) {
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "❄️ " + destino.getNombreUsuario() + " ha sido CONGELADO."));

                sacarDeRonda(destino); // Lo sacamos

                // Lógica de turno simple:
                // Si me congelé a mí mismo, turno del siguiente.
                // Si congelé a otro, turno del siguiente.
                if (jugadoresEnRonda.isEmpty()) {
                    iniciarTurno(); // Fin
                } else if (destino.equals(origen)) {
                    iniciarTurno(); // Como me fui, iniciarTurno recalcula
                } else {
                    siguienteTurno(); // Avanzar
                }
            }
            else if (accion == AccionEspecial.FLIP_3) {
                final ClientHandler target = destino;
                new Thread(() -> {
                    try {
                        for (int i = 0; i < 3; i++) {
                            boolean vivo;
                            synchronized(this) {
                                if (!jugadoresEnRonda.contains(target)) break;
                            }
                            vivo = ejecutarRoboSeguro(target);
                            if (!vivo) break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // SIEMPRE AVANZAR AL TERMINAR
                        synchronized(this) { siguienteTurno(); }
                    }
                }).start();
            } else {
                // Carta desconocida, avanzar para no trabar
                siguienteTurno();
            }
        } catch (Exception e) {
            System.err.println("ERROR EN EFECTO: " + e.getMessage());
            iniciarTurno(); // Desbloqueo de emergencia
        } finally {
            cartaEspecialPendiente = null;
            // No seteamos procesandoAccion=false aquí, lo hará iniciarTurno()
        }
    }

    private void plantarseAutomatico() {
        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(TipoMensaje.ERROR, "¡Tiempo agotado!"));
        sacarDeRonda(actual);
        iniciarTurno();
    }

    private void siguienteTurno() {
        if (jugadoresEnRonda.isEmpty()) {
            new Thread(this::finDeRonda).start();
            return;
        }
        indiceTurno = (indiceTurno + 1) % jugadoresEnRonda.size();
        iniciarTurno();
    }

    private void finDeRonda() {
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "--- FIN DE LA RONDA ---"));
        try { Thread.sleep(4000); } catch (Exception e) {}
        synchronized(this) { iniciarPartida(); }
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
        for (ClientHandler h : jugadores) {
            // Siempre nueva lista para romper caché
            estadoGlobal.put(h.getNombreUsuario(), new ArrayList<>(cartasJugadores.get(h)));
        }
        broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_OPONENTES, estadoGlobal));

        for(ClientHandler j : jugadores) {
            j.enviarMensaje(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, new ArrayList<>(cartasJugadores.get(j))));
        }
    }
}