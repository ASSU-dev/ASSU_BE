package com.assu.server.domain.inquiry.service;

import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.inquiry.dto.InquiryCreateRequestDTO;
import com.assu.server.domain.inquiry.dto.InquiryResponseDTO;
import com.assu.server.domain.inquiry.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryService {
    Long create(InquiryCreateRequestDTO inquiryCreateRequestDTO, Long memberId);
    PageResponseDTO<InquiryResponseDTO> getInquiries(Inquiry.StatusFilter status, int page, int size, Long memberId);
    InquiryResponseDTO get(Long inquiryId, Long memberId);
    void answer(Long inquiryId, String answerText);
    Page<InquiryResponseDTO> list(Inquiry.StatusFilter status, Pageable pageable, Long memberId);

    // 백오피스 전용 (소유권 검증 없음)
    PageResponseDTO<InquiryResponseDTO> getAllInquiries(Inquiry.StatusFilter status, String keyword, int page, int size);
    InquiryResponseDTO getById(Long inquiryId);
}
