package com.assu.server.domain.inquiry.service;

import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.inquiry.dto.InquiryCreateRequestDTO;
import com.assu.server.domain.inquiry.dto.InquiryResponseDTO;
import com.assu.server.domain.inquiry.entity.Inquiry;
import com.assu.server.domain.inquiry.entity.Inquiry.Status;
import com.assu.server.domain.inquiry.repository.InquiryRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InquiryServiceImpl implements InquiryService {

    private final InquiryRepository inquiryRepository;
    private final MemberRepository memberRepository;

    /** 문의 등록 */
    @Override
    public Long create(InquiryCreateRequestDTO inquiryCreateRequestDTO, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));

        Inquiry inquiry = Inquiry.create(member, inquiryCreateRequestDTO);

        inquiryRepository.save(inquiry);
        return inquiry.getId();
    }

    /** 문의 내역 조회 (status=all|waiting|answered) */
    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<InquiryResponseDTO> getInquiries(Inquiry.StatusFilter status, int page, int size, Long memberId) {
        if (page < 1) throw new DatabaseException(ErrorStatus.PAGE_UNDER_ONE);
        if (size < 1 || size > 200) throw new DatabaseException(ErrorStatus.PAGE_SIZE_INVALID);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<InquiryResponseDTO> result = list(status, pageable, memberId);
        return PageResponseDTO.of(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InquiryResponseDTO> list(Inquiry.StatusFilter status, Pageable pageable, Long memberId) {
        Page<Inquiry> page = switch (status) {
            case WAITING -> inquiryRepository.findByMemberIdAndStatus(memberId, Status.WAITING, pageable);
            case ANSWERED -> inquiryRepository.findByMemberIdAndStatus(memberId, Status.ANSWERED, pageable);
            case ALL -> inquiryRepository.findByMemberId(memberId, pageable);
        };
        return page.map(InquiryResponseDTO::from);
    }

    /** 단건 상세 조회 */
    @Override
    @Transactional(readOnly = true)
    public InquiryResponseDTO get(Long id, Long memberId) {
        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_INQUIRY));

        if (!inquiry.getMember().getId().equals(memberId)) {
            throw new DatabaseException(ErrorStatus.FORBIDDEN_INQUIRY);
        }

        return InquiryResponseDTO.from(inquiry);
    }

    /** 답변 저장(상태 ANSWERED 전환) */
    @Override
    public void answer(Long inquiryId, String answerText) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_INQUIRY));

        if (inquiry.getStatus() == Inquiry.Status.ANSWERED) {
            throw new DatabaseException(ErrorStatus.ALREADY_ANSWERED);
        }

        inquiry.answer(answerText);
    }
}