package com.flip7.server.logic;

import com.flip7.common.model.Carta;
import com.flip7.common.model.Carta.Tipo;
import com.flip7.common.model.Carta.AccionEspecial;
import java.util.*;

public class Mazo {
    private List<Carta> cartas;

    public Mazo() {
        cartas = new ArrayList<>();
        generarMazo();
        barajar();
    }

    private void generarMazo() {

        for (int i = 1; i <= 12; i++) {
            for (int j = 0; j < i; j++) {
                cartas.add(new Carta(i));
            }
        }
        // Hay un 0 [cite: 19, 57]
        cartas.add(new Carta(0));


        agregarEspeciales(3, Tipo.ACCION, AccionEspecial.FLIP_3, 0);
        agregarEspeciales(3, Tipo.ACCION, AccionEspecial.FREEZE, 0);
        agregarEspeciales(3, Tipo.ACCION, AccionEspecial.SECOND_CHANCE, 0);



        agregarEspeciales(2, Tipo.MODIFICADOR, AccionEspecial.SUMA_2, 2);
        agregarEspeciales(2, Tipo.MODIFICADOR, AccionEspecial.SUMA_4, 4);
        agregarEspeciales(2, Tipo.MODIFICADOR, AccionEspecial.SUMA_6, 6);
        agregarEspeciales(1, Tipo.MODIFICADOR, AccionEspecial.SUMA_8, 8);
        agregarEspeciales(1, Tipo.MODIFICADOR, AccionEspecial.SUMA_10, 10);
        agregarEspeciales(1, Tipo.MODIFICADOR, AccionEspecial.MULTIPLICA_X2, 0); // [cite: 66]
    }

    private void agregarEspeciales(int cantidad, Tipo tipo, AccionEspecial accion, int valor) {
        for (int i = 0; i < cantidad; i++) {
            cartas.add(new Carta(tipo, accion, valor));
        }
    }

    public void barajar() {
        Collections.shuffle(cartas);
    }

    public Carta robarCarta() {
        if (cartas.isEmpty()) return null;
        return cartas.remove(0);
    }
}