package com.flip7.client.ui;

import com.flip7.client.Controller.GameController; // Asegúrate que la carpeta sea 'controller' minúscula
import javax.swing.*;
import java.awt.*;

public class LobbyWindow extends JFrame {

    // --- 1. DECLARACIÓN DE VARIABLES (Todo va aquí arriba) ---
    private GameController controller;
    private JButton btnCrear, btnUnirse;
    private JList<String> listaSalas;
    private DefaultListModel<String> modeloSalas;

    // --- 2. EL CONSTRUCTOR (Solo uno) ---
    public LobbyWindow(GameController controller) {
        this.controller = controller;
        this.controller.setCurrentView(this);

        setTitle("Flip 7 - Lobby");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Configurar la lista
        modeloSalas = new DefaultListModel<>();
        listaSalas = new JList<>(modeloSalas);
        add(new JScrollPane(listaSalas), BorderLayout.CENTER);

        // Configurar botones
        JPanel panelBotones = new JPanel();
        btnCrear = new JButton("Crear Sala");
        btnUnirse = new JButton("Unirse a Sala");

        panelBotones.add(btnCrear);
        panelBotones.add(btnUnirse);
        add(panelBotones, BorderLayout.SOUTH);

        // Configurar acciones (Listeners)
        btnCrear.addActionListener(e -> controller.crearSala());

        btnUnirse.addActionListener(e -> {
            if (listaSalas.getSelectedValue() != null) {
                controller.unirseSala(listaSalas.getSelectedValue());
            } else {
                JOptionPane.showMessageDialog(this, "Por favor selecciona una sala de la lista.");
            }
        });
    }

    // --- 3. MÉTODOS EXTRA (Fuera del constructor) ---
    public void actualizarListaSalas(String[] salas) {
        modeloSalas.clear();
        for (String s : salas) {
            modeloSalas.addElement(s);
        }
    }
}