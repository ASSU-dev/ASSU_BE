package com.assu.server.global.util;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpResolver {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private ClientIpResolver() {
    }

    // Traefik을 거치면 remoteAddr이 프록시 IP라 X-Forwarded-For의 첫 값을 우선한다
    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
