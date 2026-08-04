package com.assu.server.domain.appreview.service;

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

import com.assu.server.domain.appreview.dto.AppReviewRequestDTO;
import com.assu.server.domain.appreview.entity.AppReview;
import com.assu.server.domain.appreview.repository.AppReviewRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;

@ExtendWith(MockitoExtension.class)
class AppReviewServiceImplTest {

	@InjectMocks
	private AppReviewServiceImpl appReviewService;

	@Mock
	private AppReviewRepository appReviewRepository;

	@Mock
	private MemberRepository memberRepository;

	@Test
	@DisplayName("존재하지 않는 멤버가 앱 리뷰를 작성하면 NO_SUCH_MEMBER 예외가 발생한다")
	void create_MemberNotFound_ThrowsException() {
		// 1. Given
		Long memberId = 1L;
		AppReviewRequestDTO request = new AppReviewRequestDTO(5, "너무 좋아용~");
		when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> appReviewService.create(request, memberId));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_MEMBER, exception.getCode());
		verify(appReviewRepository, never()).save(any());
	}

	@Test
	@DisplayName("앱 리뷰 작성 시 요청 내용대로 리뷰가 저장된다")
	void create_Success_SavesReview() {
		// 1. Given
		Long memberId = 1L;
		Member member = mock(Member.class);
		AppReviewRequestDTO request = new AppReviewRequestDTO(4, "제휴 정보가 한눈에 보여서 편해요");
		when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

		// 2. When
		appReviewService.create(request, memberId);

		// 3. Then (저장된 엔티티 필드 검증)
		ArgumentCaptor<AppReview> captor = ArgumentCaptor.forClass(AppReview.class);
		verify(appReviewRepository, times(1)).save(captor.capture());

		AppReview saved = captor.getValue();
		assertEquals(member, saved.getMember());
		assertEquals(4, saved.getRate());
		assertEquals("제휴 정보가 한눈에 보여서 편해요", saved.getContent());
	}
}
