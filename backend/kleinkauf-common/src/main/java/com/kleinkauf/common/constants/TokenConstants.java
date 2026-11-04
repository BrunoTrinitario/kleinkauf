package com.kleinkauf.common.constants;

/*
Tipo de JWT, en el claim "type". Los firma users con el mismo secreto, asi que sin el tipo un token de
recuperacion de contrasena serviria para entrar y uno de sesion para cambiar la contrasena sin saber la actual.
El gateway rechaza los de recuperacion; users solo cambia la contrasena con uno de recuperacion.
Los tokens de sesion de antes de este claim no lo tienen: el gateway los sigue aceptando hasta que vencen.
 */
public final class TokenConstants {
    public static final String TYPE_CLAIM = "type";
    public static final String ACCESS = "access";
    public static final String PASSWORD_RESET = "password";

    private TokenConstants() {
    }
}
