package com.flip7.client.ui;

import com.flip7.client.controller.GameController;
import javax.swing.*;
import java.awt.*;

public class LoginWindow extends JFrame {

    private JTextField txtUsuario;
    private JTextField txtIp;
    private JButton btnConectar;
    private GameController controller;

    public LoginWindow(GameController controller) {
        this.controller = controller;
        this.controller.setLoginWindow(this);

        setTitle("Flip 7 - Login");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridLayout(4, 1));
        setLocationRelativeTo(null);

        txtUsuario = new JTextField("Jugador1");
        txtIp = new JTextField("localhost");
        btnConectar = new JButton("Entrar al Juego");

        add(new JLabel("Nombre de Usuario:"));
        add(txtUsuario);
        add(new JLabel("IP del Servidor:"));
        add(txtIp);
        add(btnConectar);

        btnConectar.addActionListener(e -> {
            String user = txtUsuario.getText();
            String ip = txtIp.getText();
            controller.conectar(ip, 12345, user);
        });
    }
}
