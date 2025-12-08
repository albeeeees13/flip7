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