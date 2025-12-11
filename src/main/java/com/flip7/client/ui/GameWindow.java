package com.flip7.client.ui;

import com.flip7.client.controller.GameController;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import com.flip7.common.model.Carta;

public class GameWindow extends JFrame {
    private GameController controller;
    private JPanel panelMesa;
    private JButton btnFlip, btnPlantarse;

    public GameWindow(GameController controller) {
        this.controller = controller;
        this.controller.setCurrentView(this);

        setTitle("Flip 7 - En Juego");
        setSize(800, 600);
        setLayout(new BorderLayout());

        panelMesa = new JPanel();
        panelMesa.setBackground(new Color(34, 139, 34));
        add(panelMesa, BorderLayout.CENTER);


        JPanel panelControles = new JPanel();
        btnFlip = new JButton("FLIP (Sacar)");
        btnPlantarse = new JButton("Plantarse");

        panelControles.add(btnFlip);
        panelControles.add(btnPlantarse);
        add(panelControles, BorderLayout.SOUTH);

        btnFlip.addActionListener(e -> {
            controller.enviarAccionJuego(TipoAccion.SACAR_CARTA);
        });

        btnPlantarse.addActionListener(e -> {
            controller.enviarAccionJuego(TipoAccion.PLANTARSE);
        });
    }


}

