package com.assu.server.domain.chat.dto;

import lombok.Getter;
import lombok.Setter;

public class BlockRequestDTO {

    public record BlockMemberRequestDTO(
            Long opponentId
    ) {}
}
