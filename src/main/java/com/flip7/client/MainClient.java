package com.flip7.client;

import com.flip7.client.controller.GameController;
import com.flip7.client.ui.LoginWindow;
import javax.swing.SwingUtilities;

public class MainClient {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameController controller = new GameController();
            LoginWindow login = new LoginWindow(controller);
            login.setVisible(true);
        });
    }
}