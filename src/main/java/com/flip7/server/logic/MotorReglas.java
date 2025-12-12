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
        if (cartasEnMesa == null || cartasEnMesa.isEmpty()) return 0;


        int suma = cartasEnMesa.stream()
                .filter(c -> c.getTipo() == Tipo.NUMERO)
                .mapToInt(Carta::getValor)
                .sum();


        boolean tieneX2 = cartasEnMesa.stream()
                .anyMatch(c -> c.getAccion() == AccionEspecial.MULTIPLICA_X2);
        if (tieneX2) suma *= 2;


        int mods = cartasEnMesa.stream()
                .filter(c -> c.getTipo() == Tipo.MODIFICADOR && c.getAccion() != AccionEspecial.MULTIPLICA_X2)
                .mapToInt(Carta::getValor)
                .sum();


        int bono = calcularBonoFlip7(cartasEnMesa);

        return suma + mods + bono;
    }

    public boolean verificarBust(List<Carta> cartasEnMesa, Carta nuevaCarta) {
        if (nuevaCarta.getTipo() != Tipo.NUMERO) return false;

        boolean repetido = cartasEnMesa.stream()
                .filter(c -> c.getTipo() == Tipo.NUMERO)
                .anyMatch(c -> c.getValor() == nuevaCarta.getValor());

        if (repetido) {
            boolean salvado = cartasEnMesa.stream()
                    .anyMatch(c -> c.getAccion() == AccionEspecial.SECOND_CHANCE);
            return !salvado;
        }
        return false;
    }

    private int calcularBonoFlip7(List<Carta> cartasEnMesa) {
        Set<Integer> unicos = cartasEnMesa.stream()
                .filter(c -> c.getTipo() == Tipo.NUMERO)
                .map(Carta::getValor)
                .collect(Collectors.toSet());
        return (unicos.size() >= 7) ? BONUS_FLIP_7 : 0;
    }

    public boolean esGanador(int puntos) { return puntos >= META_VICTORIA; }
}