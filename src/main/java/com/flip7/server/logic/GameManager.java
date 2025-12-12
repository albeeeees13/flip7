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
    private int limiteJugadores;

    private List<ClientHandler> jugadores;
    private List<ClientHandler> espectadores;
    private List<ClientHandler> jugadoresEnRonda;

    private Map<String, Integer> puntajesGlobales;

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

    public GameManager(String idSala, int limiteJugadores) {
        this.idSala = idSala;
        this.limiteJugadores = limiteJugadores;
        this.jugadores = new ArrayList<>();
        this.espectadores = new ArrayList<>();
        this.cartasJugadores = new HashMap<>();
        this.puntajesGlobales = new HashMap<>();
        this.reglas = new MotorReglas();
        this.mazo = new Mazo();
    }

    public void agregarJugador(ClientHandler jugador) {
        synchronized (lock) {
            if (jugadores.size() < limiteJugadores && !juegoIniciado) {
                jugadores.add(jugador);
                cartasJugadores.put(jugador, new ArrayList<>());
                puntajesGlobales.putIfAbsent(jugador.getNombreUsuario(), 0);

                jugador.enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_CHAT, "Te uniste (" + jugadores.size() + "/" + limiteJugadores + ")"));

                if (jugadores.size() == limiteJugadores) {
                    iniciarPartida();
                }
            } else {
                espectadores.add(jugador);
                jugador.enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_CHAT, "Sala llena. Eres ESPECTADOR."));
                enviarEstadoA(jugador);
            }
        }
    }

    private void iniciarPartida() {
        System.out.println("[GAME] Iniciando ronda...");
        synchronized (lock) {
            juegoIniciado = true;
            mazo = new Mazo();
            jugadoresEnRonda = new ArrayList<>(jugadores);
            indiceTurno = 0;
            for (ClientHandler j : jugadores) cartasJugadores.get(j).clear();
        }

        broadcast(new Mensaje(TipoMensaje.INICIO_JUEGO, "¡Ronda iniciada! Gana quien llegue a 200pts."));
        enviarEstadoJuego();
        iniciarTurno();
    }

    private void iniciarTurno() {
        esperandoObjetivo = false;
        if (tareaTurno != null && !tareaTurno.isDone()) tareaTurno.cancel(false);

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
            broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));
            tareaTurno = scheduler.schedule(this::plantarseAutomatico, 30, TimeUnit.SECONDS);
        }
    }

    // --- AQUÍ ESTÁ EL ARREGLO DEL CHAT ---
    public void procesarJugada(ClientHandler solicitante, Mensaje msj) {

        // 1. ¡CHAT PRIMERO! (Así funciona siempre, aunque no sea tu turno)
        if (msj.getTipo() == TipoMensaje.MENSAJE_CHAT) {
            String prefijo = "";
            synchronized(lock) {
                if (espectadores.contains(solicitante)) prefijo = "[Espectador] ";
            }
            broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, prefijo + solicitante.getNombreUsuario() + ": " + msj.getContenido()));
            return; // Cortamos aquí para que no siga validando turnos
        }

        // 2. Si es espectador y no es chat, ignorar
        synchronized (lock) { if (espectadores.contains(solicitante)) return; }

        // 3. Anti-Spam y Validación de Turno
        long ahora = System.currentTimeMillis();
        if (ahora - ultimoTiempoAccion < 500) return;
        ultimoTiempoAccion = ahora;

        TipoMensaje tipo = msj.getTipo();
        ClientHandler actual;

        synchronized (lock) {
            if (!jugadoresEnRonda.contains(solicitante)) return;
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
                    sacarDeRonda(solicitante, true);
                    siguienteTurno();
                    break;
                case SELECCIONAR_OBJETIVO:
                    aplicarEfectoEspecial(solicitante, (String) msj.getContenido());
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
                    // --- ARREGLO SECOND CHANCE (BORRADO POR POSICIÓN) ---
                    // 1. Borrar la carta que causó el bust (SIEMPRE es la última de la lista)
                    if (!susCartas.isEmpty()) {
                        susCartas.remove(susCartas.size() - 1);
                    }

                    // 2. Buscar y borrar la primera Second Chance
                    for (int i = 0; i < susCartas.size(); i++) {
                        if (susCartas.get(i).getAccion() == AccionEspecial.SECOND_CHANCE) {
                            susCartas.remove(i);
                            break; // ¡Importante! Borrar solo una y salir
                        }
                    }
                }

                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡" + jugador.getNombreUsuario() + " usó SECOND CHANCE!"));
                enviarEstadoJuego();
                return true;
            } else {
                // BUST REAL
                jugador.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "BUST"));
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡BUST! " + jugador.getNombreUsuario() + " sacó otro " + cartaNueva.getValor()));

                try { Thread.sleep(2000); } catch (Exception e) {}

                synchronized (lock) {
                    cartasJugadores.get(jugador).clear();
                    sacarDeRonda(jugador, false);
                }
                enviarEstadoJuego();
                return false;
            }
        } else {
            // Flip 7: Solo Números
            boolean ganoFlip7 = false;
            synchronized(lock) {
                Set<Integer> numerosUnicos = new HashSet<>();
                for (Carta c : susCartas) {
                    if (c.getTipo() == Tipo.NUMERO) numerosUnicos.add(c.getValor());
                }
                if (numerosUnicos.size() >= 7) ganoFlip7 = true;
            }

            if (ganoFlip7) {
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡FLIP 7! " + jugador.getNombreUsuario() + " gana +15 puntos!"));
                synchronized (lock) {
                    int pts = reglas.calcularPuntosMesa(cartasJugadores.get(jugador)) + 15;
                    puntajesGlobales.put(jugador.getNombreUsuario(), puntajesGlobales.get(jugador.getNombreUsuario()) + pts);
                    sacarDeRondaInterno(jugador);
                }
                enviarEstadoJuego();
                return false;
            }

            if (cartaNueva.getTipo() == Tipo.ACCION &&
                    (cartaNueva.getAccion() == AccionEspecial.FREEZE || cartaNueva.getAccion() == AccionEspecial.FLIP_3)) {
                activarSeleccionObjetivo(jugador, cartaNueva);
            }
            return true;
        }
    }

    private void sacarDeRonda(ClientHandler jugador, boolean sumarPuntos) {
        if(tareaTurno != null) tareaTurno.cancel(false);

        synchronized (lock) {
            if (sumarPuntos) {
                int puntos = reglas.calcularPuntosMesa(cartasJugadores.get(jugador));
                if (puntos > 0) {
                    String nombre = jugador.getNombreUsuario();
                    int total = puntajesGlobales.get(nombre) + puntos;
                    puntajesGlobales.put(nombre, total);
                    broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, nombre + " se planta con " + puntos + " (Total: " + total + ")"));
                }
            }
            sacarDeRondaInterno(jugador);
        }
    }

    private void sacarDeRondaInterno(ClientHandler jugador) {
        int indexJugador = jugadoresEnRonda.indexOf(jugador);
        if (indexJugador == -1) return;

        boolean estabaAntesDeMi = indexJugador < indiceTurno;
        jugadoresEnRonda.remove(jugador);

        if (estabaAntesDeMi) indiceTurno--;
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
                for (ClientHandler h : jugadores) if (h.getNombreUsuario().equals(nombreDestino)) destino = h;
            }
            AccionEspecial accion = cartaEspecialPendiente.getAccion();
            broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, origen.getNombreUsuario() + " usó " + accion));

            if (accion == AccionEspecial.FREEZE) {
                sacarDeRonda(destino, true);
                synchronized (lock) {
                    if (jugadoresEnRonda.isEmpty()) scheduler.schedule(this::finDeRonda, 100, TimeUnit.MILLISECONDS);
                    else if (destino.equals(origen)) iniciarTurno();
                    else siguienteTurno();
                }
            } else if (accion == AccionEspecial.FLIP_3) {
                final ClientHandler target = destino;
                scheduler.submit(() -> {
                    try {
                        for (int i = 0; i < 3; i++) {
                            boolean vivo;
                            synchronized (lock) { if (!jugadoresEnRonda.contains(target)) break; }
                            vivo = ejecutarRoboSeguro(target);
                            if (!vivo) break;
                        }
                    } catch (Exception e) {}
                    finally { synchronized (lock) { siguienteTurno(); } }
                });
            } else siguienteTurno();
        } catch (Exception e) { iniciarTurno(); }
        finally { cartaEspecialPendiente = null; }
    }

    private void plantarseAutomatico() {
        ClientHandler actual;
        synchronized(lock) { actual = getJugadorActual(); }
        if (actual != null) {
            broadcast(new Mensaje(TipoMensaje.ERROR, "¡Tiempo agotado!"));
            sacarDeRonda(actual, true);
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

        String ganador = null;
        StringBuilder sb = new StringBuilder("PUNTAJES:\n");
        for(Map.Entry<String, Integer> entry : puntajesGlobales.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            if (entry.getValue() >= 200) ganador = entry.getKey();
        }
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, sb.toString()));

        if (ganador != null) {
            broadcast(new Mensaje(TipoMensaje.ERROR, "¡GANADOR DEL JUEGO: " + ganador + "!"));
            juegoIniciado = false;
        } else {
            try { Thread.sleep(4000); } catch (Exception e) {}
            iniciarPartida();
        }
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
        synchronized (lock) {
            targets = new ArrayList<>(jugadores);
            targets.addAll(espectadores);
        }
        for (ClientHandler j : targets) j.enviarMensaje(msg);
    }

    private void enviarEstadoJuego() {
        for (ClientHandler e : espectadores) enviarEstadoA(e);
        Map<String, List<Carta>> estadoGlobal = new HashMap<>();
        List<ClientHandler> targets;
        synchronized (lock) {
            for (ClientHandler h : jugadores) estadoGlobal.put(h.getNombreUsuario(), new ArrayList<>(cartasJugadores.get(h)));
            targets = new ArrayList<>(jugadores);
        }
        Mensaje msgOponentes = new Mensaje(TipoMensaje.ACTUALIZAR_OPONENTES, estadoGlobal);
        for(ClientHandler j : targets) {
            j.enviarMensaje(msgOponentes);
            List<Carta> miMano;
            synchronized (lock) { miMano = new ArrayList<>(cartasJugadores.get(j)); }
            j.enviarMensaje(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, miMano));
        }
    }

    private void enviarEstadoA(ClientHandler cliente) {
        Map<String, List<Carta>> estadoGlobal = new HashMap<>();
        synchronized (lock) {
            for (ClientHandler h : jugadores) estadoGlobal.put(h.getNombreUsuario(), new ArrayList<>(cartasJugadores.get(h)));
        }
        cliente.enviarMensaje(new Mensaje(TipoMensaje.ACTUALIZAR_OPONENTES, estadoGlobal));
    }
}