package com.flip7.server.logic;

import com.flip7.server.network.ClientHandler;
import java.util.ArrayList;
import java.util.List;

public class LobbyManager {
   
    private List<ClientHandler> clientesConectados;

    public LobbyManager() {
        this.clientesConectados = new ArrayList<>();
    }


    public synchronized void agregarJugador(ClientHandler cliente) {
        clientesConectados.add(cliente);
        System.out.println("Lobby: Jugador agregado. Total conectados: " + clientesConectados.size());

    }


    public synchronized void removerJugador(ClientHandler cliente) {
        clientesConectados.remove(cliente);
        System.out.println("Lobby: Jugador desconectado. Quedan: " + clientesConectados.size());
    }


  public void broadcast(Mensaje msj) {
        for (ClientHandler c : clientesConectados) {
            c.enviarMensaje(msj);
        }
    }

}