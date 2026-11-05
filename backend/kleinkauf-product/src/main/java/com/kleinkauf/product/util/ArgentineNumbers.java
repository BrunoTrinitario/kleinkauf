package com.kleinkauf.product.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/*
Numbers in Argentine format for the CSV: '.' separates thousands and ',' is the decimal mark.
15.000 = fifteen thousand, 15.000,50 = fifteen thousand fifty cents, 15,5 = fifteen and a half.
If there are dots they must separate groups of 3 digits: '15.5' is an error and is not read as 155.
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

    // Without a thousands separator so it can be imported again without ambiguity: 15000,5
    public static String format(BigDecimal value) {
        return value == null ? "" : value.toPlainString().replace('.', ',');
    }

    public static String format(double value) {
        return format(BigDecimal.valueOf(value).stripTrailingZeros());
    }
}
