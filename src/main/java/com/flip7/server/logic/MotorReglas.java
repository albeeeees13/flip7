package com.flip7.server.logic;

import com.flip7.common.model.Carta;
import com.flip7.common.model.Carta.Tipo;
import com.flip7.common.model.Carta.AccionEspecial;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MotorReglas {

    private static final int META_VICTORIA = 200;
    private static final int BONUS_FLIP_7 = 15;

    public int calcularPuntosMesa(List<Carta> cartasEnMesa) {
        if (cartasEnMesa == null || cartasEnMesa.isEmpty()) {
            return 0;
        }


        int sumaNumeros = cartasEnMesa.stream()
                .filter(c -> c.getTipo() == Tipo.NUMERO)
                .mapToInt(Carta::getValor)
                .sum();


        boolean tieneMultiplicador = cartasEnMesa.stream()
                .anyMatch(c -> c.getAccion() == AccionEspecial.MULTIPLICA_X2);

        if (tieneMultiplicador) {
            sumaNumeros *= 2;
        }


        int sumaModificadores = cartasEnMesa.stream()
                .filter(c -> c.getTipo() == Tipo.MODIFICADOR && c.getAccion() != AccionEspecial.MULTIPLICA_X2)
                .mapToInt(Carta::getValor)
                .sum();


        int bonoFlip7 = calcularBonoFlip7(cartasEnMesa);

        return sumaNumeros + sumaModificadores + bonoFlip7;
    }

    public boolean verificarBust(List<Carta> cartasEnMesa, Carta nuevaCarta) {

        if (nuevaCarta.getTipo() != Tipo.NUMERO) {
            return false;
        }

        boolean numeroRepetido = cartasEnMesa.stream()
                .filter(c -> c.getTipo() == Tipo.NUMERO)
                .anyMatch(c -> c.getValor() == nuevaCarta.getValor());

        if (numeroRepetido) {

            boolean tieneSalvavidas = cartasEnMesa.stream()
                    .anyMatch(c -> c.getAccion() == AccionEspecial.SECOND_CHANCE);


            return !tieneSalvavidas;
        }

        return false;
    }

    public boolean esGanador(int puntajeTotal) {
        return puntajeTotal >= META_VICTORIA; // [cite: 10]
    }


    private int calcularBonoFlip7(List<Carta> cartasEnMesa) {
        Set<Integer> numerosDistintos = cartasEnMesa.stream()
                .filter(c -> c.getTipo() == Tipo.NUMERO)
                .map(Carta::getValor)
                .collect(Collectors.toSet());

        if (numerosDistintos.size() >= 7) {
            return BONUS_FLIP_7;
        }
        return 0;
    }
}