package com.flip7.common.model;

import java.io.Serializable;

public class Carta implements Serializable {

    public enum Tipo { NUMERO, ACCION, MODIFICADOR }

    public enum AccionEspecial {
        NINGUNA,
        FREEZE,
        FLIP_3,
        SECOND_CHANCE,
        SUMA_2, SUMA_4, SUMA_6, SUMA_8, SUMA_10,
        MULTIPLICA_X2
    }

    private int valor;
    private Tipo tipo;
    private AccionEspecial accion;

    public Carta(int valor) {
        this.valor = valor;
        this.tipo = Tipo.NUMERO;
        this.accion = AccionEspecial.NINGUNA;
    }

    public Carta(Tipo tipo, AccionEspecial accion, int valor) {
        this.tipo = tipo;
        this.accion = accion;
        this.valor = valor;
    }

    public int getValor() { return valor; }
    public Tipo getTipo() { return tipo; }
    public AccionEspecial getAccion() { return accion; }

    @Override
    public String toString() {
        return (tipo == Tipo.NUMERO) ? String.valueOf(valor) : accion.toString();
    }
}