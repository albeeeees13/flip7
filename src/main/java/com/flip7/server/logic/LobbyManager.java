package com.flip7.server.logic;

import com.flip7.server.network.ClientHandler;
import com.flip7.common.network.Mensaje;
import com.flip7.common.enums.TipoMensaje;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyManager {

    private Map<String, GameManager> salasActivas;
    private List<ClientHandler> jugadoresEnLobby;

    public LobbyManager() {
        this.salasActivas = new ConcurrentHashMap<>();
        this.jugadoresEnLobby = new ArrayList<>();
    }

    // --- ESTE ES EL MÉTODO NUEVO QUE NECESITAS PARA LEER EL MENSAJE ---
    public synchronized void procesarMensaje(ClientHandler cliente, Mensaje msj) {
        switch (msj.getTipo()) {
            case CREAR_SALA:
                try {
                    // Leemos el número que mandó el cliente (ej: "4")
                    String contenido = (String) msj.getContenido();
                    // Si viene vacío, por defecto 4, si no, lo convertimos
                    int limite = (contenido != null && !contenido.isEmpty()) ? Integer.parseInt(contenido) : 4;

                    // Llamamos a crear sala con el límite
                    crearSala(cliente, limite);

                } catch (NumberFormatException e) {
                    cliente.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "Error: Límite inválido."));
                }
                break;

            case UNIRSE_SALA:
                String idSala = (String) msj.getContenido();
                unirseASala(idSala, cliente);
                break;

            case LISTA_SALAS:
                enviarListaSalas(cliente);
                break;
        }
    }
    // ------------------------------------------------------------------

    public synchronized void agregarJugador(ClientHandler jugador) {
        jugadoresEnLobby.add(jugador);
        enviarListaSalas(jugador);
    }

    public synchronized void removerJugador(ClientHandler jugador) {
        jugadoresEnLobby.remove(jugador);
        // Si el jugador estaba en una sala, el GameManager manejará su desconexión
    }

    public synchronized boolean estaJugadorConectado(String nombre) {
        for (ClientHandler c : jugadoresEnLobby) {
            if (c.getNombreUsuario().equalsIgnoreCase(nombre)) {
                return true;
            }
        }
        return false;
    }

    // MODIFICADO: Ahora recibe el límite
    public synchronized void crearSala(ClientHandler anfitrion, int limite) {
        // Generamos un ID simple
        String idSala = "Sala de " + anfitrion.getNombreUsuario();

        if (salasActivas.containsKey(idSala)) {
            anfitrion.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "Ya tienes una sala creada."));
            return;
        }

        // --- AQUÍ ESTABA TU ERROR ROJO ---
        // Ahora usamos el constructor correcto con 2 parámetros
        GameManager nuevaSala = new GameManager(idSala, limite);
        // ---------------------------------

        salasActivas.put(idSala, nuevaSala);

        // Sacamos al jugador del lobby y lo metemos a su sala
        // OJO: unirseASala busca en el mapa, así que ya debe estar puesta
        unirseASala(idSala, anfitrion);

        // Avisamos a los demás
        broadcastListaSalas();
    }

    public synchronized void unirseASala(String idSala, ClientHandler jugador) {
        GameManager sala = salasActivas.get(idSala);

        if (sala != null) {
            jugador.setSalaActual(sala);
            // El GameManager se encarga de aceptar o rechazar (por límite)
            sala.agregarJugador(jugador);

            // Confirmamos
            jugador.enviarMensaje(new Mensaje(TipoMensaje.UNIRSE_SALA, idSala));
        } else {
            jugador.enviarMensaje(new Mensaje(TipoMensaje.ERROR, "La sala no existe."));
        }
    }

    private void broadcastListaSalas() {
        String[] nombresSalas = salasActivas.keySet().toArray(new String[0]);
        Mensaje msgUpdate = new Mensaje(TipoMensaje.LISTA_SALAS, nombresSalas);

        for (ClientHandler c : jugadoresEnLobby) {
            if (c.getSalaActual() == null) {
                c.enviarMensaje(msgUpdate);
            }
        }
    }

    private void enviarListaSalas(ClientHandler c) {
        String[] nombresSalas = salasActivas.keySet().toArray(new String[0]);
        c.enviarMensaje(new Mensaje(TipoMensaje.LISTA_SALAS, nombresSalas));
    }
}