package com.assu.server.domain.inquiry.service;

import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.inquiry.dto.InquiryResponseDTO;
import com.assu.server.domain.inquiry.entity.Inquiry;
import com.assu.server.domain.inquiry.entity.Inquiry.Status;
import com.assu.server.domain.inquiry.repository.InquiryRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BackofficeInquiryServiceImpl implements BackofficeInquiryService {

    private final InquiryRepository inquiryRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<InquiryResponseDTO> getInquiries(Inquiry.StatusFilter status, String keyword, int page, int size) {
        if (page < 1) throw new GeneralException(ErrorStatus.PAGE_UNDER_ONE);
        if (size < 1 || size > 200) throw new GeneralException(ErrorStatus.PAGE_SIZE_INVALID);

        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        Page<Inquiry> result;
        if (hasKeyword) {
            result = switch (status) {
                case WAITING -> inquiryRepository.findByStatusAndKeyword(Status.WAITING, keyword, pageable);
                case ANSWERED -> inquiryRepository.findByStatusAndKeyword(Status.ANSWERED, keyword, pageable);
                case ALL -> inquiryRepository.findByKeyword(keyword, pageable);
            };
        } else {
            result = switch (status) {
                case WAITING -> inquiryRepository.findByStatus(Status.WAITING, pageable);
                case ANSWERED -> inquiryRepository.findByStatus(Status.ANSWERED, pageable);
                case ALL -> inquiryRepository.findAll(pageable);
            };
        }
        return PageResponseDTO.of(result.map(InquiryResponseDTO::from));
    }

    @Override
    @Transactional(readOnly = true)
    public InquiryResponseDTO getById(Long inquiryId) {
        Inquiry inquiry = inquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_INQUIRY));
        return InquiryResponseDTO.from(inquiry);
    }

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