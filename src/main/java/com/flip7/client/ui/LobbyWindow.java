package com.flip7.client.ui;

import com.flip7.client.controller.GameController;
import javax.swing.*;
import java.awt.*;

public class LobbyWindow extends JFrame {
    private GameController controller;

    public LobbyWindow(GameController controller) {
        this.controller = controller;
        this.controller.setCurrentView(this);

        setTitle("Flip 7 - Lobby");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
    }
}
