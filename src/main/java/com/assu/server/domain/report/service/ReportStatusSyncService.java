package com.assu.server.domain.report.service;

import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.domain.report.entity.enums.ReportStatus;
import com.assu.server.domain.report.event.ReportProcessedEvent;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.repository.ReviewRepository;
import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.domain.suggestion.repository.SuggestionRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.domain.report.exception.ReportException;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportStatusSyncService {

    private final ReviewRepository reviewRepository;
    private final SuggestionRepository suggestionRepository;
    private final StudentRepository studentRepository;

    @EventListener
    @Async
    @Transactional
    public void handleReportProcessed(ReportProcessedEvent event) {
        log.info("신고 처리 이벤트 수신: Report ID: {}, Target Type: {}, Target ID: {}, Status: {}",
                event.getReportId(), event.getTargetType(), event.getTargetId(), event.getStatus());

        try {
            switch (event.getTargetType()) {
                case REVIEW:
                    syncReviewStatus(event);
                    break;
                case SUGGESTION:
                    syncSuggestionStatus(event);
                    break;
                case STUDENT_USER:
                    syncStudentUserStatus(event);
                    break;
                default:
                    log.warn("알 수 없는 신고 대상 타입: {}", event.getTargetType());
            }
        } catch (Exception e) {
            log.error("신고 상태 동기화 실패: Report ID: {}, Error: {}", event.getReportId(), e.getMessage(), e);
        }
    }

    private void syncReviewStatus(ReportProcessedEvent event) {
        Review review = reviewRepository.findById(event.getTargetId())
                .orElseThrow(() -> new ReportException(ErrorStatus.NO_SUCH_MEMBER));

        ReportedStatus newStatus = mapReportStatusToReportedStatus(event.getStatus());
        if (newStatus != null) {
            review.updateReportedStatus(newStatus);
            reviewRepository.save(review);
            log.info("리뷰 상태 동기화 완료: Review ID: {}, Status: {}", event.getTargetId(), newStatus);
        }
    }

    private void syncSuggestionStatus(ReportProcessedEvent event) {
        Suggestion suggestion = suggestionRepository.findById(event.getTargetId())
                .orElseThrow(() -> new ReportException(ErrorStatus.NO_SUCH_SUGGESTION));

        ReportedStatus newStatus = mapReportStatusToReportedStatus(event.getStatus());
        if (newStatus != null) {
            suggestion.updateReportedStatus(newStatus);
            suggestionRepository.save(suggestion);
            log.info("건의글 상태 동기화 완료: Suggestion ID: {}, Status: {}", event.getTargetId(), newStatus);
        }
    }

    private void syncStudentUserStatus(ReportProcessedEvent event) {
        Student student = studentRepository.findById(event.getTargetId())
                .orElseThrow(() -> new ReportException(ErrorStatus.NO_SUCH_MEMBER));

        ReportedStatus newStatus = mapReportStatusToReportedStatus(event.getStatus());
        if (newStatus != null) {
            student.updateReportedStatus(newStatus);
            studentRepository.save(student);
            log.info("학생 상태 동기화 완료: Student ID: {}, Status: {}", event.getTargetId(), newStatus);
        }
    }

    private ReportedStatus mapReportStatusToReportedStatus(ReportStatus reportStatus) {
        return switch (reportStatus) {
            case PROCESSED -> ReportedStatus.REPORTED;
            case REJECTED -> ReportedStatus.NORMAL;
            case PENDING, UNDER_REVIEW -> null; // PENDING 상태는 상태 변경하지 않음
        };
    }
}
