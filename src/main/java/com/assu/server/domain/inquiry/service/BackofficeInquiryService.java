package com.assu.server.domain.inquiry.service;

import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.inquiry.dto.InquiryResponseDTO;
import com.assu.server.domain.inquiry.entity.Inquiry;

public interface BackofficeInquiryService {
    PageResponseDTO<InquiryResponseDTO> getInquiries(Inquiry.StatusFilter status, String keyword, int page, int size);
    InquiryResponseDTO getById(Long inquiryId);
    void answer(Long inquiryId, String answerText);
}
