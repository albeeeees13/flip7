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

    // Mapa: Jugador -> Sus Cartas en la ronda actual
    private Map<ClientHandler, List<Carta>> cartasJugadores;

    private Mazo mazo;
    private Timer timerTurno;

    // Estado para Cartas Especiales (Pausa para elegir objetivo)
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
        if (esperandoObjetivo) return; // No hacemos nada si estamos esperando respuesta de un popup

        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));

        // Timer de 30 segundos para pensar
        if (timerTurno != null) timerTurno.cancel();
        timerTurno = new Timer();
        timerTurno.schedule(new TimerTask() {
            @Override
            public void run() {
                plantarseAutomatico();
            }
        }, 30000);
    }

    // MÉTODO PRINCIPAL DE ENTRADA (Recibe el Mensaje completo)
    public synchronized void procesarJugada(ClientHandler solicitante, Mensaje msj) {
        TipoMensaje tipo = msj.getTipo();

        // Validaciones de turno (excepto si estamos respondiendo a un objetivo)
        if (!solicitante.equals(getJugadorActual()) && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) {
            solicitante.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "No es tu turno"));
            return;
        }

        // Si el juego está pausado esperando un objetivo, ignoramos otras acciones
        if (esperandoObjetivo && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) return;

        switch (tipo) {
            case ACCION_SACAR:
                sacarCarta(solicitante);
                break;
            case ACCION_PLANTARSE:
                plantarse(solicitante);
                break;
            case SELECCIONAR_OBJETIVO:
                // Recuperamos el nombre del objetivo seleccionado
                String objetivo = (String) msj.getContenido();
                aplicarEfectoEspecial(solicitante, objetivo);
                break;
        }
    }

    private void sacarCarta(ClientHandler jugador) {
        timerTurno.cancel();
        Carta carta = mazo.robarCarta();

        if (carta == null) {
            broadcast(new Mensaje(TipoMensaje.ERROR, "Fin del mazo. Ronda terminada."));
            // Aquí iría lógica de fin de ronda por mazo vacío
            return;
        }

        List<Carta> susCartas = cartasJugadores.get(jugador);

        // --- AQUÍ ESTÁ LA LÓGICA DE BUST ---

        // 1. Verificamos si es Bust ANTES de añadirla definitivamente
        boolean esBust = reglas.verificarBust(susCartas, carta);

        // 2. Agregamos la carta SIEMPRE para que el cliente la vea (aunque sea la repetida)
        susCartas.add(carta);
        enviarEstadoJuego(); // El cliente dibuja la carta normal

        if (esBust) {
            // Verificar si tiene Second Chance
            Optional<Carta> secondChance = susCartas.stream()
                    .filter(c -> c.getAccion() == AccionEspecial.SECOND_CHANCE).findFirst();

            if (secondChance.isPresent()) {

                try { Thread.sleep(1500); } catch (InterruptedException e) {} // Pausa para drama

                susCartas.remove(secondChance.get());
                susCartas.remove(carta);

                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡" + jugador.getNombreUsuario() + " usó SECOND CHANCE!"));
                enviarEstadoJuego();
                iniciarTurno();
            } else {

                jugador.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "BUST"));
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡BUST! " + jugador.getNombreUsuario() + " sacó otro " + carta.getValor()));


                susCartas.clear();



                siguienteTurno();
            }
            return;
        }




        if (carta.getTipo() == Tipo.ACCION &&
                (carta.getAccion() == AccionEspecial.FREEZE || carta.getAccion() == AccionEspecial.FLIP_3)) {

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
            return;
        }

        iniciarTurno();
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
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT,
                origen.getNombreUsuario() + " aplicó " + accion + " a " + destino.getNombreUsuario()));


        cartaEspecialPendiente = null;
        iniciarTurno();
    }

    private void plantarse(ClientHandler jugador) {
        timerTurno.cancel();
        int puntos = reglas.calcularPuntosMesa(cartasJugadores.get(jugador));
        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, jugador.getNombreUsuario() + " se plantó con " + puntos));
        siguienteTurno();
    }

    private void plantarseAutomatico() {
        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(TipoMensaje.ERROR, "¡Tiempo agotado! Te plantas automáticamente."));
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

    private void enviarEstadoJuego() {
        // Enviar a cada jugador sus propias cartas para que se dibujen
        for(ClientHandler j : jugadores) {
            j.enviarMensaje(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, cartasJugadores.get(j)));
        }
    }
}