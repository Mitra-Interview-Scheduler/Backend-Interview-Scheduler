package com.nemal.util;

public final class LookupCodeUtils {
    private LookupCodeUtils() {}

    public static String toCode(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String code = value.trim()
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("(^_+|_+$)", "")
                .toUpperCase();
        return code.length() > 50 ? code.substring(0, 50) : code;
    }
}
