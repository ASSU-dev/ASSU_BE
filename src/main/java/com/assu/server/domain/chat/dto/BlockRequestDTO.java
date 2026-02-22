package com.assu.server.domain.chat.dto;

public class BlockRequestDTO {

    public record BlockMemberRequestDTO(
            Long opponentId
    ) {}
}
