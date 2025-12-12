package com.flip7.client.ui;

import javax.swing.*;
import java.awt.*;

public class DialogoObjetivo extends JDialog {
    private String seleccionado = null;

    public DialogoObjetivo(JFrame parent, String[] jugadores, String accion) {
        super(parent, "Seleccionar Objetivo: " + accion, true); // true = Modal (bloquea el juego hasta elegir)
        setLayout(new BorderLayout());
        setSize(300, 200);
        setLocationRelativeTo(parent);

        JLabel lblInstruccion = new JLabel("¿A quién aplicas " + accion + "?", SwingConstants.CENTER);
        lblInstruccion.setFont(new Font("Arial", Font.BOLD, 14));
        add(lblInstruccion, BorderLayout.NORTH);

        JPanel panelBotones = new JPanel(new GridLayout(0, 1, 5, 5)); // Lista vertical


        JButton btnYo = new JButton("A MÍ MISMO");
        btnYo.setBackground(new Color(173, 216, 230));
        btnYo.addActionListener(e -> {
            seleccionado = "SELF"; // Código especial para uno mismo
            dispose();
        });
        panelBotones.add(btnYo);


        for (String j : jugadores) {
            JButton btn = new JButton(j);
            btn.addActionListener(e -> {
                seleccionado = j;
                dispose();
            });
            panelBotones.add(btn);
        }

        add(new JScrollPane(panelBotones), BorderLayout.CENTER);
    }

    public String getSeleccionado() {
        return seleccionado;
    }
}