package com.flip7.client.ui;

import com.flip7.common.model.Carta;
import com.flip7.common.model.Carta.AccionEspecial;
import com.flip7.common.model.Carta.Tipo;

import javax.swing.*;
import java.awt.*;

public class CartaPanel extends JPanel {
    private Carta carta;

    public CartaPanel(Carta carta) {
        this.carta = carta;
        setPreferredSize(new Dimension(90, 130)); // Un poco más pequeñas para que quepan
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1. Determinar Color y Texto según el TIPO
        Color colorFondo = Color.GRAY;
        String textoCentro = "";
        String textoEsquina = "";

        if (carta.getTipo() == Tipo.NUMERO) {
            colorFondo = getColorNumero(carta.getValor());
            textoCentro = String.valueOf(carta.getValor());
            textoEsquina = textoCentro;
        }
        else if (carta.getTipo() == Tipo.MODIFICADOR) {
            colorFondo = new Color(255, 140, 0); // Naranja
            if (carta.getAccion() == AccionEspecial.MULTIPLICA_X2) {
                textoCentro = "x2";
            } else {
                textoCentro = "+" + carta.getValor();
            }
            textoEsquina = textoCentro;
        }
        else if (carta.getTipo() == Tipo.ACCION) {
            switch (carta.getAccion()) {
                case FREEZE:
                    colorFondo = new Color(135, 206, 250); // Azul Cielo
                    textoCentro = "FREEZE";
                    textoEsquina = "❄";
                    break;
                case FLIP_3:
                    colorFondo = new Color(255, 215, 0); // Amarillo
                    textoCentro = "FLIP 3";
                    textoEsquina = "⚡";
                    break;
                case SECOND_CHANCE:
                    colorFondo = new Color(220, 20, 60); // Rojo Pasión
                    textoCentro = "2nd CHANCE";
                    textoEsquina = "❤"; // Corazón
                    break;
            }
        }

        // 2. Dibujar la Carta
        g2.setColor(colorFondo);
        g2.fillRoundRect(2, 2, getWidth()-4, getHeight()-4, 15, 15);

        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(2, 2, getWidth()-4, getHeight()-4, 15, 15);

        // 3. Dibujar Texto
        g2.setColor(Color.WHITE);

        // Texto Central (Ajustar tamaño si es largo como "FREEZE")
        int tamanoFuente = textoCentro.length() > 2 ? 20 : 40;
        g2.setFont(new Font("Arial", Font.BOLD, tamanoFuente));
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(textoCentro)) / 2;
        int y = (getHeight() + fm.getAscent()) / 2 - 5;
        g2.drawString(textoCentro, x, y);

        // Texto Esquinas
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.drawString(textoEsquina, 8, 20);
        g2.drawString(textoEsquina, getWidth() - 25, getHeight() - 10);
    }

    private Color getColorNumero(int v) {
        if (v == 0) return Color.GRAY;
        if (v <= 2) return new Color(65, 105, 225); // Azul
        if (v <= 4) return new Color(34, 139, 34);  // Verde
        if (v <= 7) return new Color(178, 34, 34);  // Rojo (Riesgo)
        if (v <= 9) return new Color(128, 0, 128);  // Morado
        return new Color(218, 165, 32);             // Dorado (Valiosos)
    }
}