package com.flip7.client.Controller;

import com.flip7.client.ui.GameWindow;
import com.flip7.client.ui.LobbyWindow;
import com.flip7.client.network.ClientConnection;
import com.flip7.client.ui.LoginWindow;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.network.Mensaje;

import javax.swing.*;

public class GameController {

    private ClientConnection connection;
    // 1. CORRECCIÓN: Agregamos estas variables para que no den error
    private JFrame currentView;
    private LoginWindow loginWindow;

    public GameController() {
    }

    public void setCurrentView(JFrame view) {
        this.currentView = view;
    }

    public void setLoginWindow(LoginWindow window) {
        this.loginWindow = window;
        this.currentView = window; // Actualizamos la vista actual también
    }

    // 2. CORRECCIÓN: Actualizamos los parámetros para que coincidan con LoginWindow (5 datos)
    public void conectar(String ip, int puerto, String usuario, String password, String accion) {
        try {
            // Creamos la conexión y arrancamos el hilo
            connection = new ClientConnection(ip, puerto, this);
            new Thread(connection).start();

            // Enviamos el login con usuario y contraseña
            String payload = usuario + "," + password;
            connection.enviarMensaje(new Mensaje(TipoMensaje.LOGIN, payload));

        } catch (Exception e) {
            JOptionPane.showMessageDialog(currentView, "No se pudo conectar: " + e.getMessage());
        }
    }

    // Método que recibe mensajes del servidor (Llamado desde ClientConnection)
    public void recibirMensaje(Mensaje msj) {
        System.out.println("Cliente recibió: " + msj.getTipo());

        switch (msj.getTipo()) {
            case LOGIN_EXITO:
                JOptionPane.showMessageDialog(currentView, "¡Conectado! Bienvenido " + msj.getContenido());
                abrirLobby(); // Cambiamos de ventana
                break;

            case ERROR:
                JOptionPane.showMessageDialog(currentView, "Error: " + msj.getContenido());
                break;

            // Agrega aquí más casos si necesitas (CHAT, etc.)
        }
    }


    private void abrirLobby() {
        if (currentView != null) currentView.dispose();
        LobbyWindow lobby = new LobbyWindow(this);
        lobby.setVisible(true);
        this.currentView = lobby;
    }


    public void crearSala() {
        if (connection != null) connection.enviarMensaje(new Mensaje(TipoMensaje.CREAR_SALA, null));
    }

    public void unirseSala(String idSala) {
        if (connection != null) connection.enviarMensaje(new Mensaje(TipoMensaje.UNIRSE_SALA, idSala));
    }


    public void enviarAccionJuego(TipoMensaje accion) {
        if (connection != null) {
            connection.enviarMensaje(new Mensaje(accion, null));
        }
    }
}