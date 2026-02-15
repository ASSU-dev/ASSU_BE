package com.assu.server.domain.inquiry.service;

import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.inquiry.dto.InquiryCreateRequestDTO;
import com.assu.server.domain.inquiry.dto.InquiryResponseDTO;
import com.assu.server.domain.inquiry.entity.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InquiryService {
    Long create(InquiryCreateRequestDTO inquiryCreateRequestDTO, Long memberId);
    PageResponseDTO<InquiryResponseDTO> getInquiries(Inquiry.Status status, int page, int size, Long memberId);
    InquiryResponseDTO get(Long inquiryId, Long memberId);
    void answer(Long inquiryId, String answerText);
    Page<InquiryResponseDTO> list(Inquiry.Status status, Pageable pageable, Long memberId);
}
