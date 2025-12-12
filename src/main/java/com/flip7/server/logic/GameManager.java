package com.flip7.server.logic;

import com.flip7.common.model.Carta;
import com.flip7.common.model.Carta.AccionEspecial;
import com.flip7.common.model.Carta.Tipo;
import com.flip7.common.network.Mensaje;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.server.network.ClientHandler;

import java.util.*;
import java.util.concurrent.*;
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

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> tareaTurno;

    private boolean esperandoObjetivo = false;
    private Carta cartaEspecialPendiente = null;
    private long ultimoTiempoAccion = 0;
    private final Object lock = new Object();

    public GameManager(String idSala) {
        this.idSala = idSala;
        this.jugadores = new ArrayList<>();
        this.cartasJugadores = new HashMap<>();
        this.reglas = new MotorReglas();
        this.mazo = new Mazo();
    }

    public void agregarJugador(ClientHandler jugador) {
        synchronized (lock) {
            jugadores.add(jugador);
            cartasJugadores.put(jugador, new ArrayList<>());
        }
        if (jugadores.size() >= 2 && !juegoIniciado) {
            iniciarPartida();
        }
    }

    private void iniciarPartida() {
        System.out.println("[GAME] Iniciando partida...");
        synchronized (lock) {
            juegoIniciado = true;
            mazo = new Mazo();
            jugadoresEnRonda = new ArrayList<>(jugadores);
            indiceTurno = 0;
            for (ClientHandler j : jugadores) cartasJugadores.get(j).clear();
        }

        broadcast(new Mensaje(TipoMensaje.INICIO_JUEGO, "¡Empieza la ronda!"));
        enviarEstadoJuego();
        iniciarTurno();
    }

    private void iniciarTurno() {
        esperandoObjetivo = false;

        if (tareaTurno != null && !tareaTurno.isDone()) {
            tareaTurno.cancel(false);
        }

        ClientHandler actual = null;
        synchronized (lock) {
            if (jugadoresEnRonda.isEmpty()) {
                scheduler.schedule(this::finDeRonda, 1, TimeUnit.SECONDS);
                return;
            }
            if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;
            if (indiceTurno < 0) indiceTurno = 0;

            actual = jugadoresEnRonda.get(indiceTurno);
        }

        if (actual != null) {
            System.out.println("[GAME] Turno de: " + actual.getNombreUsuario());
            broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));
            tareaTurno = scheduler.schedule(this::plantarseAutomatico, 30, TimeUnit.SECONDS);
        }
    }

    public void procesarJugada(ClientHandler solicitante, Mensaje msj) {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimoTiempoAccion < 500) return;
        ultimoTiempoAccion = ahora;

        TipoMensaje tipo = msj.getTipo();
        ClientHandler actual;

        synchronized (lock) {
            if (!jugadoresEnRonda.contains(solicitante)) return;
            if (jugadoresEnRonda.isEmpty()) return;
            actual = jugadoresEnRonda.get(indiceTurno);
        }

        if (!solicitante.equals(actual) && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) {
            solicitante.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "No es tu turno"));
            return;
        }

        if (esperandoObjetivo && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) return;

        try {
            switch (tipo) {
                case ACCION_SACAR: sacarCarta(solicitante); break;
                case ACCION_PLANTARSE:
                    sacarDeRonda(solicitante);
                    siguienteTurno();
                    break;
                case SELECCIONAR_OBJETIVO:
                    String objetivo = (String) msj.getContenido();
                    aplicarEfectoEspecial(solicitante, objetivo);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
            iniciarTurno();
        }
    }

    private void sacarCarta(ClientHandler jugador) {
        if (tareaTurno != null) tareaTurno.cancel(false);

        scheduler.submit(() -> {
            try {
                boolean sigueVivo = ejecutarRoboSeguro(jugador);

                if (!sigueVivo) {
                    iniciarTurno();
                } else if (!esperandoObjetivo) {
                    siguienteTurno();
                }
            } catch (Exception e) {
                e.printStackTrace();
                iniciarTurno();
            }
        });
    }

    private boolean ejecutarRoboSeguro(ClientHandler jugador) {
        Carta cartaNueva;
        List<Carta> susCartas;
        boolean esBust;

        synchronized (lock) {
            cartaNueva = mazo.robarCarta();
            if (cartaNueva == null) return false;
            susCartas = cartasJugadores.get(jugador);

            // Regla: Descartar 2nd Chance extra si ya tiene una
            if (cartaNueva.getAccion() == AccionEspecial.SECOND_CHANCE) {
                boolean yaTiene = susCartas.stream().anyMatch(c -> c.getAccion() == AccionEspecial.SECOND_CHANCE);
                if (yaTiene) {
                    broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, jugador.getNombreUsuario() + " descarta 2nd Chance extra."));
                    enviarEstadoJuego();
                    return true;
                }
            }

            esBust = reglas.verificarBust(susCartas, cartaNueva);
            susCartas.add(cartaNueva);
        }

        enviarEstadoJuego();
        try { Thread.sleep(800); } catch (Exception e) {}

        if (esBust) {
            boolean tieneSC = false;
            synchronized (lock) {
                tieneSC = susCartas.stream().anyMatch(c -> c.getAccion() == AccionEspecial.SECOND_CHANCE);
            }

            if (tieneSC) {
                try { Thread.sleep(1000); } catch (Exception e) {}

                synchronized (lock) {
                    System.out.println("[DEBUG] Aplicando Second Chance a " + jugador.getNombreUsuario());

                    // --- ESTRATEGIA: BORRAR POR FUERZA BRUTA ---

                    // 1. Borrar la ÚLTIMA carta (la que acabamos de meter y causó el bust)
                    if (!susCartas.isEmpty()) {
                        susCartas.remove(susCartas.size() - 1);
                        System.out.println("[DEBUG] Carta mala eliminada.");
                    }

                    // 2. Buscar y borrar la PRIMERA Second Chance que aparezca
                    for (int i = 0; i < susCartas.size(); i++) {
                        if (susCartas.get(i).getAccion() == AccionEspecial.SECOND_CHANCE) {
                            susCartas.remove(i);
                            System.out.println("[DEBUG] Second Chance consumida.");
                            break; // Importante salir para solo borrar una
                        }
                    }
                }

                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡" + jugador.getNombreUsuario() + " usó SECOND CHANCE!"));
                enviarEstadoJuego();
                return true;
            } else {
                jugador.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "BUST"));
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡BUST! " + jugador.getNombreUsuario() + " sacó otro " + cartaNueva.getValor()));
                try { Thread.sleep(2000); } catch (Exception e) {}

                synchronized (lock) {
                    cartasJugadores.get(jugador).clear();
                    sacarDeRondaInterno(jugador);
                }
                enviarEstadoJuego();
                return false;
            }
        } else {
            // --- FLIP 7: Solo cuenta NÚMEROS ---
            boolean ganoFlip7 = false;
            synchronized(lock) {
                Set<Integer> numerosUnicos = new HashSet<>();
                for (Carta c : susCartas) {
                    // ¡FILTRO ESTRICTO! Solo Tipo.NUMERO cuenta
                    if (c.getTipo() == Tipo.NUMERO) {
                        numerosUnicos.add(c.getValor());
                    }
                }
                if (numerosUnicos.size() >= 7) ganoFlip7 = true;
            }

            if (ganoFlip7) {
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡FLIP 7! " + jugador.getNombreUsuario() + " gana con +15 puntos!"));
                synchronized (lock) {
                    // Aquí asumimos que ganas la ronda
                    sacarDeRondaInterno(jugador);
                }
                return false; // Termina turno
            }

            if (cartaNueva.getTipo() == Tipo.ACCION &&
                    (cartaNueva.getAccion() == AccionEspecial.FREEZE || cartaNueva.getAccion() == AccionEspecial.FLIP_3)) {
                activarSeleccionObjetivo(jugador, cartaNueva);
            }
            return true;
        }
    }

    private void sacarDeRonda(ClientHandler jugador) {
        if(tareaTurno != null) tareaTurno.cancel(false);
        synchronized (lock) {
            int puntos = reglas.calcularPuntosMesa(cartasJugadores.get(jugador));
            if (puntos > 0 && !cartasJugadores.get(jugador).isEmpty()) {
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, jugador.getNombreUsuario() + " se planta con " + puntos));
            }
            sacarDeRondaInterno(jugador);
        }
    }

    private void sacarDeRondaInterno(ClientHandler jugador) {
        int indexJugador = jugadoresEnRonda.indexOf(jugador);
        if (indexJugador == -1) return;

        boolean estabaAntesDeMi = indexJugador < indiceTurno;
        jugadoresEnRonda.remove(jugador);

        if (estabaAntesDeMi) {
            indiceTurno--;
        }
        if (indiceTurno < 0) indiceTurno = 0;
    }

    private void activarSeleccionObjetivo(ClientHandler jugador, Carta carta) {
        if (tareaTurno != null) tareaTurno.cancel(false);
        esperandoObjetivo = true;
        cartaEspecialPendiente = carta;

        List<String> rivales;
        synchronized (lock) {
            rivales = jugadores.stream()
                    .map(ClientHandler::getNombreUsuario)
                    .filter(n -> !n.equals(jugador.getNombreUsuario()))
                    .collect(Collectors.toList());
        }

        List<String> payload = new ArrayList<>();
        payload.add(carta.getAccion().toString());
        payload.addAll(rivales);

        jugador.enviarMensaje(new Mensaje(TipoMensaje.SOLICITAR_OBJETIVO, payload.toArray(new String[0])));
    }

    private void aplicarEfectoEspecial(ClientHandler origen, String nombreDestino) {
        try {
            esperandoObjetivo = false;
            ClientHandler destino = origen;

            synchronized (lock) {
                if (nombreDestino != null && !nombreDestino.equals("SELF")) {
                    for (ClientHandler h : jugadores) {
                        if (h.getNombreUsuario().equals(nombreDestino)) {
                            destino = h; break;
                        }
                    }
                }
            }

            AccionEspecial accion = cartaEspecialPendiente.getAccion();
            broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, origen.getNombreUsuario() + " aplicó " + accion + " a " + destino.getNombreUsuario()));

            if (accion == AccionEspecial.FREEZE) {
                sacarDeRonda(destino);
                synchronized (lock) {
                    if (jugadoresEnRonda.isEmpty()) {
                        scheduler.schedule(this::finDeRonda, 100, TimeUnit.MILLISECONDS);
                    } else if (destino.equals(origen)) {
                        iniciarTurno();
                    } else {
                        siguienteTurno();
                    }
                }
            }
            else if (accion == AccionEspecial.FLIP_3) {
                final ClientHandler target = destino;
                scheduler.submit(() -> {
                    try {
                        for (int i = 0; i < 3; i++) {
                            boolean vivo;
                            synchronized (lock) {
                                if (!jugadoresEnRonda.contains(target)) break;
                            }
                            vivo = ejecutarRoboSeguro(target);
                            if (!vivo) break;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        synchronized (lock) { siguienteTurno(); }
                    }
                });
            } else {
                siguienteTurno();
            }
        } catch (Exception e) {
            e.printStackTrace();
            iniciarTurno();
        } finally {
            cartaEspecialPendiente = null;
        }
    }

    private void plantarseAutomatico() {
        ClientHandler actual;
        synchronized(lock) { actual = getJugadorActual(); }

        if (actual != null) {
            broadcast(new Mensaje(TipoMensaje.ERROR, "¡Tiempo agotado!"));
            sacarDeRonda(actual);
            iniciarTurno();
        }
    }

    private void siguienteTurno() {
        synchronized (lock) {
            if (jugadoresEnRonda.isEmpty()) {
                scheduler.schedule(this::finDeRonda, 100, TimeUnit.MILLISECONDS);
                return;
            }
            indiceTurno++;
            if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;
        }
        iniciarTurno();
    }

    private void finDeRonda() {
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "--- FIN DE LA RONDA ---"));
        try { Thread.sleep(4000); } catch (Exception e) {}
        iniciarPartida();
    }

    private ClientHandler getJugadorActual() {
        synchronized (lock) {
            if (jugadoresEnRonda.isEmpty()) return null;
            if (indiceTurno >= jugadoresEnRonda.size()) indiceTurno = 0;
            return jugadoresEnRonda.get(indiceTurno);
        }
    }

    private void broadcast(Mensaje msg) {
        List<ClientHandler> targets;
        synchronized (lock) { targets = new ArrayList<>(jugadores); }
        for (ClientHandler j : targets) j.enviarMensaje(msg);
    }

    private void enviarEstadoJuego() {
        Map<String, List<Carta>> estadoGlobal = new HashMap<>();
        List<ClientHandler> targets;

        synchronized (lock) {
            for (ClientHandler h : jugadores) {
                estadoGlobal.put(h.getNombreUsuario(), new ArrayList<>(cartasJugadores.get(h)));
            }
            targets = new ArrayList<>(jugadores);
        }

        Mensaje msgOponentes = new Mensaje(TipoMensaje.ACTUALIZAR_OPONENTES, estadoGlobal);
        for(ClientHandler j : targets) {
            j.enviarMensaje(msgOponentes);

            List<Carta> miMano;
            synchronized (lock) {
                miMano = new ArrayList<>(cartasJugadores.get(j));
            }
            j.enviarMensaje(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, miMano));
        }
    }
}