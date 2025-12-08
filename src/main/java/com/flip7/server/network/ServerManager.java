package com.flip7.server.network;

import com.flip7.server.logic.GameManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerManager {
    private static final int PUERTO = 12345; 
    private ServerSocket serverSocket;
    private boolean corriendo = false;


    private GameManager salaPrincipal;

    public ServerManager() {

        this.salaPrincipal = new GameManager("Sala-1");
    }

    public void iniciar() {
        try {
            serverSocket = new ServerSocket(PUERTO);
            corriendo = true;
            System.out.println("Servidor iniciado en el puerto: " + PUERTO);
            System.out.println("Esperando jugadores...");

            while (corriendo) {

                Socket socketCliente = serverSocket.accept();
                System.out.println("Nuevo cliente conectado: " + socketCliente.getInetAddress());


                ClientHandler handler = new ClientHandler(socketCliente);


                salaPrincipal.agregarJugador(handler);


                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}