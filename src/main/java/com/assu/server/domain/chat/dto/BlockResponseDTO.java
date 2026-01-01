package com.assu.server.domain.chat.dto;

import com.assu.server.domain.chat.entity.Block;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class BlockResponseDTO {

    public record BlockMemberDTO (
            Long memberId,
            String name,
            LocalDateTime blockDate
    ) {
        public static BlockMemberDTO toBlockDTO(
                Long blockedId,
                String blockedName
        ) {
            return new BlockMemberDTO(
                    blockedId,
                    blockedName,
                    LocalDateTime.now()
            );
        }

        public static BlockMemberDTO toBlockedMemberDTO(
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

        public static List<BlockMemberDTO> toBlockedMemberListDTO(
                List<Block> blockList
        ) {
            return blockList.stream()
                    .map(BlockResponseDTO.BlockMemberDTO::toBlockedMemberDTO)
                    .collect(Collectors.toList());
        }

    }


    public record CheckBlockMemberDTO (
            Long memberId,
            String name,
            boolean blocked
    ) {
        public static CheckBlockMemberDTO toCheckBlockDTO(
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
