package com.flip7.client.ui;

import com.flip7.client.Controller.GameController;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.model.Carta;
import com.flip7.common.network.Mensaje;

import javax.swing.*;
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

        setTitle("Flip 7 - Partida en Curso");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        panelOponentes = new JPanel();
        panelOponentes.setPreferredSize(new Dimension(0, 200)); // Altura fija
        panelOponentes.setBackground(new Color(50, 50, 50)); // Gris oscuro
        panelOponentes.setLayout(new FlowLayout(FlowLayout.LEFT)); // Se irán agregando a la izquierda

        JScrollPane scrollOponentes = new JScrollPane(panelOponentes);
        scrollOponentes.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        add(scrollOponentes, BorderLayout.NORTH);

        panelMiMesa = new JPanel();
        panelMiMesa.setBackground(new Color(39, 119, 20)); // Verde Casino
        panelMiMesa.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 20));


        TitledBorder bordeMio = BorderFactory.createTitledBorder("TU ZONA");
        bordeMio.setTitleColor(Color.WHITE);
        panelMiMesa.setBorder(bordeMio);

        add(new JScrollPane(panelMiMesa), BorderLayout.CENTER);


        JPanel panelLateral = new JPanel(new BorderLayout());
        panelLateral.setPreferredSize(new Dimension(280, 0));

        areaChat = new JTextArea();
        areaChat.setEditable(false);
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
        btnFlip = new JButton("FLIP (Sacar)");
        btnPlantarse = new JButton("PLANTARSE");
        panelControles.add(btnFlip);
        panelControles.add(btnPlantarse);
        add(panelControles, BorderLayout.SOUTH);


        btnFlip.addActionListener(e -> controller.enviarAccionJuego(TipoMensaje.ACCION_SACAR));
        btnPlantarse.addActionListener(e -> controller.enviarAccionJuego(TipoMensaje.ACCION_PLANTARSE));

        ActionListener chatAction = e -> {
            if(!inputChat.getText().isEmpty()){
                controller.enviarMensajeChat(inputChat.getText());
                inputChat.setText("");
            }
        };
        inputChat.addActionListener(chatAction);
        btnEnviar.addActionListener(chatAction);
    }


    public void actualizarMesa(List<Carta> cartas) {
        panelMiMesa.removeAll();
        for(Carta c : cartas) {
            panelMiMesa.add(new CartaPanel(c)); // Usa el CartaPanel bonito
        }
        panelMiMesa.revalidate();
        panelMiMesa.repaint();
    }


    public void actualizarOponentes(Map<String, List<Carta>> estadoOponentes) {
        panelOponentes.removeAll();

        for (Map.Entry<String, List<Carta>> entrada : estadoOponentes.entrySet()) {
            String nombre = entrada.getKey();
            List<Carta> cartas = entrada.getValue();


            JPanel miniTablero = new JPanel();
            miniTablero.setPreferredSize(new Dimension(200, 180));
            miniTablero.setBackground(new Color(30, 100, 30)); // Verde más oscuro
            miniTablero.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(Color.WHITE), nombre,
                    TitledBorder.CENTER, TitledBorder.TOP, new Font("Arial", Font.BOLD, 12), Color.WHITE));


            for(Carta c : cartas) {
                // Aquí podrías hacer un CartaPanelPequeño, o escalar el normal
                CartaPanel p = new CartaPanel(c);
                p.setPreferredSize(new Dimension(40, 60)); // Carta mini
                miniTablero.add(p);
            }
            panelOponentes.add(miniTablero);
        }
        panelOponentes.revalidate();
        panelOponentes.repaint();
    }

    public void agregarMensajeChat(String msj) { areaChat.append(msj + "\n"); }
}