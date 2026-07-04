package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.backoffice.entity.PushTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BackofficePushSendRequestDTO(

        @NotNull
        @Schema(description = "발송 대상 유형 (ALL / STUDENT / UNION / PARTNER / INDIVIDUAL)", example = "ALL")
        PushTargetType targetType,

        @Schema(description = "수신자 멤버 ID (targetType=INDIVIDUAL일 때만 필수)", example = "42")
        Long receiverId,

        @NotBlank
        @Schema(description = "푸시 제목", example = "공지사항")
        String title,

        @NotBlank
        @Schema(description = "푸시 본문", example = "안녕하세요, 공지사항입니다.")
        String body,

        @Schema(description = "딥링크 URL (선택)", example = "/notice/1")
        String deepLink
) {}