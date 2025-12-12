package com.flip7.client.ui;

import com.flip7.client.Controller.GameController;
import com.flip7.common.enums.TipoMensaje;
import com.flip7.common.model.Carta;
import com.flip7.common.network.Mensaje;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

public class GameWindow extends JFrame {

    private GameController controller;
    private JPanel panelOponentes;
    private JPanel panelMiMesa;
    private JTextArea areaChat;
    private JTextField inputChat;
    private JButton btnEnviar, btnFlip, btnPlantarse;

    public GameWindow(GameController controller) {
        this.controller = controller;
        this.controller.setCurrentView(this);

        setTitle("Flip 7 - Mesa de Juego");
        setSize(1280, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. ZONA OPONENTES (DIVIDIDA)
        panelOponentes = new JPanel();
        panelOponentes.setBackground(new Color(40, 40, 40));
        // Usamos GridLayout dinámico (1 fila, X columnas) o FlowLayout centrado
        panelOponentes.setLayout(new GridLayout(1, 0, 10, 0));
        panelOponentes.setPreferredSize(new Dimension(0, 300));

        JScrollPane scrollOps = new JScrollPane(panelOponentes);
        scrollOps.setBorder(BorderFactory.createTitledBorder(null, "OPONENTES", TitledBorder.CENTER, TitledBorder.TOP, new Font("Arial", Font.BOLD, 14), Color.WHITE));
        add(scrollOps, BorderLayout.NORTH);

        // 2. MI MESA
        panelMiMesa = new JPanel();
        panelMiMesa.setBackground(new Color(34, 139, 34)); // Verde mesa
        panelMiMesa.setLayout(new FlowLayout(FlowLayout.CENTER, 15, 25));

        JScrollPane scrollMio = new JScrollPane(panelMiMesa);
        scrollMio.setBorder(BorderFactory.createTitledBorder(null, "TU ZONA", TitledBorder.CENTER, TitledBorder.TOP, new Font("Arial", Font.BOLD, 16), Color.WHITE));
        add(scrollMio, BorderLayout.CENTER);

        // 3. CHAT (Igual que antes)
        JPanel panelLateral = new JPanel(new BorderLayout());
        panelLateral.setPreferredSize(new Dimension(300, 0));
        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setFont(new Font("Consolas", Font.PLAIN, 12));
        areaChat.setLineWrap(true);
        panelLateral.add(new JScrollPane(areaChat), BorderLayout.CENTER);

        JPanel panelInput = new JPanel(new BorderLayout());
        inputChat = new JTextField();
        btnEnviar = new JButton(">");
        panelInput.add(inputChat, BorderLayout.CENTER);
        panelInput.add(btnEnviar, BorderLayout.EAST);
        panelLateral.add(panelInput, BorderLayout.SOUTH);
        add(panelLateral, BorderLayout.EAST);

        // 4. CONTROLES
        JPanel panelControles = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 15));
        panelControles.setBackground(new Color(20, 20, 20));

        btnFlip = new JButton("FLIP (Sacar)");
        btnFlip.setPreferredSize(new Dimension(180, 50));
        btnFlip.setBackground(new Color(50, 120, 200));
        btnFlip.setForeground(Color.WHITE);
        btnFlip.setFont(new Font("Arial", Font.BOLD, 14));

        btnPlantarse = new JButton("PLANTARSE");
        btnPlantarse.setPreferredSize(new Dimension(180, 50));
        btnPlantarse.setBackground(new Color(200, 50, 50));
        btnPlantarse.setForeground(Color.WHITE);
        btnPlantarse.setFont(new Font("Arial", Font.BOLD, 14));

        panelControles.add(btnFlip);
        panelControles.add(btnPlantarse);
        add(panelControles, BorderLayout.SOUTH);

        // Eventos
        btnFlip.addActionListener(e -> controller.enviarAccionJuego(TipoMensaje.ACCION_SACAR));
        btnPlantarse.addActionListener(e -> controller.enviarAccionJuego(TipoMensaje.ACCION_PLANTARSE));

        ActionListener chatAction = e -> {
            String txt = inputChat.getText().trim();
            if(!txt.isEmpty()) { controller.enviarMensajeChat(txt); inputChat.setText(""); }
        };
        inputChat.addActionListener(chatAction);
        btnEnviar.addActionListener(chatAction);
    }

    public void actualizarMesa(List<Carta> cartas) {
        SwingUtilities.invokeLater(() -> {
            panelMiMesa.removeAll();
            for(Carta c : cartas) panelMiMesa.add(new CartaPanel(c));
            panelMiMesa.revalidate(); panelMiMesa.repaint();
        });
    }

    public void actualizarOponentes(Map<String, List<Carta>> oponentes) {
        SwingUtilities.invokeLater(() -> {
            panelOponentes.removeAll();

            // Creamos un panel grande por cada oponente
            for (Map.Entry<String, List<Carta>> entry : oponentes.entrySet()) {
                String nombre = entry.getKey();
                // Opcional: No mostrarse a uno mismo en la zona de arriba
                // if (nombre.equals(MI_NOMBRE)) continue;

                JPanel panelJugador = new JPanel();
                panelJugador.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
                panelJugador.setBackground(new Color(60, 60, 60));
                panelJugador.setBorder(BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(Color.LIGHT_GRAY), nombre,
                        TitledBorder.LEFT, TitledBorder.TOP, new Font("Arial", Font.BOLD, 12), Color.WHITE));

                for(Carta c : entry.getValue()) {
                    CartaPanel p = new CartaPanel(c);
                    p.setPreferredSize(new Dimension(50, 75)); // Cartas más pequeñas para oponentes
                    panelJugador.add(p);
                }

                panelOponentes.add(panelJugador);
            }
            panelOponentes.revalidate(); panelOponentes.repaint();
        });
    }

    public void agregarMensajeChat(String msg) {
        SwingUtilities.invokeLater(() -> {
            areaChat.append(msg + "\n");
            areaChat.setCaretPosition(areaChat.getDocument().getLength());
        });
    }

    public void mostrarEfectoBust() {
        // Efecto visual opcional
    }
}