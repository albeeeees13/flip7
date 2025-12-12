package com.flip7.server;

import com.flip7.server.logic.LobbyManager;
import com.flip7.server.network.ClientHandler;
import java.net.ServerSocket;
import java.net.Socket;

public class MainServer {
    public static void main(String[] args) {
        int puerto = 12345;


        LobbyManager lobby = new LobbyManager();

        System.out.println("Servidor Flip 7 iniciado en puerto " + puerto);

        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            while (true) {

                Socket socketCliente = serverSocket.accept();
                System.out.println("Nuevo cliente conectado: " + socketCliente.getInetAddress());


                ClientHandler handler = new ClientHandler(socketCliente, lobby);

                new Thread(handler).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}