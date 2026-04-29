package com.hackerrank.sample.service.compare;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numeric value with optional unit, parsed from strings like
 * {@code "4000 mAh"}, {@code "8 GB"}, {@code "6.2 in"}. Two values are
 * comparable iff their normalised units match (case-insensitive).
 */
public record NumericValue(BigDecimal magnitude, String unit) {

    private static final Pattern PATTERN =
            Pattern.compile("^\\s*([0-9]+(?:[\\.,][0-9]+)?)\\s*([A-Za-z\"%/]+)?\\s*$");

    public static NumericValue parse(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return new NumericValue(new BigDecimal(n.toString()), "");
        }
        String text = raw.toString();
        Matcher m = PATTERN.matcher(text);
        if (!m.matches()) {
            return null;
        }
        BigDecimal mag = new BigDecimal(m.group(1).replace(',', '.'));
        String unit = m.group(2);
        return new NumericValue(mag, unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT));
    }

    public boolean unitMatches(NumericValue other) {
        return other != null && unit.equalsIgnoreCase(other.unit);
    }
}
