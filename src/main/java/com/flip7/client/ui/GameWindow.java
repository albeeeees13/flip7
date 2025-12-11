package com.flip7.client.ui;

import com.flip7.client.controller.GameController;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import com.flip7.common.model.Carta;

public class GameWindow extends JFrame {
    private GameController controller;
    private JPanel panelMesa;

    public GameWindow(GameController controller) {
        this.controller = controller;
        this.controller.setCurrentView(this);

        setTitle("Flip 7 - En Juego");
        setSize(800, 600);
        setLayout(new BorderLayout());

        panelMesa = new JPanel();
        panelMesa.setBackground(new Color(34, 139, 34));
        add(panelMesa, BorderLayout.CENTER);
    }
}
