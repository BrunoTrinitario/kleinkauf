package com.decor.product.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/*
Numeros en formato argentino para el CSV: '.' separa miles y ',' es el decimal.
15.000 = quince mil, 15.000,50 = quince mil con cincuenta, 15,5 = quince y medio.
Si hay puntos tienen que separar grupos de 3 digitos: '15.5' es un error y no se interpreta como 155.
 */
public class ArgentineNumbers {

    private static final Pattern FORMAT = Pattern.compile("^-?(\\d{1,3}(\\.\\d{3})+|\\d+)(,\\d+)?$");

    public static BigDecimal parse(String value) {
        String trimmed = value.trim();
        if (!FORMAT.matcher(trimmed).matches()) {
            throw new NumberFormatException(value);
        }
        return new BigDecimal(trimmed.replace(".", "").replace(',', '.'));
    }

    // Sin separador de miles para que se pueda volver a importar sin ambiguedad: 15000,5
    public static String format(BigDecimal value) {
        return value == null ? "" : value.toPlainString().replace('.', ',');
    }

    public static String format(double value) {
        return format(BigDecimal.valueOf(value).stripTrailingZeros());
    }
}
