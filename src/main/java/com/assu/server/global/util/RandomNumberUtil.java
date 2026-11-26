package com.assu.server.global.util;

import java.security.SecureRandom;

public class RandomNumberUtil {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String generateSixDigit() {
        int number = 100000 + SECURE_RANDOM.nextInt(900000); // 100000~999999
        return String.valueOf(number);
    }
}
