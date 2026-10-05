package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record BackofficeDevSyncResponseDTO(

        @Schema(description = "복사 원본 스키마", example = "assu_db")
        String sourceSchema,

        @Schema(description = "복사 대상 스키마", example = "assu_dev_db")
        String targetSchema,

        @Schema(description = "복사한 테이블 목록", example = "[\"member\", \"student\"]")
        List<String> copiedTables
) {
}
