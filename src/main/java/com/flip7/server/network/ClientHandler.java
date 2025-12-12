package com.flip7.server.network;

import com.flip7.server.logic.GameManager;
import com.flip7.server.logic.LobbyManager;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.network.Mensaje;
import com.flip7.server.logic.ValidadorCredenciales;
import com.flip7.server.data.UsuarioDAO;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String nombreUsuario;
    private boolean conectado = true;

    private LobbyManager lobby;
    private GameManager salaActual;

    public ClientHandler(Socket socket, LobbyManager lobby) {
        this.socket = socket;
        this.lobby = lobby;
        try {
            this.out = new ObjectOutputStream(socket.getOutputStream());
            this.in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
            conectado = false;
        }
    }

    @Override
    public void run() {
        try {
            // Opcional: Agregar al lobby solo si ya hizo login,
            // pero si tu lógica lo pide antes, déjalo aquí.
            if (conectado && lobby != null) {
                lobby.agregarJugador(this);
            }
            while (conectado) {
                Mensaje mensajeRecibido = (Mensaje) in.readObject();
                System.out.println("Mensaje recibido de " + getNombreUsuario() + ": " + mensajeRecibido.getTipo());
                procesarMensaje(mensajeRecibido);
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Cliente desconectado: " + getNombreUsuario());
        } finally {
            cerrarConexion();
        }
    }

    private void procesarMensaje(Mensaje msj) {
        switch (msj.getTipo()) {
            case LOGIN:
                String contenido = (String) msj.getContenido();

                // 1. Validación de Formato Básico
                if (contenido == null || !contenido.contains(",")) {
                    enviarMensaje(new Mensaje(TipoMensaje.ERROR, "Formato incorrecto. Envíe: usuario,contraseña"));
                    break;
                }

                String[] partes = contenido.split(",");
                if (partes.length < 2) {
                    enviarMensaje(new Mensaje(TipoMensaje.ERROR, "Falta la contraseña."));
                    break;
                }

                String usuarioInput = partes[0].trim();
                String passInput = partes[1].trim();

                // 2. Validación de Lógica de Negocio (SRP - Clase ValidadorCredenciales)
                String errorValidacion = ValidadorCredenciales.validarUsuario(usuarioInput);
                if (!errorValidacion.equals("OK")) {
                    enviarMensaje(new Mensaje(TipoMensaje.ERROR, errorValidacion));
                    break;
                }

                // 3. Validación de Sesión en Memoria (¿Ya está conectado?)
                if (lobby.estaJugadorConectado(usuarioInput)) {
                    enviarMensaje(new Mensaje(TipoMensaje.ERROR, "La cuenta '" + usuarioInput + "' ya está en uso."));
                    break;
                }


                UsuarioDAO dao = new UsuarioDAO();

                if (dao.login(usuarioInput, passInput)) {
                    this.nombreUsuario = usuarioInput;
                    enviarMensaje(new Mensaje(TipoMensaje.LOGIN_EXITO, "Bienvenido de nuevo, " + usuarioInput));
                } else {
                    boolean registrado = dao.registrar(usuarioInput, passInput);
                    if (registrado) {
                        this.nombreUsuario = usuarioInput;
                        enviarMensaje(new Mensaje(TipoMensaje.LOGIN_EXITO, "Cuenta creada. Bienvenido " + usuarioInput));
                    } else {
                        enviarMensaje(new Mensaje(TipoMensaje.ERROR, "Contraseña incorrecta."));
                    }
                }
                break;

            case CREAR_SALA:
                // Ahora sí llamamos al lobby
                if (lobby != null) {
                    lobby.crearSala(this);
                }
                break;

            case UNIRSE_SALA:
                String idSala = (String) msj.getContenido();
                if (lobby != null) {
                    lobby.unirseASala(idSala, this);
                }
                break;

            case MENSAJE_CHAT:
                if (salaActual != null) {

                } else {
                    enviarMensaje(new Mensaje(TipoMensaje.ERROR, "No puedes chatear fuera de una partida."));
                }
                break;
            case ACCION_SACAR:
            case ACCION_PLANTARSE:
            case SELECCIONAR_OBJETIVO:
                if (salaActual != null) {

                    salaActual.procesarJugada(this, msj);
                } else {
                    enviarMensaje(new Mensaje(TipoMensaje.ERROR, "No estás en una partida activa."));
                }
                break;
            default:
                System.out.println("Mensaje no manejado o desconocido: " + msj.getTipo());
        }
    }

    public void enviarMensaje(Mensaje msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("Error enviando mensaje a " + nombreUsuario);
            cerrarConexion();
        }
    }

    public String getNombreUsuario() {
        return (nombreUsuario != null) ? nombreUsuario : "Anónimo";
    }

    public void setSalaActual(GameManager sala) {
        this.salaActual = sala;
    }

    public GameManager getSalaActual() {
        return this.salaActual;
    }

    private void cerrarConexion() {
        conectado = false;
        if (lobby != null) lobby.removerJugador(this);
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}