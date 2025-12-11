package com.flip7.server.logic;

import com.flip7.server.network.ClientHandler;
import com.flip7.common.network.Mensaje;
import com.flip7.common.enums.TipoMensaje;
import java.util.*;

public class LobbyManager {


    private Map<String, GameManager> salasActivas;
    private List<ClientHandler> jugadoresEnLobby;

    public LobbyManager() {
        this.salasActivas = new HashMap<>();
        this.jugadoresEnLobby = new ArrayList<>();
    }

    public synchronized void agregarJugador(ClientHandler jugador) {
        jugadoresEnLobby.add(jugador);
        enviarListaSalas(jugador);
    }

    public synchronized void removerJugador(ClientHandler jugador) {
        jugadoresEnLobby.remove(jugador);
        // Aquí podrías agregar lógica para sacarlo de una sala si se desconecta
    }

    // Validar si el nombre ya existe (Para tu duda de "gerber")
    public synchronized boolean estaJugadorConectado(String nombre) {
        for (ClientHandler c : jugadoresEnLobby) {
            if (c.getNombreUsuario().equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void crearSala(ClientHandler anfitrion) {
        // Generamos un ID simple, ej: "Sala de Kevin"
        String idSala = "Sala de " + anfitrion.getNombreUsuario();

        if (salasActivas.containsKey(idSala)) {
            anfitrion.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "Ya tienes una sala creada."));
            return;
        }

        GameManager nuevaSala = new GameManager(idSala);
        salasActivas.put(idSala, nuevaSala);

        // Sacamos al jugador del lobby general y lo metemos a su sala
        unirseASala(idSala, anfitrion);

        // Avisamos a TODOS en el lobby que hay una sala nueva
        broadcastListaSalas();
    }

    public synchronized void unirseASala(String idSala, ClientHandler jugador) {
        GameManager sala = salasActivas.get(idSala);

        if (sala != null) {
            jugador.setSalaActual(sala); // Vinculamos al jugador con la sala
            sala.agregarJugador(jugador);

            // Le decimos al cliente: "¡Éxito! Abre la ventana de juego"
            // Usamos UNIRSE_SALA como confirmación
            jugador.enviarMensaje(new Mensaje(TipoMensaje.UNIRSE_SALA, "OK"));
        } else {
            jugador.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "La sala no existe."));
        }
    }

    private void broadcastListaSalas() {
        // Enviar la lista actualizada a todos los que siguen en el lobby
        String[] nombresSalas = salasActivas.keySet().toArray(new String[0]);
        Mensaje msgUpdate = new Mensaje(TipoMensaje.LISTA_SALAS, nombresSalas);

        for (ClientHandler c : jugadoresEnLobby) {
            if (c.getSalaActual() == null) { // Solo a los que no están jugando
                c.enviarMensaje(msgUpdate);
            }
        }
    }

    private void enviarListaSalas(ClientHandler c) {
        String[] nombresSalas = salasActivas.keySet().toArray(new String[0]);
        c.enviarMensaje(new Mensaje(TipoMensaje.LISTA_SALAS, nombresSalas));
    }
}