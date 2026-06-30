package com.assu.server.domain.backoffice.dto;

import java.time.LocalDateTime;

public record BackofficeAdminCreateResponseDTO(
    Long adminId,
    String email,
    String name,
    LocalDateTime createdAt
) {
}
