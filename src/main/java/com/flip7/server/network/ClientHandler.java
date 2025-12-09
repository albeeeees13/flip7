package com.flip7.server.network;

import com.flip7.server.logic.GameManager;
import com.flip7.server.logic.LobbyManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String nombreUsuario;
    private boolean conectado = true;

    private LobbyManager lobby
    private GameManager salaActual;


    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.lobby = lobby;
        try {

            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
            conectado = false;
        }
    }

    @Override
    public void run() {
        try {
            if (conectado) lobby.agregarJugador(this);
            while (conectado) {
                Mensaje mensajeRecibido = (Mensaje) in.readObject();
                System.out.println("Mensaje recibido de " + getNombreUsuario() + ": " + mensajeRecibido.getTipo());
                switch (mensajeRecibido.getTipo()) {
                    case LOGIN:
                        this.nombreUsuario = (String) mensajeRecibido.getContenido();
                        break;
                    case UNIRSE_SALA:
                        break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            cerrarConexion();
        }
    }


    public void enviarMensaje(Mensaje msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error enviando mensaje a " + nombreUsuario);
            cerrarConexion();
        }
    }

    public String getNombreUsuario() {
        return (nombreUsuario != null) ? nombreUsuario : "Anónimo";
    }

    private void cerrarConexion() {
        conectado = false;
        if (lobby != null) lobby.removerJugador(this); // <--- AGREGAR ESTO
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    }
