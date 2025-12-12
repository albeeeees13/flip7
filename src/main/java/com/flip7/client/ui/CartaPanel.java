package com.flip7.client.ui;

import com.flip7.common.model.Carta;
import javax.swing.*;
import java.awt.*;

public class CartaPanel extends JPanel {
    private Carta carta;

    public CartaPanel(Carta carta) {
        this.carta = carta;
        // Tamaño fijo de carta estilo naipe
        setPreferredSize(new Dimension(100, 150));
        setOpaque(false); // Para que se vean los bordes redondeados
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // Suavizar bordes (Antialiasing)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Elegir color según el valor o tipo
        Color colorFondo = getColorPorValor(carta.getValor());

        // 2. Dibujar el cuerpo de la carta
        g2.setColor(colorFondo);
        g2.fillRoundRect(5, 5, 90, 140, 15, 15);

        // 3. Borde
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(5, 5, 90, 140, 15, 15);

        // 4. Dibujar Texto (Número o Acción)
        g2.setColor(Color.WHITE);

        String texto = String.valueOf(carta.getValor());
        // Lógica visual básica para cartas especiales (si valor es 0 o negativo, es especial)
        if (texto.equals("0")) texto = "0";
        // Aquí podrías mapear valores negativos a textos como "FRZ" o "F3" si tu backend lo manda así

        // Fuente grande para el centro
        g2.setFont(new Font("Arial", Font.BOLD, 40));
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(texto)) / 2;
        int y = (getHeight() + fm.getAscent()) / 2 - 5;
        g2.drawString(texto, x, y);

        // Fuente pequeña para las esquinas
        g2.setFont(new Font("Arial", Font.BOLD, 15));
        g2.drawString(texto, 12, 25); 
        g2.drawString(texto, 75, 135);
    }

    private Color getColorPorValor(int valor) {
        // Colores estilo Flip 7 (Aproximados)
        if (valor == 0) return new Color(100, 100, 100); // Gris
        if (valor == 1 || valor == 2) return new Color(65, 105, 225);
        if (valor == 3 || valor == 4) return new Color(34, 139, 34);
        if (valor == 5 || valor == 6) return new Color(255, 140, 0);
        if (valor == 7) return new Color(220, 20, 60);
        if (valor >= 8 && valor <= 10) return new Color(128, 0, 128);
        if (valor > 10) return new Color(255, 215, 0);

        // Acciones especiales (Asumiendo que usaremos valores especiales luego)
        return Color.DARK_GRAY;
    }
}