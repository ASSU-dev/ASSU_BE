package com.assu.server.domain.notification.entity;

import java.util.Arrays;

public enum NotificationType {
    CHAT("chat"),
    PARTNER_SUGGESTION("partner_suggestion"),
    PARTNER_PROPOSAL("partner_proposal"),
    ORDER("order"),
    PARTNER_ALL("partner_all"), // 채팅, 주문 안내
    ADMIN_ALL("admin_all"), // 채팅, 제휴 건의, 제휴 제안
    STAMP("stamp");

    private final String code;
    NotificationType(String code) { this.code = code; }
    public String code() { return code; }

    public static NotificationType from(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported type: " + code));
    }
}