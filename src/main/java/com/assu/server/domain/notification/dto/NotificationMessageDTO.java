package com.assu.server.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public record NotificationMessageDTO (
        @Schema(description = "중복 요청 방지를 위한 멱등성 키", example = "notif-20260120-0001")
        String idempotencyKey,

        @Schema(description = "알림 수신자 회원 ID", example = "42")
        Long receiverId,

        @Schema(description = "알림 제목", example = "새로운 문의가 등록되었습니다")
        String title,

        @Schema(description = "알림 본문 내용", example = "문의하신 내용에 대한 답변이 등록되었습니다.")
        String body,

        @Schema(description = "알림과 함께 전달되는 추가 데이터(key-value)", example = "{\"inquiryId\":\"12\",\"type\":\"ANSWER\"}")
        Map<String, String> data
){}
