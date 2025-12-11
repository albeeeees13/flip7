package com.flip7.client.network;

import com.flip7.client.Controller.GameController;
import com.flip7.common.network.Mensaje;
import java.io.*;
import java.net.Socket;

public class ClientConnection implements Runnable {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean conectado = false;

    // AGREGA ESTA VARIABLE
    private GameController controller;

    // --- MODIFICA EL CONSTRUCTOR PARA QUE RECIBA EL CONTROLLER ---
    public ClientConnection(String ip, int puerto, GameController controller) throws IOException {
        this.socket = new Socket(ip, puerto);
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
        this.controller = controller; // <--- Guardamos la referencia
        this.conectado = true;
    }

    @Override
    public void run() {
        try {
            while (conectado) {
                Mensaje msj = (Mensaje) in.readObject();
                // AVISAMOS AL CONTROLADOR CUANDO LLEGA ALGO
                if (controller != null) {
                    controller.recibirMensaje(msj);
                }
            }
        } catch (Exception e) {
            System.out.println("Desconectado del servidor");
        }
    }

    public void enviarMensaje(Mensaje msj) {
        try {
            out.writeObject(msj);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}