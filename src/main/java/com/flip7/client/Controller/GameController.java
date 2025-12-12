package com.flip7.client.Controller;

import com.flip7.client.ui.GameWindow;
import com.flip7.client.ui.LobbyWindow;
import com.flip7.client.network.ClientConnection;
import com.flip7.client.ui.LoginWindow;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.network.Mensaje;
import com.flip7.common.model.Carta;
import com.flip7.client.ui.DialogoObjetivo;
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


            case ERROR:
                String errorMsg = (String) msj.getContenido();
                if (errorMsg.equals("BUST")) {
                    // Animación de perder
                    if (currentView instanceof GameWindow) {
                        ((GameWindow) currentView).mostrarEfectoBust();
                    }
                } else {
                    // Error normal (Popup)
                    JOptionPane.showMessageDialog(currentView, "⚠️ " + errorMsg);
                }
                break;

            case LISTA_SALAS:
                if (currentView instanceof LobbyWindow) {
                    String[] salas = (String[]) msj.getContenido();
                    ((LobbyWindow) currentView).actualizarListaSalas(salas);
                }
                break;

            case SOLICITAR_OBJETIVO:
                String[] datos = (String[]) msj.getContenido();
                String nombreAccion = datos[0];

                String[] rivales = new String[datos.length - 1];
                System.arraycopy(datos, 1, rivales, 0, rivales.length);

                mostrarSelectorObjetivo(nombreAccion, rivales);
                break;

            case ACTUALIZAR_TABLERO:
                if (currentView instanceof GameWindow) {
                    Object contenido = msj.getContenido();

                    if (contenido instanceof List) {
                        List<Carta> cartas = (List<Carta>) contenido;
                        ((GameWindow) currentView).actualizarMesa(cartas);
                    } else if (contenido instanceof String) {
                        String texto = (String) contenido;
                        currentView.setTitle("Flip 7 - " + texto);
                    }
                }
                break;
            case ACTUALIZAR_OPONENTES:
                if (currentView instanceof GameWindow) {

                    java.util.Map<String, java.util.List<Carta>> oponentes =
                            (java.util.Map<String, java.util.List<Carta>>) msj.getContenido();

                    ((GameWindow) currentView).actualizarOponentes(oponentes);
                }
                break;



            case INICIO_JUEGO:
                JOptionPane.showMessageDialog(currentView, "🎮 " + msj.getContenido());
                break;

            case MENSAJE_CHAT:
                if (currentView instanceof GameWindow) {
                    ((GameWindow) currentView).agregarMensajeChat((String) msj.getContenido());
                } else {
                    JOptionPane.showMessageDialog(currentView, "Chat: " + msj.getContenido());
                }
                break;
        }
    }

    private void abrirLobby() {
        if (currentView != null) currentView.dispose();
        LobbyWindow lobby = new LobbyWindow(this);
        lobby.setVisible(true);
        this.currentView = lobby;
    }

    private void mostrarSelectorObjetivo(String accion, String[] rivales) {
        if (currentView instanceof JFrame) {
            DialogoObjetivo dialog = new DialogoObjetivo((JFrame) currentView, rivales, accion);
            dialog.setVisible(true);

            String elegido = dialog.getSeleccionado();
            if (elegido == null) elegido = "SELF";

            connection.enviarMensaje(new Mensaje(TipoMensaje.SELECCIONAR_OBJETIVO, elegido));
        }
    }


    public void crearSala(int limite) {
        if (connection != null) {

            connection.enviarMensaje(new Mensaje(TipoMensaje.CREAR_SALA, String.valueOf(limite)));
        }
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