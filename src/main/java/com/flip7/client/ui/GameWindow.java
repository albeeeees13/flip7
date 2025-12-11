package com.flip7.client.ui;

import com.flip7.client.Controller.GameController;
import com.flip7.common.enums.TipoMensaje; // <--- SI ESTO FALTA, DA ERROR
import com.flip7.common.model.Carta;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GameWindow extends JFrame {

    // --- ¡OJO KEVIN! ESTAS 3 LÍNEAS SON LAS QUE TE FALTABAN ---
    // Si no pones esto aquí, Java no sabe qué es "panelMesa" ni "btnFlip"
    private GameController controller;
    private JPanel panelMesa;
    private JButton btnFlip, btnPlantarse;
    // -----------------------------------------------------------

    public GameWindow(GameController controller) {
        this.controller = controller;
        // Si esto da rojo, es porque no hiciste el PASO 1 (GameController)
        this.controller.setCurrentView(this);

        setTitle("Flip 7 - En Juego");
        setSize(800, 600);
        setLayout(new BorderLayout());

        // Panel Mesa
        panelMesa = new JPanel();
        panelMesa.setBackground(new Color(34, 139, 34));
        add(panelMesa, BorderLayout.CENTER);

        // Panel Botones
        JPanel panelControles = new JPanel();
        btnFlip = new JButton("FLIP (Sacar)");
        btnPlantarse = new JButton("Plantarse");

        panelControles.add(btnFlip);
        panelControles.add(btnPlantarse);
        add(panelControles, BorderLayout.SOUTH);

        // Eventos
        btnFlip.addActionListener(e -> {
            controller.enviarAccionJuego(TipoMensaje.ACCION_SACAR);
        });

        btnPlantarse.addActionListener(e -> {
            controller.enviarAccionJuego(TipoMensaje.ACCION_PLANTARSE);
        });
    }

    public void actualizarMesa(List<Carta> cartas) {
        panelMesa.removeAll();
        for(Carta c : cartas) {
            JLabel labelCarta = new JLabel(String.valueOf(c.getValor()));
            labelCarta.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            labelCarta.setPreferredSize(new Dimension(80, 120));
            labelCarta.setOpaque(true);
            labelCarta.setBackground(Color.WHITE);
            panelMesa.add(labelCarta);
        }
        panelMesa.revalidate();
        panelMesa.repaint();
    }
}