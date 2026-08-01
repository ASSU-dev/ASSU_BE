package com.assu.server.domain.backoffice.service;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.backoffice.dto.BackofficeReportDTO;
import com.assu.server.domain.backoffice.dto.BackofficeReportResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeReportStatusUpdateRequestDTO;
import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.domain.report.entity.Report;
import com.assu.server.domain.report.entity.enums.ReportStatus;
import com.assu.server.domain.report.entity.enums.ReportTargetType;
import com.assu.server.domain.report.event.ReportProcessedEvent;
import com.assu.server.domain.report.repository.ReportRepository;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.repository.ReviewRepository;
import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.domain.suggestion.repository.SuggestionRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BackofficeReportServiceImpl implements BackofficeReportService {

    private final ReviewRepository reviewRepository;
    private final SuggestionRepository suggestionRepository;
    private final ReportRepository reportRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public Page<BackofficeReportResponseDTO> getReports(Pageable pageable) {
        Page<Report> reports = reportRepository.findAll(pageable);
        return reports.map(BackofficeReportResponseDTO::from);
    }

    @Override
    @Transactional(readOnly = true)
    public BackofficeReportResponseDTO getReportDetail(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_REPORT));
        return BackofficeReportResponseDTO.from(report);
    }

    @Override
    public BackofficeReportResponseDTO updateReportStatus(Long reportId, BackofficeReportStatusUpdateRequestDTO req) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_REPORT));

        ReportStatus nextStatus;
        try {
            nextStatus = ReportStatus.valueOf(req.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomAuthException(ErrorStatus._BAD_REQUEST);
        }

        report.updateStatus(nextStatus);

        reportRepository.save(report);

        return BackofficeReportResponseDTO.from(report);
    }

    @Override
    public BackofficeReportDTO.SoftDeleteResponseDTO softDeleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST));

        if (review.getStatus() == ReportedStatus.DELETED) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        processRelatedReports(ReportTargetType.REVIEW, reviewId);

        return BackofficeReportDTO.SoftDeleteResponseDTO.of(reviewId);
    }

    @Override
    public BackofficeReportDTO.SoftDeleteResponseDTO softDeleteSuggestion(Long suggestionId) {
        Suggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_SUGGESTION));

        if (suggestion.getStatus() == ReportedStatus.DELETED) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        processRelatedReports(ReportTargetType.SUGGESTION, suggestionId);

        return BackofficeReportDTO.SoftDeleteResponseDTO.of(suggestionId);
    }

    @Override
    public BackofficeReportDTO.RejectReportResponseDTO rejectReport(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST));

        if (report.getStatus() == ReportStatus.REJECTED) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        report.updateStatus(ReportStatus.REJECTED);
        eventPublisher.publishEvent(new ReportProcessedEvent(
                report.getId(), report.getTargetType(), report.getTargetId(), ReportStatus.REJECTED));

        return BackofficeReportDTO.RejectReportResponseDTO.of(reportId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BackofficeReportDTO.ReportListItemDTO> getReports(boolean pending, boolean processed, boolean rejected) {
        List<ReportStatus> statuses = new java.util.ArrayList<>();

        if (!pending && !processed && !rejected) {
            statuses.addAll(List.of(ReportStatus.values()));
        } else {
            if (pending) statuses.add(ReportStatus.PENDING);
            if (processed) statuses.add(ReportStatus.PROCESSED);
            if (rejected) statuses.add(ReportStatus.REJECTED);
        }

        return BackofficeReportDTO.ReportListItemDTO.fromList(
                reportRepository.findAllByStatusIn(statuses)
        );
    }

    private void processRelatedReports(ReportTargetType targetType, Long targetId) {
        List<Report> reports = reportRepository.findAllByTargetTypeAndTargetId(targetType, targetId);
        reports.forEach(report -> {
            report.updateStatus(ReportStatus.PROCESSED);
            eventPublisher.publishEvent(new ReportProcessedEvent(
                    report.getId(), targetType, targetId, ReportStatus.PROCESSED));
        });
    }
}