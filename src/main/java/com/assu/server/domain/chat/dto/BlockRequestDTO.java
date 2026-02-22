package com.assu.server.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetbrains.annotations.NotNull;

public class BlockRequestDTO {

    @Schema(description = "채팅 차단 DTO")
    public record BlockMemberRequestDTO(
            @Schema(description = "차단할 사용자 ID", example = "12")
            @NotNull
            Long opponentId
    ) {}
}
