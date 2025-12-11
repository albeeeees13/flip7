package com.flip7.client.controller;

import com.flip7.client.network.ClientConnection;
import com.flip7.client.ui.LoginWindow;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.network.Mensaje;

import javax.swing.*;

public class GameController {

    private ClientConnection connection;
    private LoginWindow loginWindow;

    public GameController() {
        // Inicialmente no estamos conectados
    }

    public void setLoginWindow(LoginWindow window) {
        this.loginWindow = window;
    }

    // Acción de conectar (llamada desde el botón de la UI)
    public void conectar(String ip, int puerto, String usuario) {
        connection = new ClientConnection(ip, puerto, this);
        if (connection.conectar()) {
            // Si conecta el socket, intentamos el Login lógico
            connection.enviarMensaje(new Mensaje(TipoMensaje.LOGIN, usuario));
        } else {
            JOptionPane.showMessageDialog(loginWindow, "No se pudo conectar al servidor.");
        }
    }

    // Método que recibe TODO lo que llega del servidor (Joahan)
    public void recibirMensaje(Mensaje msj) {
        System.out.println("Cliente recibió: " + msj.getTipo());

        switch (msj.getTipo()) {
            case LOGIN_EXITO:
                JOptionPane.showMessageDialog(loginWindow, "¡Conectado! " + msj.getContenido());
                // TODO: Aquí cerrarías loginWindow y abrirías LobbyWindow
                // loginWindow.dispose();
                // new LobbyWindow(this).setVisible(true);
                break;

            case ERROR:
                JOptionPane.showMessageDialog(loginWindow, "Error: " + msj.getContenido());
                break;

            case MENSAJE_CHAT:
                System.out.println("Chat: " + msj.getContenido());
                break;
        }
    }

    public void enviarMensaje(Mensaje msj) {
        if (connection != null) {
            connection.enviarMensaje(msj);
        }
    }

    public void crearSala() {
        connection.enviarMensaje(new Mensaje(TipoMensaje.CREAR_SALA, null));
    }

    public void unirseSala(String idSala) {
        connection.enviarMensaje(new Mensaje(TipoMensaje.UNIRSE_SALA, idSala));
    }
}
