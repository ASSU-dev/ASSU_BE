package com.assu.server.domain.chat.service;

import com.assu.server.domain.chat.dto.BlockResponseDTO;
import com.assu.server.domain.chat.entity.Block;
import com.assu.server.domain.chat.repository.BlockRepository;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlockServiceImpl implements BlockService {
    private final BlockRepository blockRepository;
    private final MemberRepository memberRepository;

    @Transactional
    @Override
    public BlockResponseDTO.BlockMemberDTO blockMember(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        Member blocker = memberRepository.findById(blockerId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST));
        Member blocked = memberRepository.findById(blockedId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST));


        // 이미 차단했는지 확인
        if (blockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            // 이미 차단한 경우, 아무것도 하지 않거나 예외 처리
            return null;
        }

        String blockedName = resolveBlockedName(blocked);

        Block block = Block.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build();

        blockRepository.save(block);

        return BlockResponseDTO.BlockMemberDTO.of(blockedId,  blockedName);
    }

    @Override
    public BlockResponseDTO.CheckBlockMemberDTO checkBlock(Long blockerId, Long blockedId) {

        Member blocker = memberRepository.findById(blockerId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST));
        Member blocked = memberRepository.findById(blockedId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST));

        String blockedName = resolveBlockedName(blocked);

        if (blockRepository.existsBlockRelationBetween(blocker, blocked)) {
            return BlockResponseDTO.CheckBlockMemberDTO.of(blockedId, blockedName, true);
        }
        else  {
            return BlockResponseDTO.CheckBlockMemberDTO.of(blockedId, blockedName, false);
        }
    }

    @Transactional
    @Override
    public BlockResponseDTO.BlockMemberDTO unblockMember(Long blockerId, Long blockedId) {
        Member blocker = memberRepository.findById(blockerId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));
        Member blocked = memberRepository.findById(blockedId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        String blockedName = resolveBlockedName(blocked);

        // Transactional 환경에서는 Dirty-checking으로 delete 쿼리가 나갑니다.
        blockRepository.deleteByBlockerAndBlocked(blocker, blocked);
        return BlockResponseDTO.BlockMemberDTO.of(blockedId, blockedName);
    }

    @Transactional
    @Override
    public List<BlockResponseDTO.BlockMemberDTO> getMyBlockList(Long blockerId) {
        Member blocker = memberRepository.findById(blockerId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST));

        List<Block> blockList = blockRepository.findByBlocker(blocker);

        return BlockResponseDTO.BlockMemberDTO.fromList(blockList);
    }

    // 차단 대상은 ADMIN/PARTNER만 허용
    private String resolveBlockedName(Member blocked) {
        UserRole blockedRole = blocked.getRole();
        if (blockedRole != UserRole.ADMIN && blockedRole != UserRole.PARTNER) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }
        return blocked.resolveName();
    }
}
