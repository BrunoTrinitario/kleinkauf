package com.kleinkauf.users.constants;

// Email texts. They reach the store's customers, so they are in Spanish
public class MailConstants {
    public static String FORGOT_SUBJECT = "Recuperá tu contraseña";

    public static String FORGOT_MAIL_CONTENT(String link) {
        return "Hola,\n\n"
                + "Recibimos un pedido para cambiar la contraseña de tu cuenta. Para elegir una nueva, entrá a este link:\n\n"
                + link + "\n\n"
                + "El link sirve una sola vez. Si no lo pediste, ignorá este mail: tu contraseña no cambia.\n";
    }
}
