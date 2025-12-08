package com.flip7.common;

import com.flip7.common.enums.TipoMensaje;
import java.io.Serializable;

public class Mensaje implements Serializable {

    public enum Tipo {
        LOGIN,
        LOGIN_EXITO,
        ERROR,
        CREAR_SALA,
        UNIRSE_SALA,
        LISTA_SALAS,
        ROL_ASIGNADO,
        INICIO_JUEGO,
        ACCION_SACAR,
        ACCION_PLANTARSE,
        ACTUALIZAR_TABLERO,
        SOLICITAR_OBJETIVO,
        SELECCIONAR_OBJETIVO,
        MENSAJE_CHAT,
        FIN_JUEGO
    }

    private TipoMensaje tipo;
    private Object contenido;
    private String remitente;

    public Mensaje(TipoMensaje tipo, Object contenido, String remitente) {
        this.tipo = tipo;
        this.contenido = contenido;
        this.remitente = remitente;
    }

    public Mensaje(TipoMensaje tipo, Object contenido) {
        this(tipo, contenido, "SISTEMA");
    }

    public Tipo getTipo() { return tipo; }
    public Object getContenido() { return contenido; }
    public String getRemitente() { return remitente; }
}
