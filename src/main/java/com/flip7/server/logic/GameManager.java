package com.flip7.server.logic;

import com.flip7.common.Carta;
import com.flip7.common.enums.TipoAccion;

import com.flip7.server.network.ClientHandler;

import java.util.*;

public class GameManager {

    private String idSala;
    private List<ClientHandler> jugadores;
    private int indiceTurno = 0;
    private boolean juegoIniciado = false;


    private MotorReglas reglas;
    private List<Carta> cartasEnMesa;


    private Timer timerTurno;
    public GameManager(String idSala) {
        this.idSala = idSala;
        this.jugadores = new ArrayList<>();
        this.cartasEnMesa = new ArrayList<>();
        this.reglas = new MotorReglas(); // SRP: Delegamos la matemática aquí
    }


    public void agregarJugador(ClientHandler jugador) {
        if (jugadores.size() < 2 && !juegoIniciado) {
            jugadores.add(jugador);

            jugador.enviarMensaje(new Mensaje(Mensaje.Tipo.ROL_ASIGNADO, "JUGADOR"));


            if (jugadores.size() == 2) {
                iniciarPartida();
            }
        } else {

            jugador.enviarMensaje(new Mensaje(Mensaje.Tipo.ROL_ASIGNADO, "ESPECTADOR"));
        }
    }



    private void iniciarPartida() {
        juegoIniciado = true;
        broadcast(new Mensaje(Mensaje.Tipo.INICIO_JUEGO, "¡La partida ha comenzado!"));
        iniciarTurno();
    }

    private void iniciarTurno() {
        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(Mensaje.Tipo.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));


        if (timerTurno != null) timerTurno.cancel();
        timerTurno = new Timer();
        timerTurno.schedule(new TimerTask() {
            @Override
            public void run() {
                forzarFinDeTurno();
            }
        }, 60000);
    }
    public synchronized void procesarAccionSacar(ClientHandler solicitante) {

        if (!solicitante.equals(getJugadorActual())) return;


        timerTurno.cancel();


        Carta cartaNueva = new Carta(5, TipoAccion.NINGUNA, "5 de Corazones");


        boolean esBust = reglas.verificarBust(cartasEnMesa, cartaNueva);

        if (esBust) {
            broadcast(new Mensaje(Mensaje.Tipo.ACTUALIZAR_TABLERO, "¡BUST! Salió repetida: " + cartaNueva.getTexto()));
            cartasEnMesa.clear();
            siguienteTurno();
        } else {
            cartasEnMesa.add(cartaNueva);
            broadcast(new Mensaje(Mensaje.Tipo.ACTUALIZAR_TABLERO, cartasEnMesa));

            iniciarTurno();
        }
    }

    private void siguienteTurno() {
        indiceTurno = (indiceTurno + 1) % jugadores.size();
        iniciarTurno();
    }

    private void forzarFinDeTurno() {
        broadcast(new Mensaje(Mensaje.Tipo.ERROR, "¡Tiempo agotado! Cambio de turno."));
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