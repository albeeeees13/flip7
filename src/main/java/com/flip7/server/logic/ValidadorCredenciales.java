package com.flip7.server.logic;

import java.util.regex.Pattern;

public class ValidadorCredenciales {

    private static final Pattern SOLO_LETRAS = Pattern.compile("^[a-zA-Z]+$");

    public static String validarUsuario(String usuario) {
        if (usuario == null) return "El usuario no puede estar vacío.";

        if (usuario.contains(" ")) return "El usuario no puede tener espacios.";

        if (usuario.length() < 3 || usuario.length() > 15) {
            return "El usuario debe medir entre 3 y 15 letras.";
        }
        if (!SOLO_LETRAS.matcher(usuario).matches()) {
            return "ERROR: El usuario solo puede contener letras (A-Z). No se permiten números ni símbolos.";
        }
        return "OK";
    }
}