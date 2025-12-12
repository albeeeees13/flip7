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

    // Mapa: Jugador -> Sus Cartas
    private Map<ClientHandler, List<Carta>> cartasJugadores;

    private Mazo mazo;
    private Timer timerTurno;

    // Estado para Cartas Especiales
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
        if (esperandoObjetivo) return; // No cambiar turno si estamos esperando respuesta

        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));

        // Timer de 30s para pensar
        if (timerTurno != null) timerTurno.cancel();
        timerTurno = new Timer();
        timerTurno.schedule(new TimerTask() {
            @Override
            public void run() {
                plantarseAutomatico();
            }
        }, 30000);
    }

    public synchronized void procesarJugada(ClientHandler solicitante, Mensaje msj) {

        TipoMensaje tipo = msj.getTipo(); // Sacamos el tipo aquí

        if (!solicitante.equals(getJugadorActual()) && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) {
            solicitante.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "No es tu turno"));
            return;
        }


        if (esperandoObjetivo && tipo != TipoMensaje.SELECCIONAR_OBJETIVO) return;

        switch (tipo) {
            case ACCION_SACAR:
                sacarCarta(solicitante);
                break;
            case ACCION_PLANTARSE:
                plantarse(solicitante);
                break;
            case SELECCIONAR_OBJETIVO:

                String objetivo = (String) msj.getContenido();
                aplicarEfectoEspecial(solicitante, objetivo);
                break;
        }
    }


    public synchronized void recibirObjetivo(ClientHandler solicitante, String nombreObjetivo) {
        if(esperandoObjetivo && solicitante.equals(getJugadorActual())) {
            aplicarEfectoEspecial(solicitante, nombreObjetivo);
        }
    }

    private void sacarCarta(ClientHandler jugador) {
        timerTurno.cancel();
        Carta carta = mazo.robarCarta();

        if (carta == null) {
            broadcast(new Mensaje(TipoMensaje.ERROR, "Fin del mazo. Ronda terminada."));
            // Lógica de fin de ronda...
            return;
        }

        List<Carta> susCartas = cartasJugadores.get(jugador);

        // 1. REGLA BUST (Muerte súbita por repetida)
        boolean esBust = reglas.verificarBust(susCartas, carta);

        if (esBust) {
            // Verificar si tiene Second Chance para salvarse
            Optional<Carta> secondChance = susCartas.stream()
                    .filter(c -> c.getAccion() == AccionEspecial.SECOND_CHANCE).findFirst();

            if (secondChance.isPresent()) {
                // SE SALVA: Se descarta la carta repetida Y el Second Chance
                susCartas.remove(secondChance.get());
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡" + jugador.getNombreUsuario() + " usó SECOND CHANCE!"));
                // Sigue jugando su turno
                enviarEstadoJuego();
                iniciarTurno();
            } else {
                // PIERDE TODO
                broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT, "¡BUST! " + jugador.getNombreUsuario() + " sacó otro " + carta.getValor()));
                susCartas.clear(); // Pierde puntos
                enviarEstadoJuego();
                siguienteTurno();
            }
            return;
        }

        // Si no es Bust, añadimos la carta
        susCartas.add(carta);
        enviarEstadoJuego();

        // 2. VERIFICAR SI ES CARTA DE ACCIÓN QUE REQUIERE OBJETIVO
        if (carta.getTipo() == Tipo.ACCION &&
                (carta.getAccion() == AccionEspecial.FREEZE || carta.getAccion() == AccionEspecial.FLIP_3)) {

            esperandoObjetivo = true;
            cartaEspecialPendiente = carta;

            // Enviamos lista de rivales al cliente para el Popup
            List<String> rivales = jugadores.stream()
                    .map(ClientHandler::getNombreUsuario)
                    .filter(n -> !n.equals(jugador.getNombreUsuario()))
                    .collect(Collectors.toList());

            // Construimos array: [NombreAccion, Rival1, Rival2...]
            List<String> payload = new ArrayList<>();
            payload.add(carta.getAccion().toString());
            payload.addAll(rivales);

            jugador.enviarMensaje(new Mensaje(TipoMensaje.SOLICITAR_OBJETIVO, payload.toArray(new String[0])));
            return; // PAUSA AQUÍ HASTA QUE RESPONDA
        }

        // Si es normal, sigue su turno
        iniciarTurno();
    }

    private void aplicarEfectoEspecial(ClientHandler origen, String nombreDestino) {
        esperandoObjetivo = false;
        ClientHandler destino = origen; // Por defecto a sí mismo ("SELF")

        // Buscar el jugador destino
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

        if (accion == AccionEspecial.FREEZE) {
            // Lógica Freeze: El destino pierde su turno o queda inactivo
            // (Para simplificar ahora, solo avisamos)
        }
        else if (accion == AccionEspecial.FLIP_3) {
            // Lógica Flip 3: Forzar sacar 3 cartas
        }

        cartaEspecialPendiente = null;
        iniciarTurno(); // Continuamos
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

    // Envía el estado completo (Tu mesa + Oponentes)
    private void enviarEstadoJuego() {
        // Enviar a cada jugador sus cartas y un resumen de los otros
        // (Simplificado: Mandamos actualizar tablero a todos)
        for(ClientHandler j : jugadores) {
            j.enviarMensaje(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, cartasJugadores.get(j)));
            // Aquí faltaría enviar el mapa de oponentes para la vista dividida
        }
    }
}