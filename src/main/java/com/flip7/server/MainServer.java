package com.flip7.server;

import com.flip7.server.data.DatabaseConnection;
import com.flip7.server.network.ServerManager;

public class MainServer {

    public static void main(String[] args) {
        System.out.println("--- INICIANDO SERVIDOR FLIP 7 ---");
        
        DatabaseConnection.inicializarBD();

        ServerManager server = new ServerManager();
        server.iniciar();
    }
}