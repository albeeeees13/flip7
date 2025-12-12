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

    // MARCADOR GLOBAL (Para llegar a 200)
    private Map<String, Integer> puntajesGlobales;

    private int indiceTurno = 0;
    private boolean juegoIniciado = false;
    private MotorReglas reglas;

    private Map<ClientHandler, List<Carta>> cartasJugadores;
    private Mazo mazo;

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> tareaTurno; // Variable corregida

    private boolean esperandoObjetivo = false;
    private Carta cartaEspecialPendiente = null;
    private long ultimoTiempoAccion = 0;
    private final Object lock = new Object();

    // CONSTRUCTOR CORREGIDO (Acepta límite)
    public GameManager(String idSala, int limiteJugadores) {
        this.idSala = idSala;
        this.limiteJugadores = limiteJugadores;
        this.jugadores = new ArrayList<>();
        this.espectadores = new ArrayList<>();
        this.cartasJugadores = new HashMap<>();
        this.puntajesGlobales = new HashMap<>(); // Inicializar marcador
        this.reglas = new MotorReglas();
        this.mazo = new Mazo();
    }

    public void agregarJugador(ClientHandler jugador) {
        synchronized (lock) {
            if (jugadores.size() < limiteJugadores && !juegoIniciado) {
                jugadores.add(jugador);
                cartasJugadores.put(jugador, new ArrayList<>());
                puntajesGlobales.put(jugador.getNombreUsuario(), 0); // Empieza en 0 puntos

                jugador.enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_CHAT, "Unido (" + jugadores.size() + "/" + limiteJugadores + ")"));

                if (jugadores.size() == limiteJugadores) {
                    iniciarPartida();
                }
            } else {
                espectadores.add(jugador);
                jugador.enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_CHAT, "Sala llena. Modo Espectador."));
                enviarEstadoA(jugador);
            }
        }
    }

    private void iniciarPartida() {
        System.out.println("[GAME] Iniciando nueva ronda...");
        synchronized (lock) {
            juegoIniciado = true;
            mazo = new Mazo();
            jugadoresEnRonda = new ArrayList<>(jugadores);
            indiceTurno = 0;
            for (ClientHandler j : jugadores) cartasJugadores.get(j).clear();
        }

        broadcast(new Mensaje(TipoMensaje.INICIO_JUEGO, "¡Ronda iniciada! Primero a 200 gana."));
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
            actual = jugadoresEnRonda.get(indiceTurno);
        }

        if (actual != null) {
            broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));
            tareaTurno = scheduler.schedule(this::plantarseAutomatico, 30, TimeUnit.SECONDS);
        }
    }

    public void procesarJugada(ClientHandler solicitante, Mensaje msj) {

        synchronized (lock) {
            if (espectadores.contains(solicitante)) {
                if (msj.getTipo() == TipoMensaje.MENSAJE_CHAT) {
                    broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "[Espectador] " + solicitante.getNombreUsuario() + ": " + msj.getContenido()));
                }
                return;
            }
        }


        if (msj.getTipo() == TipoMensaje.MENSAJE_CHAT) {
            broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, solicitante.getNombreUsuario() + ": " + msj.getContenido()));
            return; // Terminamos, no hace falta validar turno
        }


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
                if (!sigueVivo) iniciarTurno();
                else if (!esperandoObjetivo) siguienteTurno();
            } catch (Exception e) {
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

            // Regla: Solo 1 Second Chance
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
                    // --- ARREGLO SECOND CHANCE ---
                    // Borramos usando iterador para evitar errores de referencia
                    Iterator<Carta> it = susCartas.iterator();
                    boolean borradoSC = false;
                    boolean borradoRep = false;

                    while(it.hasNext()) {
                        Carta c = it.next();
                        if (!borradoSC && c.getAccion() == AccionEspecial.SECOND_CHANCE) {
                            it.remove(); borradoSC = true; continue;
                        }
                        if (!borradoRep && c == cartaNueva) {
                            it.remove(); borradoRep = true; continue;
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
                    sacarDeRonda(jugador, false); // false = NO suma puntos (Bust)
                }
                enviarEstadoJuego();
                return false;
            }
        } else {
            // Flip 7 Check (Solo Números)
            synchronized(lock) {
                long unicos = susCartas.stream().filter(c -> c.getTipo() == Tipo.NUMERO).map(Carta::getValor).distinct().count();
                if (unicos >= 7) {
                    broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡FLIP 7! " + jugador.getNombreUsuario() + " gana +15 puntos!"));
                    // Se planta automáticamente y gana el bono
                    // Sumamos 15 extra al cálculo normal en sacarDeRonda
                    // OJO: Hay que manejar el bono. Simplificado:
                    sacarDeRonda(jugador, true);
                    // Sumar bono manual en puntajesGlobales
                    String nombre = jugador.getNombreUsuario();
                    puntajesGlobales.put(nombre, puntajesGlobales.get(nombre) + 15);
                    return false;
                }
            }
            if (cartaNueva.getTipo() == Tipo.ACCION && (cartaNueva.getAccion() == AccionEspecial.FREEZE || cartaNueva.getAccion() == AccionEspecial.FLIP_3)) {
                activarSeleccionObjetivo(jugador, cartaNueva);
            }
            return true;
        }
    }

    // Método centralizado para sacar jugador y SUMAR PUNTOS
    private void sacarDeRonda(ClientHandler jugador, boolean sumarPuntos) {
        if (tareaTurno != null) tareaTurno.cancel(false);

        synchronized (lock) {
            if (!jugadoresEnRonda.contains(jugador)) return;

            if (sumarPuntos) {
                int puntosRonda = reglas.calcularPuntosMesa(cartasJugadores.get(jugador));
                String nombre = jugador.getNombreUsuario();
                int puntosActuales = puntajesGlobales.getOrDefault(nombre, 0);
                int total = puntosActuales + puntosRonda;

                puntajesGlobales.put(nombre, total);
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, nombre + " se planta con " + puntosRonda + " (Total: " + total + ")"));
            }

            int idx = jugadoresEnRonda.indexOf(jugador);
            boolean estabaAntes = idx < indiceTurno;
            jugadoresEnRonda.remove(jugador);

            if (estabaAntes) indiceTurno--;
            if (indiceTurno < 0) indiceTurno = 0;
        }
    }

    private void activarSeleccionObjetivo(ClientHandler jugador, Carta carta) {
        if (tareaTurno != null) tareaTurno.cancel(false);
        esperandoObjetivo = true;
        cartaEspecialPendiente = carta;
        List<String> rivales;
        synchronized (lock) {
            rivales = jugadores.stream().map(ClientHandler::getNombreUsuario)
                    .filter(n -> !n.equals(jugador.getNombreUsuario())).collect(Collectors.toList());
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
                sacarDeRonda(destino, true); // Freeze planta al jugador con puntos
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
            sacarDeRonda(actual, true); // Se planta por tiempo
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

        // VERIFICAR SI ALGUIEN GANÓ (200 Puntos)
        String ganador = null;
        for (Map.Entry<String, Integer> entry : puntajesGlobales.entrySet()) {
            broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, entry.getKey() + ": " + entry.getValue() + " pts"));
            if (entry.getValue() >= 200) ganador = entry.getKey();
        }

        if (ganador != null) {
            broadcast(new Mensaje(TipoMensaje.ERROR, "¡GANADOR DEL JUEGO: " + ganador + "!"));
            // Reiniciar juego completo o cerrar sala
            juegoIniciado = false;
        } else {
            try { Thread.sleep(4000); } catch (Exception e) {}
            iniciarPartida(); // Nueva ronda
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
        Mensaje msgOps = new Mensaje(TipoMensaje.ACTUALIZAR_OPONENTES, estadoGlobal);
        for(ClientHandler j : targets) {
            j.enviarMensaje(msgOps);
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