package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class BackofficeChatBlockDTO {

    @Schema(description = "두 멤버 간 차단 요청 DTO")
    public record BlockBetweenMembersRequestDTO(
            @Schema(description = "차단하는 멤버 ID", example = "1")
            @NotNull Long blockerId,
            @Schema(description = "차단당하는 멤버 ID", example = "2")
            @NotNull Long blockedId
    ) {}

    @Schema(description = "두 멤버 간 차단 응답 DTO")
    public record BlockBetweenMembersResponseDTO(
            Long blockerId,
            Long blockedId,
            LocalDateTime blockedAt
    ) {
        public static BlockBetweenMembersResponseDTO of(Long blockerId, Long blockedId) {
            return new BlockBetweenMembersResponseDTO(blockerId, blockedId, LocalDateTime.now());
        }
    }

    @Schema(description = "멤버 채팅 전체 차단 응답 DTO")
    public record MemberChatBlockResponseDTO(
            Long memberId,
            Boolean chatBlocked
    ) {
        public static MemberChatBlockResponseDTO of(Long memberId, Boolean chatBlocked) {
            return new MemberChatBlockResponseDTO(memberId, chatBlocked);
        }
    }
}