package com.flip7.server.logic;

import com.flip7.common.Carta;
import com.flip7.common.Mensaje;
import com.flip7.common.TipoAccion;

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

    // --- GESTIÓN DE JUGADORES ---

    public void agregarJugador(ClientHandler jugador) {
        if (jugadores.size() < 2 && !juegoIniciado) {
            jugadores.add(jugador);
            // Avisar al jugador que entró
            jugador.enviarMensaje(new Mensaje(Mensaje.Tipo.ROL_ASIGNADO, "JUGADOR"));

            // Si ya estamos listos (2 jugadores), iniciamos
            if (jugadores.size() == 2) {
                iniciarPartida();
            }
        } else {
            // Manejo de Espectador (Extra SOLID: No mezclamos lógica de juego con lógica de espectador)
            jugador.enviarMensaje(new Mensaje(Mensaje.Tipo.ROL_ASIGNADO, "ESPECTADOR"));
        }
    }

    // --- FLUJO DEL JUEGO ---

    private void iniciarPartida() {
        juegoIniciado = true;
        broadcast(new Mensaje(Mensaje.Tipo.INICIO_JUEGO, "¡La partida ha comenzado!"));
        iniciarTurno();
    }

    private void iniciarTurno() {
        ClientHandler actual = getJugadorActual();
        broadcast(new Mensaje(Mensaje.Tipo.ACTUALIZAR_TABLERO, "Turno de: " + actual.getNombreUsuario()));

        // Reiniciar Timer de 60s
        if (timerTurno != null) timerTurno.cancel();
        timerTurno = new Timer();
        timerTurno.schedule(new TimerTask() {
            @Override
            public void run() {
                forzarFinDeTurno(); // Castigo por tiempo
            }
        }, 60000);
    }