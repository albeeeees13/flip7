package com.flip7.server.network;

import com.flip7.server.logic.GameManager;
import com.flip7.server.logic.LobbyManager;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.network.Mensaje;

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

    private LobbyManager lobby;
    private GameManager salaActual;


    public ClientHandler(Socket socket,LobbyManager lobby) {
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
            // Agregamos al jugador al lobby apenas conecta
            if (conectado && lobby != null) {
                lobby.agregarJugador(this);
            }
            while (conectado) {
                Mensaje mensajeRecibido = (Mensaje) in.readObject();
                System.out.println("Mensaje recibido de " + getNombreUsuario() + ": " + mensajeRecibido.getTipo());
                procesarMensaje(mensajeRecibido);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Cliente desconectado: " + getNombreUsuario());
        } finally {
            cerrarConexion();
        }
    }
    private void procesarMensaje(Mensaje msj) {
        switch (msj.getTipo()) {
            case LOGIN:
                this.nombreUsuario = (String) msj.getContenido();
                System.out.println("Usuario logueado: " + nombreUsuario);
                enviarMensaje(new Mensaje(TipoMensaje.LOGIN_EXITO, "Bienvenido " + nombreUsuario));
                break;

            case CREAR_SALA:
                break;

            case UNIRSE_SALA:
                break;

            case MENSAJE_CHAT:
                break;

            case ACCION_SACAR:
                if (salaActual != null) {
                }
                break;

            case ACCION_PLANTARSE:
                if (salaActual != null) {
                }
                break;

            default:
                System.out.println("Mensaje no manejado: " + msj.getTipo());
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
        if (lobby != null) lobby.removerJugador(this);
        try {
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    }
