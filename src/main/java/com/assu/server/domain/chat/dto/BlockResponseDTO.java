package com.assu.server.domain.chat.dto;

import com.assu.server.domain.chat.entity.Block;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class BlockResponseDTO {

    public record BlockMemberDTO (
            Long memberId,
            String name,
            LocalDateTime blockDate
    ) {
        public static BlockMemberDTO of(
                Long blockedId,
                String blockedName
        ) {
            return new BlockMemberDTO(
                    blockedId,
                    blockedName,
                    LocalDateTime.now()
            );
        }

        public static BlockMemberDTO from(
                Block block
        ) {
            Member blockedMember = block.getBlocked();
            UserRole blockedRole = blockedMember.getRole();
            String blockedName;
            if (blockedRole == UserRole.ADMIN) {
                blockedName = blockedMember.getAdminProfile().getName();
            } else {
                blockedName = blockedMember.getPartnerProfile().getName();
            }

            return new BlockMemberDTO(
                    blockedMember.getId(),
                    blockedName,
                    block.getCreatedAt()
            );
        }

        public static List<BlockMemberDTO> fromList(
                List<Block> blockList
        ) {
            return blockList.stream()
                    .map(BlockResponseDTO.BlockMemberDTO::from)
                    .collect(Collectors.toList());
        }

    }


    public record CheckBlockMemberDTO (
            Long memberId,
            String name,
            boolean blocked
    ) {
        public static CheckBlockMemberDTO of(
                Long blockedId,
                String blockedName,
                boolean blocked
        ) {
            return new CheckBlockMemberDTO(
                    blockedId,
                    blockedName,
                    blocked
            );
        }
    }

}
