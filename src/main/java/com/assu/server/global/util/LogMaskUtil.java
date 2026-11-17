package com.assu.server.global.util;

public final class LogMaskUtil {

    private static final int VISIBLE_PREFIX_LENGTH = 4;
    private static final String MASK = "[MASKED]";

    private LogMaskUtil() {
    }

    public static String mask(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= VISIBLE_PREFIX_LENGTH) {
            return MASK;
        }
        return value.substring(0, VISIBLE_PREFIX_LENGTH) + "..." + MASK;
    }
}
