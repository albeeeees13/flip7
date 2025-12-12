package com.flip7.server.network;

import com.flip7.common.network.Mensaje;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.server.logic.GameManager;
import com.flip7.server.logic.LobbyManager;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean conectado = true;

    private LobbyManager lobby;
    private GameManager salaActual;
    private String nombreUsuario;

    public ClientHandler(Socket socket, LobbyManager lobby) {
        this.socket = socket;
        this.lobby = lobby;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (conectado) {
                try {
                    Mensaje msj = (Mensaje) in.readObject();


                    if (nombreUsuario == null) {
                        if (msj.getTipo() == TipoMensaje.LOGIN) {
                            String textoCompleto = (String) msj.getContenido();


                            String[] partes = textoCompleto.split(",");
                            String usuario = partes[0].trim();


                            // 2. GUARDAMOS SOLO EL NOMBRE
                            this.nombreUsuario = usuario;

                            // 3. ENVIAMOS DE VUELTA SOLO EL NOMBRE
                            enviarMensaje(new Mensaje(TipoMensaje.LOGIN_EXITO, usuario));

                            lobby.agregarJugador(this);
                            System.out.println("Login: " + usuario);
                        }
                    }
                    // --- SI YA ESTÁ LOGUEADO ---
                    else {
                        if (salaActual != null) {
                            salaActual.procesarJugada(this, msj);
                        } else if (lobby != null) {
                            lobby.procesarMensaje(this, msj);
                        }
                    }

                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.out.println("Desconexión: " + nombreUsuario);
        } finally {
            cerrarConexion();
        }
    }

    public void enviarMensaje(Mensaje msj) {
        try {
            out.writeObject(msj);
            out.flush();
            out.reset();
        } catch (IOException e) {
            conectado = false;
        }
    }

    private void cerrarConexion() {
        try {
            conectado = false;
            if (lobby != null) lobby.removerJugador(this);
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNombreUsuario() { return nombreUsuario; }
    public void setSalaActual(GameManager sala) { this.salaActual = sala; }
    public GameManager getSalaActual() { return salaActual; }
}