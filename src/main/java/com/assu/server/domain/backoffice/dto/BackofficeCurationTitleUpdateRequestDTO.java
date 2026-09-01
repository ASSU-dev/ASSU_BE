package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record BackofficeCurationTitleUpdateRequestDTO(
    @NotBlank
    @Schema(description = "수정할 큐레이션 섹션 제목 ({name} 플레이스홀더 사용 가능)", example = "{name}님을 위한 제휴")
    String title
) {}
