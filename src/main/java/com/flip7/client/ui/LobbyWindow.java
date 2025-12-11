package com.flip7.client.ui;

import com.flip7.client.controller.GameController;
import javax.swing.*;
import java.awt.*;

public class LobbyWindow extends JFrame {

    private GameController controller;
    private JButton btnCrear, btnUnirse;

    private JList<String> listaSalas;
    private DefaultListModel<String> modeloSalas;


    public LobbyWindow(GameController controller) {
        this.controller = controller;
        this.controller.setCurrentView(this);

        setTitle("Flip 7 - Lobby");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());


        // En el constructor:
        modeloSalas = new DefaultListModel<>();
        listaSalas = new JList<>(modeloSalas);
        add(new JScrollPane(listaSalas), BorderLayout.CENTER);

        JPanel panelBotones = new JPanel();
        btnCrear = new JButton("Crear Sala");
        btnUnirse = new JButton("Unirse a Sala");

        panelBotones.add(btnCrear);
        panelBotones.add(btnUnirse);
        add(panelBotones, BorderLayout.SOUTH);

        btnCrear.addActionListener(e -> controller.crearSala());
        btnUnirse.addActionListener(e -> {
            if (listaSalas.getSelectedValue() != null) {
                controller.unirseSala(listaSalas.getSelectedValue());

            }
        });

    }

    public void actualizarListaSalas(String[] salas) {
        modeloSalas.clear();
        for (String s : salas) {
            modeloSalas.addElement(s);
        }
    }
}
