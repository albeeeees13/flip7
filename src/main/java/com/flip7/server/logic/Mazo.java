package com.flip7.server.logic;

import com.flip7.common.model.Carta;
import com.flip7.common.enums.TipoAccion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Mazo {

    private List<Carta> cartas;

    public Mazo() {
        this.cartas = new ArrayList<>();
        inicializarMazo();
        barajar();
    }

    private void inicializarMazo() {

        for (int valor = 1; valor <= 12; valor++) {
            for (int i = 0; i < valor; i++) {

                cartas.add(new Carta(valor, TipoAccion.NINGUNA, valor + " de Bastos"));
            }
        }

        for (int i = 0; i < 5; i++) {
            cartas.add(new Carta(0, TipoAccion.SECOND_CHANCE, "Second Chance"));
        }
        for (int i = 0; i < 3; i++) {
            cartas.add(new Carta(0, TipoAccion.FREEZE, "Freeze"));
        }

    }

    public void barajar() {
        Collections.shuffle(this.cartas);
    }

    public Carta robarCarta() {
        if (cartas.isEmpty()) {
            return null;
        }
        return cartas.remove(0);
    }

    public int cartasRestantes() {
        return cartas.size();
    }
}