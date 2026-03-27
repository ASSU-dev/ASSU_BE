package com.assu.server.domain.report.service;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.report.dto.ReportRequestDTO;
import com.assu.server.domain.report.dto.ReportResponseDTO;
import com.assu.server.domain.report.entity.Report;
import com.assu.server.domain.report.entity.enums.ReportStatus;
import com.assu.server.domain.report.entity.enums.ReportTargetType;
import com.assu.server.domain.report.repository.ReportRepository;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.repository.ReviewRepository;
import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.domain.suggestion.repository.SuggestionRepository;
import com.assu.server.domain.report.exception.ReportException;
import com.assu.server.domain.report.event.ReportProcessedEvent;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final MemberRepository memberRepository;
    private final ReviewRepository reviewRepository;
    private final SuggestionRepository suggestionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public ReportResponseDTO.CreateReportResponse reportContent(Long reporterId,
            ReportRequestDTO.CreateContentReportRequest request) {
        // 신고자 존재 확인
        Member reporter = memberRepository.findById(reporterId)
                .orElseThrow(() -> new ReportException(ErrorStatus.NO_MEMBER));

        // 신고 대상 존재 확인 및 자기 자신 신고 방지
        validateContentReportTarget(reporterId, request.getTargetType(), request.getTargetId());

        // 중복 신고 확인
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, request.getTargetType(),
                request.getTargetId())) {
            throw new ReportException(ErrorStatus.REPORT_DUPLICATE);
        }

        // 콘텐츠 신고 생성
        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reported(null) // 콘텐츠 신고는 피신고자 없음
                .reportType(request.getReportType())
                .status(ReportStatus.PENDING)
                .build();

        Report savedReport = reportRepository.save(report);

        // 신고 생성 이벤트 발행
        eventPublisher.publishEvent(new ReportProcessedEvent(
                savedReport.getId(),
                savedReport.getTargetType(),
                savedReport.getTargetId(),
                savedReport.getStatus()));

        return ReportResponseDTO.CreateReportResponse.of(savedReport.getId());
    }

    @Override
    @Transactional
    public ReportResponseDTO.CreateReportResponse reportStudent(Long reporterId,
            ReportRequestDTO.CreateStudentReportRequest request) {
        // 신고자 존재 확인
        Member reporter = memberRepository.findById(reporterId)
                .orElseThrow(() -> new ReportException(ErrorStatus.NO_MEMBER));

        // 신고 대상 존재 확인 및 자기 자신 신고 방지
        Student reportedStudent = validateStudentReportTarget(reporterId, request.getTargetType(),
                request.getTargetId());

        // 중복 신고 확인 (작성자 기준)
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId,
                ReportTargetType.STUDENT_USER,
                reportedStudent.getId())
        ) {
            throw new ReportException(ErrorStatus.REPORT_DUPLICATE);
        }

        // 작성자 신고 생성
        Report report = Report.builder()
                .reporter(reporter)
                .targetType(ReportTargetType.STUDENT_USER)
                .targetId(reportedStudent.getId())
                .reported(reportedStudent.getMember())
                .reportType(request.getReportType())
                .status(ReportStatus.PENDING)
                .build();

        Report savedReport = reportRepository.save(report);

        // 신고 생성 이벤트 발행
        eventPublisher.publishEvent(new ReportProcessedEvent(
                savedReport.getId(),
                savedReport.getTargetType(),
                savedReport.getTargetId(),
                savedReport.getStatus()));

        return ReportResponseDTO.CreateReportResponse.of(savedReport.getId());
    }

    // 콘텐츠 신고 대상 검증 메서드
    private void validateContentReportTarget(Long reporterId, ReportTargetType targetType, Long targetId) {
        switch (targetType) {
            case REVIEW:
                Review review = reviewRepository.findById(targetId)
                        .orElseThrow(() -> new ReportException(ErrorStatus.NO_SUCH_MEMBER));
                if (reporterId.equals(review.getStudent().getId())) {
                    throw new ReportException(ErrorStatus.REVIEW_REPORT_SELF_NOT_ALLOWED);
                }
                break;
            case SUGGESTION:
                Suggestion suggestion = suggestionRepository.findById(targetId)
                        .orElseThrow(() -> new ReportException(ErrorStatus.NO_SUCH_SUGGESTION));
                if (reporterId.equals(suggestion.getStudent().getId())) {
                    throw new ReportException(ErrorStatus.SUGGESTION_REPORT_SELF_NOT_ALLOWED);
                }
                break;
            default:
                throw new ReportException(ErrorStatus.INVALID_REPORT_TYPE);
        }
    }

    // 작성자 신고 대상 검증 메서드
    private Student validateStudentReportTarget(Long reporterId, ReportTargetType targetType, Long targetId) {
        Student student;

        switch (targetType) {
            case REVIEW:
                Review review = reviewRepository.findById(targetId)
                        .orElseThrow(() -> new ReportException(ErrorStatus.NO_SUCH_MEMBER));
                student = review.getStudent();
                break;
            case SUGGESTION:
                Suggestion suggestion = suggestionRepository.findById(targetId)
                        .orElseThrow(() -> new ReportException(ErrorStatus.NO_SUCH_SUGGESTION));
                student = suggestion.getStudent();
                break;
            default:
                throw new ReportException(ErrorStatus.INVALID_REPORT_TYPE);
        }

        // 자기 자신 신고 방지
        if (reporterId.equals(student.getId())) {
            throw new ReportException(ErrorStatus.REPORT_SELF_NOT_ALLOWED);
        }

        return student;
    }
}