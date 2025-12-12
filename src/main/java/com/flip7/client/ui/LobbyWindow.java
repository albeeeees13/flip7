package com.flip7.client.ui;

import com.flip7.client.Controller.GameController;
import javax.swing.*;
import java.awt.*;

public class LobbyWindow extends JFrame {

    private GameController controller;


    private DefaultListModel<String> modeloSalas;
    private JList<String> listaSalas;
    private JButton btnCrear;
    private JButton btnUnirse;


    public LobbyWindow(GameController controller) {
        this.controller = controller;
        setTitle("Lobby - Flip 7");
        setSize(400, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Panel Central (Lista de Salas)
        modeloSalas = new DefaultListModel<>(); // Inicializamos el modelo
        listaSalas = new JList<>(modeloSalas);  // Conectamos la lista al modelo
        listaSalas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scroll = new JScrollPane(listaSalas);
        scroll.setBorder(BorderFactory.createTitledBorder("Salas Disponibles"));
        add(scroll, BorderLayout.CENTER);

        // 2. Panel Inferior (Botones)
        JPanel panelBotones = new JPanel();
        btnCrear = new JButton("Crear Sala");
        btnUnirse = new JButton("Unirse");

        panelBotones.add(btnCrear);
        panelBotones.add(btnUnirse);
        add(panelBotones, BorderLayout.SOUTH);

        // --- ACCIONES DE LOS BOTONES ---

        // Botón CREAR: Pregunta el límite de jugadores
        btnCrear.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(this, "¿Máximo de jugadores? (2-8)", "4");
            if (input != null && !input.isEmpty()) {
                try {
                    int limite = Integer.parseInt(input);
                    if (limite < 2 || limite > 8) {
                        JOptionPane.showMessageDialog(this, "El límite debe ser entre 2 y 8.");
                    } else {
                        // Llamamos al controller con el límite
                        controller.crearSala(limite);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Ingresa un número válido.");
                }
            }
        });

        // Botón UNIRSE: Toma la sala seleccionada
        btnUnirse.addActionListener(e -> {
            String salaSeleccionada = listaSalas.getSelectedValue();
            if (salaSeleccionada != null) {
                controller.unirseSala(salaSeleccionada);
            } else {
                JOptionPane.showMessageDialog(this, "Selecciona una sala de la lista.");
            }
        });


    }



    public void actualizarListaSalas(String[] salas) {

        SwingUtilities.invokeLater(() -> {
            modeloSalas.clear();
            if (salas != null) {
                for (String s : salas) {
                    modeloSalas.addElement(s);
                }
            }
        });
    }
}