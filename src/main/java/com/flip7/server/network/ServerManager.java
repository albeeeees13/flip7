package com.flip7.server.network;

import com.flip7.server.logic.LobbyManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerManager {
    private static final int PUERTO = 12345;
    private ServerSocket serverSocket;
    private boolean corriendo = false;

    private LobbyManager lobbyManager;

    public ServerManager() {
        this.lobbyManager = new LobbyManager();
        System.out.println("LobbyManager inicializado.");
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

                ClientHandler handler = new ClientHandler(socketCliente, this.lobbyManager);

                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}