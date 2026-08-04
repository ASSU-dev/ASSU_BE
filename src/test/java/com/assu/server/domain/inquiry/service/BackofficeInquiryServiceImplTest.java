package com.assu.server.domain.inquiry.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.inquiry.dto.InquiryResponseDTO;
import com.assu.server.domain.inquiry.entity.Inquiry;
import com.assu.server.domain.inquiry.repository.InquiryRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class BackofficeInquiryServiceImplTest {

	@InjectMocks
	private BackofficeInquiryServiceImpl backofficeInquiryService;

	@Mock
	private InquiryRepository inquiryRepository;

	@Test
	@DisplayName("페이지 번호가 1 미만이면 PAGE_UNDER_ONE 예외가 발생한다")
	void getInquiries_PageUnderOne_ThrowsException() {
		// 1. Given & When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> backofficeInquiryService.getInquiries(Inquiry.StatusFilter.ALL, null, 0, 10));

		// 2. Then
		assertEquals(ErrorStatus.PAGE_UNDER_ONE, exception.getCode());
	}

	@Test
	@DisplayName("페이지 크기가 200을 초과하면 PAGE_SIZE_INVALID 예외가 발생한다")
	void getInquiries_PageSizeTooLarge_ThrowsException() {
		// 1. Given & When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> backofficeInquiryService.getInquiries(Inquiry.StatusFilter.ALL, null, 1, 201));

		// 2. Then
		assertEquals(ErrorStatus.PAGE_SIZE_INVALID, exception.getCode());
	}

	@Test
	@DisplayName("존재하지 않는 문의를 단건 조회하면 NO_SUCH_INQUIRY 예외가 발생한다")
	void getById_InquiryNotFound_ThrowsException() {
		// 1. Given
		when(inquiryRepository.findById(10L)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> backofficeInquiryService.getById(10L));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_INQUIRY, exception.getCode());
	}

	@Test
	@DisplayName("존재하는 문의를 단건 조회하면 DTO로 변환하여 반환한다")
	void getById_Success_ReturnsInquiryResponseDTO() {
		// 1. Given
		Member member = mock(Member.class);
		Inquiry inquiry = Inquiry.builder()
			.id(10L).member(member).title("문의").content("내용").email("assu@gmail.com")
			.status(Inquiry.Status.WAITING).build();
		when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

		// 2. When
		InquiryResponseDTO response = backofficeInquiryService.getById(10L);

		// 3. Then
		assertEquals(10L, response.id());
		assertEquals("문의", response.title());
		assertEquals(Inquiry.Status.WAITING.name(), response.status());
	}

	@Test
	@DisplayName("존재하지 않는 문의에 답변하면 NO_SUCH_INQUIRY 예외가 발생한다")
	void answer_InquiryNotFound_ThrowsException() {
		// 1. Given
		when(inquiryRepository.findById(10L)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> backofficeInquiryService.answer(10L, "답변입니다."));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_INQUIRY, exception.getCode());
	}

	@Test
	@DisplayName("이미 답변된 문의에 다시 답변하면 ALREADY_ANSWERED 예외가 발생한다")
	void answer_AlreadyAnswered_ThrowsException() {
		// 1. Given
		Member member = mock(Member.class);
		Inquiry inquiry = Inquiry.builder()
			.id(10L).member(member).title("문의").content("내용").email("assu@gmail.com")
			.status(Inquiry.Status.ANSWERED).build();
		when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> backofficeInquiryService.answer(10L, "답변입니다."));

		// 3. Then
		assertEquals(ErrorStatus.ALREADY_ANSWERED, exception.getCode());
	}

	@Test
	@DisplayName("답변 저장 시 문의 상태가 ANSWERED로 전환되고 답변 시각이 기록된다")
	void answer_Success_UpdatesStatusToAnswered() {
		// 1. Given
		Member member = mock(Member.class);
		Inquiry inquiry = Inquiry.builder()
			.id(10L).member(member).title("문의").content("내용").email("assu@gmail.com")
			.status(Inquiry.Status.WAITING).build();
		when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

		// 2. When
		backofficeInquiryService.answer(10L, "답변입니다.");

		// 3. Then
		assertEquals(Inquiry.Status.ANSWERED, inquiry.getStatus());
		assertEquals("답변입니다.", inquiry.getAnswer());
		assertNotNull(inquiry.getAnsweredAt());
	}
}
