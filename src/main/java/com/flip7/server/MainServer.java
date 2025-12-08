package com.flip7.server;

import com.flip7.server.network.ServerManager;

public class MainServer {
    public static void main(String[] args) {

        ServerManager server = new ServerManager();
        server.iniciar();
    }
}