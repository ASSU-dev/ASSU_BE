package com.assu.server.domain.report.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.report.dto.ReportRequestDTO;
import com.assu.server.domain.report.dto.ReportResponseDTO;
import com.assu.server.domain.report.entity.Report;
import com.assu.server.domain.report.entity.enums.ReportStatus;
import com.assu.server.domain.report.entity.enums.ReportTargetType;
import com.assu.server.domain.report.entity.enums.ReportType;
import com.assu.server.domain.report.event.ReportProcessedEvent;
import com.assu.server.domain.report.exception.ReportException;
import com.assu.server.domain.report.repository.ReportRepository;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.repository.ReviewRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.suggestion.entity.Suggestion;
import com.assu.server.domain.suggestion.repository.SuggestionRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

	@InjectMocks
	private ReportServiceImpl reportService;

	@Mock
	private ReportRepository reportRepository;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private ReviewRepository reviewRepository;

	@Mock
	private SuggestionRepository suggestionRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private static final Long REPORTER_ID = 1L;
	private static final Long REVIEW_ID = 100L;

	private Member givenReporter() {
		Member reporter = mock(Member.class);
		when(memberRepository.findById(REPORTER_ID)).thenReturn(Optional.of(reporter));
		return reporter;
	}

	private Student studentWithId(Long id) {
		return Student.builder().id(id).university(University.SSU).build();
	}

	@Test
	@DisplayName("자신이 작성한 리뷰를 신고하면 REVIEW_REPORT_SELF_NOT_ALLOWED 예외가 발생한다")
	void reportContent_SelfReview_ThrowsException() {
		// 1. Given (신고자와 리뷰 작성자가 동일)
		givenReporter();
		Review review = mock(Review.class);
		when(review.getStudent()).thenReturn(studentWithId(REPORTER_ID));
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

		ReportRequestDTO.CreateContentReportRequest request = ReportRequestDTO.CreateContentReportRequest.builder()
			.targetType(ReportTargetType.REVIEW).targetId(REVIEW_ID)
			.reportType(ReportType.REVIEW_SPAM).build();

		// 2. When
		ReportException exception = assertThrows(ReportException.class,
			() -> reportService.reportContent(REPORTER_ID, request));

		// 3. Then
		assertEquals(ErrorStatus.REVIEW_REPORT_SELF_NOT_ALLOWED, exception.getCode());
		verify(reportRepository, never()).save(any());
	}

	@Test
	@DisplayName("같은 대상을 중복 신고하면 REPORT_DUPLICATE 예외가 발생한다")
	void reportContent_Duplicate_ThrowsException() {
		// 1. Given
		givenReporter();
		Review review = mock(Review.class);
		when(review.getStudent()).thenReturn(studentWithId(999L));
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
		when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
			REPORTER_ID, ReportTargetType.REVIEW, REVIEW_ID)).thenReturn(true);

		ReportRequestDTO.CreateContentReportRequest request = ReportRequestDTO.CreateContentReportRequest.builder()
			.targetType(ReportTargetType.REVIEW).targetId(REVIEW_ID)
			.reportType(ReportType.REVIEW_SPAM).build();

		// 2. When
		ReportException exception = assertThrows(ReportException.class,
			() -> reportService.reportContent(REPORTER_ID, request));

		// 3. Then
		assertEquals(ErrorStatus.REPORT_DUPLICATE, exception.getCode());
		verify(reportRepository, never()).save(any());
	}

	@Test
	@DisplayName("리뷰 신고 성공 시 PENDING 상태로 저장되고 신고 처리 이벤트가 발행된다")
	void reportContent_Success_SavesReportAndPublishesEvent() {
		// 1. Given
		Member reporter = givenReporter();
		Review review = mock(Review.class);
		when(review.getStudent()).thenReturn(studentWithId(999L));
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
		when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
			REPORTER_ID, ReportTargetType.REVIEW, REVIEW_ID)).thenReturn(false);

		Report savedReport = Report.builder()
			.id(10L).reporter(reporter).targetType(ReportTargetType.REVIEW).targetId(REVIEW_ID)
			.reportType(ReportType.REVIEW_SPAM).status(ReportStatus.PENDING).build();
		when(reportRepository.save(any(Report.class))).thenReturn(savedReport);

		ReportRequestDTO.CreateContentReportRequest request = ReportRequestDTO.CreateContentReportRequest.builder()
			.targetType(ReportTargetType.REVIEW).targetId(REVIEW_ID)
			.reportType(ReportType.REVIEW_SPAM).build();

		// 2. When
		ReportResponseDTO.CreateReportResponse response = reportService.reportContent(REPORTER_ID, request);

		// 3. Then
		assertNotNull(response);

		// 저장된 신고 내용 검증 (콘텐츠 신고는 피신고자 없음)
		ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
		verify(reportRepository).save(reportCaptor.capture());
		assertEquals(ReportStatus.PENDING, reportCaptor.getValue().getStatus());
		assertNull(reportCaptor.getValue().getReported());

		verify(eventPublisher, times(1)).publishEvent(any(ReportProcessedEvent.class));
	}

	@Test
	@DisplayName("존재하지 않는 건의글을 신고하면 NO_SUCH_SUGGESTION 예외가 발생한다")
	void reportContent_SuggestionNotFound_ThrowsException() {
		// 1. Given
		givenReporter();
		when(suggestionRepository.findById(200L)).thenReturn(Optional.empty());

		ReportRequestDTO.CreateContentReportRequest request = ReportRequestDTO.CreateContentReportRequest.builder()
			.targetType(ReportTargetType.SUGGESTION).targetId(200L)
			.reportType(ReportType.SUGGESTION_SPAM).build();

		// 2. When
		ReportException exception = assertThrows(ReportException.class,
			() -> reportService.reportContent(REPORTER_ID, request));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_SUGGESTION, exception.getCode());
	}

	@Test
	@DisplayName("자기 자신을 작성자 신고하면 REPORT_SELF_NOT_ALLOWED 예외가 발생한다")
	void reportStudent_SelfReport_ThrowsException() {
		// 1. Given
		givenReporter();
		Review review = mock(Review.class);
		when(review.getStudent()).thenReturn(studentWithId(REPORTER_ID));
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

		ReportRequestDTO.CreateStudentReportRequest request = ReportRequestDTO.CreateStudentReportRequest.builder()
			.targetType(ReportTargetType.REVIEW).targetId(REVIEW_ID)
			.reportType(ReportType.STUDENT_USER_SPAM).build();

		// 2. When
		ReportException exception = assertThrows(ReportException.class,
			() -> reportService.reportStudent(REPORTER_ID, request));

		// 3. Then
		assertEquals(ErrorStatus.REPORT_SELF_NOT_ALLOWED, exception.getCode());
	}

	@Test
	@DisplayName("작성자 신고 성공 시 신고 대상이 STUDENT_USER 타입으로 저장된다")
	void reportStudent_Success_SavesStudentUserReport() {
		// 1. Given (건의글 작성자를 신고)
		Member reporter = givenReporter();

		Member reportedMember = mock(Member.class);
		Student reportedStudent = Student.builder()
			.id(999L).member(reportedMember).university(University.SSU).build();

		Suggestion suggestion = mock(Suggestion.class);
		when(suggestion.getStudent()).thenReturn(reportedStudent);
		when(suggestionRepository.findById(200L)).thenReturn(Optional.of(suggestion));

		when(reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
			REPORTER_ID, ReportTargetType.STUDENT_USER, 999L)).thenReturn(false);

		Report savedReport = Report.builder()
			.id(10L).reporter(reporter).targetType(ReportTargetType.STUDENT_USER).targetId(999L)
			.reported(reportedMember).reportType(ReportType.STUDENT_USER_SPAM)
			.status(ReportStatus.PENDING).build();
		when(reportRepository.save(any(Report.class))).thenReturn(savedReport);

		ReportRequestDTO.CreateStudentReportRequest request = ReportRequestDTO.CreateStudentReportRequest.builder()
			.targetType(ReportTargetType.SUGGESTION).targetId(200L)
			.reportType(ReportType.STUDENT_USER_SPAM).build();

		// 2. When
		reportService.reportStudent(REPORTER_ID, request);

		// 3. Then
		ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
		verify(reportRepository).save(reportCaptor.capture());
		assertEquals(ReportTargetType.STUDENT_USER, reportCaptor.getValue().getTargetType());
		assertEquals(999L, reportCaptor.getValue().getTargetId());
		assertEquals(reportedMember, reportCaptor.getValue().getReported());

		verify(eventPublisher, times(1)).publishEvent(any(ReportProcessedEvent.class));
	}
}
