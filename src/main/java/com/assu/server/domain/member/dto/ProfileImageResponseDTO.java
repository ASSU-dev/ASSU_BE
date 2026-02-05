package com.assu.server.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileImageResponseDTO (
    @Schema(description = "업로드된 프로필 이미지 URL")
    String url
){}
