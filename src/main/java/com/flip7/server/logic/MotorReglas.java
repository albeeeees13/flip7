package com.flip7.server.logic;

import com.flip7.common.model.Carta;
import com.flip7.common.enums.TipoAccion;
import java.util.List;

public class MotorReglas {

    private static final int META_VICTORIA = 200;
    public int calcularPuntosMesa(List<Carta> cartasEnMesa) {
        if (cartasEnMesa == null || cartasEnMesa.isEmpty()) {
            return 0;
        }
        return cartasEnMesa.stream()
                .mapToInt(Carta::getValor)
                .sum();
    }
    public boolean verificarBust(List<Carta> cartasEnMesa, Carta nuevaCarta) {
        if (nuevaCarta.getValor() <= 0) {
            return false;
        }

        boolean numeroRepetido = cartasEnMesa.stream()
                .anyMatch(c -> c.getValor() == nuevaCarta.getValor());

        if (numeroRepetido) {
            boolean tieneSalvavidas = cartasEnMesa.stream()
                    .anyMatch(c -> c.getAccion() == TipoAccion.SECOND_CHANCE);
            return !tieneSalvavidas;
        }

        return false;
    }
    public boolean esGanador(int puntajeTotal) {
        return puntajeTotal >= META_VICTORIA;
    }

    public int calcularBonos(List<Carta> cartasEnMesa) {
        return 0;
    }
}