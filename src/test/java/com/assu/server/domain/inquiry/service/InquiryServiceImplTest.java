package com.assu.server.domain.inquiry.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.inquiry.dto.InquiryCreateRequestDTO;
import com.assu.server.domain.inquiry.dto.InquiryResponseDTO;
import com.assu.server.domain.inquiry.entity.Inquiry;
import com.assu.server.domain.inquiry.repository.InquiryRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;

@ExtendWith(MockitoExtension.class)
class InquiryServiceImplTest {

	@InjectMocks
	private InquiryServiceImpl inquiryService;

	@Mock
	private InquiryRepository inquiryRepository;

	@Mock
	private MemberRepository memberRepository;

	private static final Long MEMBER_ID = 1L;

	@Test
	@DisplayName("존재하지 않는 회원이 문의를 등록하면 NO_SUCH_MEMBER 예외가 발생한다")
	void create_MemberNotFound_ThrowsException() {
		// 1. Given
		InquiryCreateRequestDTO request =
			new InquiryCreateRequestDTO("상호명 변경 요청", "상호명을 변경하고 싶습니다.", "assu@gmail.com");
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> inquiryService.create(request, MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_MEMBER, exception.getCode());
		verify(inquiryRepository, never()).save(any());
	}

	@Test
	@DisplayName("문의 등록 시 WAITING 상태로 저장된다")
	void create_Success_SavesWaitingInquiry() {
		// 1. Given
		Member member = mock(Member.class);
		InquiryCreateRequestDTO request =
			new InquiryCreateRequestDTO("상호명 변경 요청", "상호명을 변경하고 싶습니다.", "assu@gmail.com");
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		// 2. When
		inquiryService.create(request, MEMBER_ID);

		// 3. Then
		ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
		verify(inquiryRepository, times(1)).save(captor.capture());

		Inquiry saved = captor.getValue();
		assertEquals("상호명 변경 요청", saved.getTitle());
		assertEquals("상호명을 변경하고 싶습니다.", saved.getContent());
		assertEquals("assu@gmail.com", saved.getEmail());
		assertEquals(Inquiry.Status.WAITING, saved.getStatus());
		assertEquals(member, saved.getMember());
	}

	@Test
	@DisplayName("페이지 번호가 1 미만이면 PAGE_UNDER_ONE 예외가 발생한다")
	void getInquiries_PageUnderOne_ThrowsException() {
		// 1. Given & When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> inquiryService.getInquiries(Inquiry.StatusFilter.ALL, 0, 10, MEMBER_ID));

		// 2. Then
		assertEquals(ErrorStatus.PAGE_UNDER_ONE, exception.getCode());
	}

	@Test
	@DisplayName("페이지 크기가 200을 초과하면 PAGE_SIZE_INVALID 예외가 발생한다")
	void getInquiries_PageSizeTooLarge_ThrowsException() {
		// 1. Given & When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> inquiryService.getInquiries(Inquiry.StatusFilter.ALL, 1, 201, MEMBER_ID));

		// 2. Then
		assertEquals(ErrorStatus.PAGE_SIZE_INVALID, exception.getCode());
	}

	@Test
	@DisplayName("WAITING 필터로 조회하면 대기 중인 문의만 조회하는 쿼리가 호출된다")
	void list_WaitingFilter_QueriesWaitingOnly() {
		// 1. Given
		Member member = mock(Member.class);
		Inquiry inquiry = Inquiry.builder()
			.id(10L).member(member).title("문의").content("내용").email("assu@gmail.com")
			.status(Inquiry.Status.WAITING).build();

		Pageable pageable = PageRequest.of(0, 10);
		Page<Inquiry> page = new PageImpl<>(List.of(inquiry), pageable, 1);
		when(inquiryRepository.findByMemberIdAndStatus(MEMBER_ID, Inquiry.Status.WAITING, pageable))
			.thenReturn(page);

		// 2. When
		Page<InquiryResponseDTO> result = inquiryService.list(Inquiry.StatusFilter.WAITING, pageable, MEMBER_ID);

		// 3. Then
		assertEquals(1, result.getTotalElements());
		verify(inquiryRepository, never()).findByMemberId(any(), any());
	}

	@Test
	@DisplayName("ALL 필터로 조회하면 상태 구분 없이 전체 문의를 조회한다")
	void getInquiries_AllFilter_ReturnsPagedResult() {
		// 1. Given
		Member member = mock(Member.class);
		Inquiry inquiry = Inquiry.builder()
			.id(10L).member(member).title("문의").content("내용").email("assu@gmail.com")
			.status(Inquiry.Status.WAITING).build();

		Page<Inquiry> page = new PageImpl<>(List.of(inquiry), PageRequest.of(0, 10), 1);
		when(inquiryRepository.findByMemberId(any(), any(Pageable.class))).thenReturn(page);

		// 2. When
		PageResponseDTO<InquiryResponseDTO> result =
			inquiryService.getInquiries(Inquiry.StatusFilter.ALL, 1, 10, MEMBER_ID);

		// 3. Then
		assertNotNull(result);
		verify(inquiryRepository, times(1)).findByMemberId(any(), any(Pageable.class));
	}

	@Test
	@DisplayName("다른 회원의 문의를 조회하면 FORBIDDEN_INQUIRY 예외가 발생한다")
	void get_NotOwner_ThrowsException() {
		// 1. Given
		Member owner = mock(Member.class);
		when(owner.getId()).thenReturn(999L);
		Inquiry inquiry = Inquiry.builder()
			.id(10L).member(owner).title("문의").content("내용").email("assu@gmail.com")
			.status(Inquiry.Status.WAITING).build();
		when(inquiryRepository.findById(10L)).thenReturn(Optional.of(inquiry));

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> inquiryService.get(10L, MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.FORBIDDEN_INQUIRY, exception.getCode());
	}

	@Test
	@DisplayName("존재하지 않는 문의에 답변하면 NO_SUCH_INQUIRY 예외가 발생한다")
	void answer_InquiryNotFound_ThrowsException() {
		// 1. Given
		when(inquiryRepository.findById(10L)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> inquiryService.answer(10L, "답변입니다."));

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
			() -> inquiryService.answer(10L, "답변입니다."));

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
		inquiryService.answer(10L, "답변입니다.");

		// 3. Then
		assertEquals(Inquiry.Status.ANSWERED, inquiry.getStatus());
		assertEquals("답변입니다.", inquiry.getAnswer());
		assertNotNull(inquiry.getAnsweredAt());
	}
}
