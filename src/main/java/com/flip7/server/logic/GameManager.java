package com.flip7.server.logic;

import com.flip7.common.model.Carta;
import com.flip7.common.network.Mensaje;
import com.flip7.common.enums.TipoAccion;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.server.network.ClientHandler;

import java.util.*;

public class GameManager {

    private String idSala;
    private List<ClientHandler> jugadores;
    private int indiceTurno = 0;
    private boolean juegoIniciado = false;

    private MotorReglas reglas;
    private List<Carta> cartasEnMesa;
    private Mazo mazo;
    private Timer timerTurno;

    public GameManager(String idSala) {
        this.idSala = idSala;
        this.jugadores = new ArrayList<>();
        this.cartasEnMesa = new ArrayList<>();
        this.reglas = new MotorReglas();
        this.mazo = new Mazo();
    }

    public void agregarJugador(ClientHandler jugador) {
        // Si hay espacio y el juego no ha empezado
        if (jugadores.size() < 2 && !juegoIniciado) {
            jugadores.add(jugador);
            jugador.enviarMensaje(new Mensaje(TipoMensaje.ROL_ASIGNADO, "JUGADOR"));

            // Si ya hay 2, arrancamos
            if (jugadores.size() == 2) {
                iniciarPartida();
            }
        } else {
            // Si llega tarde, es espectador
            jugador.enviarMensaje(new Mensaje(TipoMensaje.ROL_ASIGNADO, "ESPECTADOR"));
        }
    }

    private void iniciarPartida() {
        juegoIniciado = true;
        mazo = new Mazo(); // Reiniciamos mazo por si acaso
        broadcast(new Mensaje(TipoMensaje.INICIO_JUEGO, "¡La partida ha comenzado!"));

        // Empezamos con el jugador 0
        indiceTurno = 0;
        iniciarTurno();
    }

    private void iniciarTurno() {
        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));

        // Reiniciar Timer de 60 segundos
        if (timerTurno != null) timerTurno.cancel();
        timerTurno = new Timer();
        timerTurno.schedule(new TimerTask() {
            @Override
            public void run() {
                forzarFinDeTurno();
            }
        }, 60000);
    }


    public synchronized void procesarJugada(ClientHandler solicitante, TipoMensaje tipoMensaje) {

        if (!juegoIniciado) {
            solicitante.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "El juego aún no comienza."));
            return;
        }

        if (!solicitante.equals(getJugadorActual())) {
            solicitante.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "¡No es tu turno!"));
            return;
        }


        switch (tipoMensaje) {
            case ACCION_SACAR:
                procesarAccionSacar(solicitante);
                break;
            case ACCION_PLANTARSE:
                procesarAccionPlantarse(solicitante);
                break;
            default:
                System.out.println("Acción no válida en este contexto: " + tipoMensaje);
        }
    }

    // Lógica interna para SACAR
    private void procesarAccionSacar(ClientHandler solicitante) {
        // Detenemos el timer porque ya actuó
        timerTurno.cancel();

        Carta cartaNueva = mazo.robarCarta();

        // Si se acaba el mazo (caso extremo)
        if (cartaNueva == null) {
            broadcast(new Mensaje(TipoMensaje.ERROR, "¡Se acabó el mazo! Reiniciando partida..."));
            juegoIniciado = false;
            return;
        }

        boolean esBust = reglas.verificarBust(cartasEnMesa, cartaNueva);

        if (esBust) {
            broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, "¡BUST! Salió: " + cartaNueva.getValor()));
            cartasEnMesa.clear();
            siguienteTurno();
        } else {
            cartasEnMesa.add(cartaNueva);
            // Enviamos las cartas nuevas a todos
            broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, new ArrayList<>(cartasEnMesa)));
            // En Flip 7, si sacas carta y no pierdes, sigue siendo tu turno (vuelves a elegir)
            iniciarTurno();
        }
    }

    // Lógica interna para PLANTARSE
    private void procesarAccionPlantarse(ClientHandler solicitante) {
        timerTurno.cancel();

        int puntosLogrados = reglas.calcularPuntosMesa(cartasEnMesa);

        broadcast(new Mensaje(TipoMensaje.MENSAJE_CHAT,
                solicitante.getNombreUsuario() + " se plantó con " + puntosLogrados + " puntos."));

        // Verificar si ganó la partida completa con estos puntos
        if (reglas.esGanador(puntosLogrados)) {
            broadcast(new Mensaje(TipoMensaje.INICIO_JUEGO, "¡JUEGO TERMINADO! Ganador: " + solicitante.getNombreUsuario()));
            juegoIniciado = false;
            // Aquí podrías guardar estadísticas en la BD usando UsuarioDAO
        } else {
            cartasEnMesa.clear();
            broadcast(new Mensaje(TipoMensaje.ACTUALIZAR_TABLERO, new ArrayList<>(cartasEnMesa)));
            siguienteTurno();
        }
    }

    private void siguienteTurno() {
        indiceTurno = (indiceTurno + 1) % jugadores.size();
        iniciarTurno();
    }

    private void forzarFinDeTurno() {
        broadcast(new Mensaje(TipoMensaje.ERROR, "¡Tiempo agotado! Cambio de turno."));
        cartasEnMesa.clear();
        siguienteTurno();
    }

    private ClientHandler getJugadorActual() {
        return jugadores.get(indiceTurno);
    }

    private void broadcast(Mensaje msg) {
        for (ClientHandler j : jugadores) {
            j.enviarMensaje(msg);
        }
    }
}