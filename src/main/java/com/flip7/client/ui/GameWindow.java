package com.flip7.client.ui;

import com.flip7.client.Controller.GameController;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.model.Carta;
import com.flip7.common.network.Mensaje;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GameWindow extends JFrame {

    private GameController controller;
    private JPanel panelMesa; // Zona cartas
    private JPanel panelLateral; // Zona Chat/Info
    private JTextArea areaChat;
    private JTextField inputChat;
    private JButton btnFlip, btnPlantarse;

    public GameWindow(GameController controller) {
        this.controller = controller;
        this.controller.setCurrentView(this);

        setTitle("Flip 7 - Mesa de Juego");
        setSize(1000, 700); // Hacemos la ventana más ancha
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Importante para cerrar procesos
        setLayout(new BorderLayout());

        // --- 1. PANEL CENTRAL (MESA VERDE) ---
        panelMesa = new JPanel();
        panelMesa.setBackground(new Color(39, 119, 20)); // Verde tapete más elegante
        panelMesa.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 20)); // Cartas centradas

        // Scroll por si hay muchas cartas
        JScrollPane scrollMesa = new JScrollPane(panelMesa);
        scrollMesa.setBorder(null); // Sin borde feo
        add(scrollMesa, BorderLayout.CENTER);


        // --- 2. PANEL LATERAL (DERECHA - CHAT E INFO) ---
        panelLateral = new JPanel();
        panelLateral.setLayout(new BorderLayout());
        panelLateral.setPreferredSize(new Dimension(250, 0));
        panelLateral.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Color.DARK_GRAY));

        // Area de Chat (Log del juego)
        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        areaChat.setFont(new Font("Consolas", Font.PLAIN, 12));
        areaChat.append("--- BIENVENIDO A FLIP 7 ---\n");

        JScrollPane scrollChat = new JScrollPane(areaChat);
        panelLateral.add(scrollChat, BorderLayout.CENTER);

        // Input de Chat
        JPanel panelInput = new JPanel(new BorderLayout());
        inputChat = new JTextField();
        JButton btnEnviar = new JButton(">");

        panelInput.add(inputChat, BorderLayout.CENTER);
        panelInput.add(btnEnviar, BorderLayout.EAST);
        panelLateral.add(panelInput, BorderLayout.SOUTH);

        add(panelLateral, BorderLayout.EAST);


        // --- 3. PANEL INFERIOR (BOTONES) ---
        JPanel panelControles = new JPanel();
        panelControles.setBackground(new Color(30, 30, 30)); // Gris oscuro
        panelControles.setBorder(new EmptyBorder(10, 10, 10, 10));

        btnFlip = crearBotonEstilizado("FLIP (Sacar)", new Color(70, 130, 180));
        btnPlantarse = crearBotonEstilizado("PLANTARSE", new Color(178, 34, 34));

        panelControles.add(btnFlip);
        panelControles.add(Box.createHorizontalStrut(20)); // Espacio
        panelControles.add(btnPlantarse);

        add(panelControles, BorderLayout.SOUTH);

        // --- EVENTOS ---
        btnFlip.addActionListener(e -> controller.enviarAccionJuego(TipoMensaje.ACCION_SACAR));
        btnPlantarse.addActionListener(e -> controller.enviarAccionJuego(TipoMensaje.ACCION_PLANTARSE));

        // Evento Chat (Enter o Botón)
        ActionListener enviarChatAction = e -> {
            String texto = inputChat.getText().trim();
            if (!texto.isEmpty()) {
                controller.enviarMensajeChat(texto);
                inputChat.setText("");
            }
        };
        inputChat.addActionListener(enviarChatAction);
        btnEnviar.addActionListener(enviarChatAction);
    }

    // Método auxiliar para botones bonitos
    private JButton crearBotonEstilizado(String texto, Color color) {
        JButton btn = new JButton(texto);
        btn.setBackground(color);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Arial", Font.BOLD, 14));
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(150, 40));
        return btn;
    }

    // Dibuja las cartas usando nuestro NUEVO CartaPanel
    public void actualizarMesa(List<Carta> cartas) {
        panelMesa.removeAll();
        for(Carta c : cartas) {
            panelMesa.add(new CartaPanel(c)); // <--- AQUÍ USAMOS LA CLASE QUE CREAMOS
        }
        panelMesa.revalidate();
        panelMesa.repaint();
    }

    // Método para escribir en el chat lateral
    public void agregarMensajeChat(String mensaje) {
        areaChat.append(mensaje + "\n");
        // Auto-scroll hacia abajo
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }
}