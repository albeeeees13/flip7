package com.flip7.client.network;

import com.flip7.common.network.Mensaje;
import com.flip7.client.controller.GameController;
import java.io.*;
import java.net.Socket;

public class ClientConnection implements Runnable {

    private String host;
    private int puerto;
    private Socket socket;
    private ObjectOutputStream salida;
    private ObjectInputStream entrada;
    private boolean escuchando;

    // Referencia al controlador para avisarle cuando lleguen cosas
    private GameController controller;

    public ClientConnection(String host, int puerto, GameController controller) {
        this.host = host;
        this.puerto = puerto;
        this.controller = controller;
    }

    // Intenta conectar al servidor. Retorna true si tuvo éxito.
    public boolean conectar() {
        try {
            socket = new Socket(host, puerto);
            // OJO: Primero se crea el Output, igual que en el servidor
            salida = new ObjectOutputStream(socket.getOutputStream());
            entrada = new ObjectInputStream(socket.getInputStream());
            escuchando = true;

            // Arrancamos el hilo que escucha lo que dice el servidor
            new Thread(this).start();
            return true;
        } catch (IOException e) {
            System.err.println("No se pudo conectar al servidor: " + e.getMessage());
            return false;
        }
    }

    // Este hilo se queda esperando mensajes del Servidor
    @Override
    public void run() {
        while (escuchando) {
            try {
                // Leemos el objeto que nos manda el server (Joahan)
                Mensaje mensaje = (Mensaje) entrada.readObject();

                // Se lo pasamos al controlador para que decida qué hacer (actualizar UI, etc.)
                controller.recibirMensaje(mensaje);

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Conexión perdida con el servidor.");
                desconectar();
            }
        }
    }

    // Método que usarás desde la UI para mandar cosas (ej. "Tiro Carta")
    public void enviarMensaje(Mensaje mensaje) {
        try {
            salida.writeObject(mensaje);
            salida.flush();
        } catch (IOException e) {
            System.err.println("Error al enviar mensaje: " + e.getMessage());
        }
    }

    public void desconectar() {
        escuchando = false;
        try {
            if (socket != null) socket.close();
            if (entrada != null) entrada.close();
            if (salida != null) salida.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
