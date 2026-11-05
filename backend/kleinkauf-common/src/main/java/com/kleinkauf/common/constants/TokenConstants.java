package com.kleinkauf.common.constants;

/*
JWT type, in the "type" claim. Users signs them all with the same secret, so without the type a password
recovery token would work to log in, and a session token to change the password without knowing the current one.
The gateway only lets session (access) tokens in; users only changes the password with a recovery token and
only renews the session with a refresh token (POST /auth/refresh).
Session tokens issued before this claim existed do not have it: the gateway keeps accepting them until they expire.
 */
public final class TokenConstants {
    public static final String TYPE_CLAIM = "type";
    public static final String ACCESS = "access";
    public static final String PASSWORD_RESET = "password";
    public static final String REFRESH = "refresh";

    private TokenConstants() {
    }
}
