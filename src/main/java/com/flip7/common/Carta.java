package com.flip7.common;

import java.io.Serializable;

// Serializable es clave para mandarlo por sockets
public class Carta implements Serializable {

    private int valor;           // Puntos (0 a 12)
    private TipoAccion accion;   // Poder especial (FLIP_3, FREEZE...)
    private String texto;        // Lo que mostramos en la UI

    public Carta() {}

    public Carta(int valor, TipoAccion accion, String texto) {
        this.valor = valor;
        this.accion = accion;
        this.texto = texto;
    }

    public int getValor() { return valor; }
    public TipoAccion getAccion() { return accion; }
    public String getTexto() { return texto; }

    @Override
    public String toString() {
        return texto + " (" + valor + ")";
    }
}
