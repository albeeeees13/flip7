package com.flip7.client.ui;

import com.flip7.client.Controller.GameController;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.model.Carta;
import com.flip7.common.network.Mensaje;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

public class GameWindow extends JFrame {

    private GameController controller;

    private JPanel panelOponentes;


    private JPanel panelMiMesa;


    private JTextArea areaChat;
    private JTextField inputChat;
    private JButton btnEnviar;


    private JButton btnFlip, btnPlantarse;

    public GameWindow(GameController controller) {
        this.controller = controller;
        this.controller.setCurrentView(this);

        setTitle("Flip 7 - Mesa de Juego");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());


        panelOponentes = new JPanel();
        panelOponentes.setPreferredSize(new Dimension(0, 220));
        panelOponentes.setBackground(new Color(50, 50, 50)); // Gris oscuro
        panelOponentes.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JScrollPane scrollOponentes = new JScrollPane(panelOponentes);
        scrollOponentes.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        scrollOponentes.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scrollOponentes, BorderLayout.NORTH);


        panelMiMesa = new JPanel();
        panelMiMesa.setBackground(new Color(39, 119, 20)); // Verde Tapete
        panelMiMesa.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 20));

        // Título visual
        TitledBorder bordeMio = BorderFactory.createTitledBorder("TU ZONA");
        bordeMio.setTitleColor(Color.WHITE);
        bordeMio.setTitleFont(new Font("Arial", Font.BOLD, 14));
        panelMiMesa.setBorder(bordeMio);

        add(new JScrollPane(panelMiMesa), BorderLayout.CENTER);


        JPanel panelLateral = new JPanel(new BorderLayout());
        panelLateral.setPreferredSize(new Dimension(280, 0));
        panelLateral.setBorder(BorderFactory.createMatteBorder(0, 2, 0, 0, Color.DARK_GRAY));

        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setFont(new Font("Consolas", Font.PLAIN, 12));
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        areaChat.append("--- SALA DE JUEGO ---\n");

        panelLateral.add(new JScrollPane(areaChat), BorderLayout.CENTER);

        JPanel panelInput = new JPanel(new BorderLayout());
        inputChat = new JTextField();
        btnEnviar = new JButton(">");

        panelInput.add(inputChat, BorderLayout.CENTER);
        panelInput.add(btnEnviar, BorderLayout.EAST);
        panelLateral.add(panelInput, BorderLayout.SOUTH);

        add(panelLateral, BorderLayout.EAST);


        JPanel panelControles = new JPanel();
        panelControles.setBackground(new Color(30, 30, 30));
        panelControles.setBorder(new EmptyBorder(10, 10, 10, 10));

        btnFlip = new JButton("FLIP (Sacar)");
        btnFlip.setPreferredSize(new Dimension(150, 40));
        btnFlip.setBackground(new Color(70, 130, 180));
        btnFlip.setForeground(Color.WHITE);

        btnPlantarse = new JButton("PLANTARSE");
        btnPlantarse.setPreferredSize(new Dimension(150, 40));
        btnPlantarse.setBackground(new Color(178, 34, 34));
        btnPlantarse.setForeground(Color.WHITE);

        panelControles.add(btnFlip);
        panelControles.add(Box.createHorizontalStrut(20));
        panelControles.add(btnPlantarse);

        add(panelControles, BorderLayout.SOUTH);


        btnFlip.addActionListener(e -> controller.enviarAccionJuego(TipoMensaje.ACCION_SACAR));
        btnPlantarse.addActionListener(e -> controller.enviarAccionJuego(TipoMensaje.ACCION_PLANTARSE));

        ActionListener chatAction = e -> {
            String texto = inputChat.getText().trim();
            if (!texto.isEmpty()) {
                controller.enviarMensajeChat(texto);
                inputChat.setText("");
            }
        };
        inputChat.addActionListener(chatAction);
        btnEnviar.addActionListener(chatAction);
    }


    public void actualizarMesa(List<Carta> cartas) {
        panelMiMesa.removeAll();
        for(Carta c : cartas) {
            panelMiMesa.add(new CartaPanel(c)); // Usa nuestra clase visual bonita
        }
        panelMiMesa.revalidate();
        panelMiMesa.repaint();
    }


    public void mostrarEfectoBust() {
        for (Component comp : panelMiMesa.getComponents()) {
            if (comp instanceof CartaPanel) {
                ((CartaPanel) comp).setGris(true);
            }
        }
        panelMiMesa.revalidate();
        panelMiMesa.repaint();
    }

    // 3. Método para el Chat
    public void agregarMensajeChat(String mensaje) {
        areaChat.append(mensaje + "\n");
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }


    public void actualizarOponentes(Map<String, List<Carta>> oponentes) {
        // ... lógica pendiente de rivales ...
    }
}