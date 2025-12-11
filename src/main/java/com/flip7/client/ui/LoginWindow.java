package com.flip7.client.ui;

import com.flip7.client.controller.GameController;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LoginWindow extends JFrame {

    private JTextField txtUsuario;
    private JPasswordField txtPassword;
    private JTextField txtIp;
    private JTextField txtPuerto;
    private JButton btnLogin;
    private JButton btnRegistro;
    private GameController controller;

    // Colores del diseño
    private final Color COLOR_AZUL = new Color(66, 133, 244); // Azul tipo Google
    private final Color COLOR_VERDE = new Color(46, 204, 113); // Verde tipo Emerald
    private final Color COLOR_FONDO = Color.WHITE;
    private final Font FUENTE_TITULO = new Font("SansSerif", Font.BOLD, 36);
    private final Font FUENTE_SUBTITULO = new Font("SansSerif", Font.PLAIN, 12);
    private final Font FUENTE_LABEL = new Font("SansSerif", Font.BOLD, 12);

    public LoginWindow(GameController controller) {
        this.controller = controller;
        this.controller.setLoginWindow(this);

        setTitle("Flip 7 - Login");
        setSize(400, 550); // Un poco más alto para que quepa todo
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        // PANEL PRINCIPAL (Fondo Blanco)
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(COLOR_FONDO);
        mainPanel.setBorder(new EmptyBorder(30, 40, 30, 40)); // Margen interno

        // --- 1. ENCABEZADO (TITULO) ---
        JLabel lblTitulo = new JLabel("FLIP 7");
        lblTitulo.setFont(FUENTE_TITULO);
        lblTitulo.setForeground(COLOR_AZUL);
        lblTitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSubtitulo = new JLabel("Inicia sesion o registrate para jugar");
        lblSubtitulo.setFont(FUENTE_SUBTITULO);
        lblSubtitulo.setForeground(Color.GRAY);
        lblSubtitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // --- 2. CAMPOS DE TEXTO (Helper para no repetir código) ---
        txtUsuario = new JTextField();
        txtPassword = new JPasswordField();
        txtIp = new JTextField("localhost"); // IP por defecto
        txtPuerto = new JTextField("12345"); // Puerto por defecto

        // --- 3. BOTONES ---
        JPanel panelBotones = new JPanel(new GridLayout(1, 2, 10, 0)); // 1 fila, 2 col, 10px separación
        panelBotones.setBackground(COLOR_FONDO);
        panelBotones.setMaximumSize(new Dimension(400, 40)); // Altura fija para botones

        btnLogin = crearBoton("ENTRAR", COLOR_AZUL);
        btnRegistro = crearBoton("REGISTRAR", COLOR_VERDE);

        panelBotones.add(btnLogin);
        panelBotones.add(btnRegistro);

        // --- AGREGAR TODO AL PANEL EN ORDEN ---
        mainPanel.add(lblTitulo);
        mainPanel.add(Box.createVerticalStrut(5)); // Espacio
        mainPanel.add(lblSubtitulo);
        mainPanel.add(Box.createVerticalStrut(30)); // Espacio grande

        agregarCampo(mainPanel, "Usuario:", txtUsuario);
        agregarCampo(mainPanel, "Password:", txtPassword);
        agregarCampo(mainPanel, "Servidor:", txtIp);
        agregarCampo(mainPanel, "Puerto:", txtPuerto);

        mainPanel.add(Box.createVerticalStrut(20)); // Espacio antes de botones
        mainPanel.add(panelBotones);

        add(mainPanel);

        // --- ACCIONES (Igual que antes) ---
        btnLogin.addActionListener(e -> enviarDatos("LOGIN"));
        btnRegistro.addActionListener(e -> enviarDatos("REGISTER"));
    }

    // Método auxiliar para crear los grupitos de Label + Input verticalmente
    private void agregarCampo(JPanel panel, String textoLabel, JComponent campo) {
        JLabel label = new JLabel(textoLabel);
        label.setFont(FUENTE_LABEL);
        label.setForeground(Color.DARK_GRAY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Ajustes visuales del campo
        campo.setPreferredSize(new Dimension(300, 35));
        campo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        campo.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Borde sutil para el input
        if (campo instanceof JTextField) {
            ((JTextField) campo).setMargin(new Insets(2, 5, 2, 5));
        }

        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBackground(COLOR_FONDO);
        row.setAlignmentX(Component.CENTER_ALIGNMENT);

        row.add(label);
        row.add(Box.createVerticalStrut(5)); // Espacio entre label y caja
        row.add(campo);
        row.add(Box.createVerticalStrut(15)); // Espacio entre campos

        panel.add(row);
    }

    // Método para estilizar botones rápido
    private JButton crearBoton(String texto, Color fondo) {
        JButton btn = new JButton(texto);
        btn.setBackground(fondo);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false); // Quita el borde 3D feo
        btn.setOpaque(true);
        return btn;
    }

    private void enviarDatos(String accion) {
        String user = txtUsuario.getText().trim();
        String pass = new String(txtPassword.getPassword()).trim();
        String ip = txtIp.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Llena todos los campos.");
            return;
        }

        try {
            int puerto = Integer.parseInt(txtPuerto.getText().trim());
            controller.conectar(ip, puerto, user, pass, accion);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "El puerto debe ser número.");
        }
    }
}