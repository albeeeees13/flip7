package com.flip7.client.Controller;

import com.flip7.client.ui.GameWindow;
import com.flip7.client.ui.LobbyWindow;
import com.flip7.client.network.ClientConnection;
import com.flip7.client.ui.LoginWindow;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.network.Mensaje;
import com.flip7.common.model.Carta;
import java.util.List;
import javax.swing.*;

public class GameController {

    private ClientConnection connection;

    private JFrame currentView;
    private LoginWindow loginWindow;

    public GameController() {
    }

    public void setCurrentView(JFrame view) {
        this.currentView = view;
    }

    public void setLoginWindow(LoginWindow window) {
        this.loginWindow = window;
        this.currentView = window;
    }

    public void conectar(String ip, int puerto, String usuario, String password, String accion) {
        try {
            connection = new ClientConnection(ip, puerto, this);
            new Thread(connection).start();

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
                JOptionPane.showMessageDialog(currentView, "¡Conectado! " + msj.getContenido());
                abrirLobby();
                break;

            case UNIRSE_SALA:

                abrirJuego();
                break;

            case LISTA_SALAS:
                if (currentView instanceof LobbyWindow) {
                    // Actualizamos la lista del lobby
                    String[] salas = (String[]) msj.getContenido();
                    ((LobbyWindow) currentView).actualizarListaSalas(salas);
                }
                break;


            case ACTUALIZAR_TABLERO:
                if (currentView instanceof GameWindow) {
                    Object contenido = msj.getContenido();


                    if (contenido instanceof List) {
                        List<Carta> cartas = (List<Carta>) contenido;
                        ((GameWindow) currentView).actualizarMesa(cartas);
                    }

                    else if (contenido instanceof String) {
                        String texto = (String) contenido;

                        currentView.setTitle("Flip 7 - " + texto);
                    }
                }
                break;

            case ERROR:
                JOptionPane.showMessageDialog(currentView, "⚠️ " + msj.getContenido());
                break;

            case INICIO_JUEGO:
                JOptionPane.showMessageDialog(currentView, "🎮 " + msj.getContenido());
                break;

            case MENSAJE_CHAT:
                JOptionPane.showMessageDialog(currentView, "💬 " + msj.getContenido());
                break;
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
    public void enviarMensajeChat(String texto) {
        if (connection != null) {
            connection.enviarMensaje(new Mensaje(TipoMensaje.MENSAJE_CHAT, texto));
        }
    }

    private void abrirJuego() {
        if (currentView != null) currentView.dispose();
        GameWindow game = new GameWindow(this);
        game.setVisible(true);
        this.currentView = game;
    }
    public void enviarAccionJuego(TipoMensaje accion) {
        if (connection != null) {
            connection.enviarMensaje(new Mensaje(accion, null));
        }
    }
}