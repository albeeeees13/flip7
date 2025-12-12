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
        // 1. NÚMEROS (0 al 12)
        // El 0 es especial: solo hay 1
        cartas.add(new Carta(0));

        // Del 1 al 12, la cantidad es igual al valor (ej. un 1, dos 2s, tres 3s...)
        for (int i = 1; i <= 12; i++) {
            for (int j = 0; j < i; j++) {
                cartas.add(new Carta(i)); // El constructor por defecto pone Tipo.NUMERO
            }
        }

        // 2. ACCIONES (¡IMPORTANTE! Usar el constructor de 3 parámetros)
        // 3 copias de cada poder
        agregarEspeciales(3, Tipo.ACCION, AccionEspecial.FLIP_3, 0);
        agregarEspeciales(3, Tipo.ACCION, AccionEspecial.FREEZE, 0);
        agregarEspeciales(3, Tipo.ACCION, AccionEspecial.SECOND_CHANCE, 0);

        // 3. MODIFICADORES (Puntos extra)
        agregarEspeciales(2, Tipo.MODIFICADOR, AccionEspecial.SUMA_2, 2);
        agregarEspeciales(2, Tipo.MODIFICADOR, AccionEspecial.SUMA_4, 4);
        agregarEspeciales(2, Tipo.MODIFICADOR, AccionEspecial.SUMA_6, 6);
        agregarEspeciales(1, Tipo.MODIFICADOR, AccionEspecial.SUMA_8, 8);
        agregarEspeciales(1, Tipo.MODIFICADOR, AccionEspecial.SUMA_10, 10);
        agregarEspeciales(1, Tipo.MODIFICADOR, AccionEspecial.MULTIPLICA_X2, 0);
    }

    private void agregarEspeciales(int cantidad, Tipo tipo, AccionEspecial accion, int valor) {
        for (int i = 0; i < cantidad; i++) {
            // AQUÍ ESTABA EL POSIBLE ERROR: Aseguramos que el TIPO sea el correcto
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