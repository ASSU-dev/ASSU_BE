package com.assu.server.domain.notification.dto;

import java.util.Map;


public record NotificationSettingsResponseDTO (
        Map<String, Boolean> settings
){}
