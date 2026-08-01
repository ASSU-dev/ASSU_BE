package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeChatBlockDTO;
import com.assu.server.domain.chat.entity.Block;
import com.assu.server.domain.chat.repository.BlockRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BackofficeChatBlockServiceImpl implements BackofficeChatBlockService {

    private final BlockRepository blockRepository;
    private final MemberRepository memberRepository;

    @Transactional
    @Override
    public BackofficeChatBlockDTO.BlockBetweenMembersResponseDTO blockBetweenMembers(Long memberAId, Long memberBId) {
        if (memberAId.equals(memberBId)) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        Member memberA = memberRepository.findById(memberAId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));
        Member memberB = memberRepository.findById(memberBId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        if (blockRepository.existsBlockRelationBetween(memberA, memberB)) {
            throw new GeneralException(ErrorStatus.ALREADY_CHAT_BLOCKED);
        }

        // A→B 단방향으로 저장 (existsBlockRelationBetween이 양방향 체크하므로 충분)
        blockRepository.save(Block.builder().blocker(memberA).blocked(memberB).build());

        return BackofficeChatBlockDTO.BlockBetweenMembersResponseDTO.of(memberAId, memberBId);
    }

    @Transactional
    @Override
    public BackofficeChatBlockDTO.BlockBetweenMembersResponseDTO unblockBetweenMembers(Long memberAId, Long memberBId) {
        Member memberA = memberRepository.findById(memberAId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));
        Member memberB = memberRepository.findById(memberBId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        if (!blockRepository.existsBlockRelationBetween(memberA, memberB)) {
            throw new GeneralException(ErrorStatus.NOT_CHAT_BLOCKED);
        }

        blockRepository.deleteAllBlocksBetween(memberAId, memberBId);

        return BackofficeChatBlockDTO.BlockBetweenMembersResponseDTO.of(memberAId, memberBId);
    }

    @Transactional
    @Override
    public BackofficeChatBlockDTO.MemberChatBlockResponseDTO blockMemberChat(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        if (Boolean.TRUE.equals(member.getChatBlocked())) {
            throw new GeneralException(ErrorStatus.ALREADY_CHAT_BLOCKED);
        }

        member.setChatBlocked(true);

        return BackofficeChatBlockDTO.MemberChatBlockResponseDTO.of(memberId, true);
    }

    @Transactional
    @Override
    public BackofficeChatBlockDTO.MemberChatBlockResponseDTO unblockMemberChat(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_MEMBER));

        if (!Boolean.TRUE.equals(member.getChatBlocked())) {
            throw new GeneralException(ErrorStatus.NOT_CHAT_BLOCKED);
        }

        member.setChatBlocked(false);

        return BackofficeChatBlockDTO.MemberChatBlockResponseDTO.of(memberId, false);
    }
}