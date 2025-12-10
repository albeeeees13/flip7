package com.flip7.server.logic;

import com.flip7.common.Carta;
import com.flip7.common.TipoAccion;
import java.util.List;

public class MotorReglas {

    private static final int META_VICTORIA = 200;


    public int calcularPuntosMesa(List<Carta> cartasEnMesa) {
        return cartasEnMesa.stream()
                .mapToInt(Carta::getValor)
                .sum();
    }


    public boolean verificarBust(List<Carta> cartasEnMesa, Carta nuevaCarta) {

        if (nuevaCarta.getValor() == 0 && nuevaCarta.getAccion() != TipoAccion.NINGUNA) {
            return false;
        }


        boolean repetida = cartasEnMesa.stream()
                .anyMatch(c -> c.getValor() == nuevaCarta.getValor() && c.getValor() > 0);

        if (repetida) {

            boolean tieneSecondChance = cartasEnMesa.stream()
                    .anyMatch(c -> c.getAccion() == TipoAccion.SECOND_CHANCE);

            return !tieneSecondChance;
        }
        return false;
    }


    public boolean esGanador(int puntajeTotal) {
        return puntajeTotal >= META_VICTORIA;
    }
}