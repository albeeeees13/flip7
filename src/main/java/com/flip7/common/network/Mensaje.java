package com.flip7.common.network;

import com.flip7.common.enums.TipoMensaje;
import java.io.Serializable;
public class Mensaje implements Serializable {

    private static final long serialVersionUID = 1L;

    private TipoMensaje tipo;
    private Object contenido;

    public Mensaje(TipoMensaje tipo, Object contenido) {
        this.tipo = tipo;
        this.contenido = contenido;
    }
    public Mensaje(TipoMensaje tipo) {
        this.tipo = tipo;
        this.contenido = null;
    }
    public TipoMensaje getTipo() {
        return tipo;
    }
    public Object getContenido() {
        return contenido;
    }
    @Override
    public String toString() {
        return "Mensaje{" +
                "tipo=" + tipo +
                ", contenido=" + contenido +
                '}';
    }
}