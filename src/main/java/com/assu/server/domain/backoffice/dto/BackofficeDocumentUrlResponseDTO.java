package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "백오피스 서류 presigned URL 응답")
public record BackofficeDocumentUrlResponseDTO(
        @Schema(description = "S3 presigned URL (약 10분 유효)") String url
) {
    public static BackofficeDocumentUrlResponseDTO of(String url) {
        return new BackofficeDocumentUrlResponseDTO(url);
    }
}
