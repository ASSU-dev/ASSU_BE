package com.assu.server.domain.review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.review.dto.ReviewResponseDTO;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.exception.CustomReviewException;
import com.assu.server.domain.review.repository.ReviewRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.s3.AmazonS3Manager;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

	@InjectMocks
	private ReviewServiceImpl reviewService;

	@Mock
	private ReviewRepository reviewRepository;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private PartnerRepository partnerRepository;

	@Mock
	private StudentRepository studentRepository;

	@Mock
	private AmazonS3Manager amazonS3Manager;

	@Mock
	private PartnershipUsageRepository partnershipUsageRepository;

	private static final Long STORE_ID = 500L;
	private static final Long REVIEW_ID = 100L;

	// ===== deleteReview =====

	@Test
	@DisplayName("존재하지 않는 리뷰를 삭제하면 _BAD_REQUEST 예외가 발생한다")
	void deleteReview_ReviewNotFound_ThrowsException() {
		// 1. Given
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

		// 2. When
		CustomReviewException exception = assertThrows(CustomReviewException.class,
			() -> reviewService.deleteReview(REVIEW_ID));

		// 3. Then
		assertEquals(ErrorStatus._BAD_REQUEST, exception.getCode());
		verify(reviewRepository, never()).deleteById(anyLong());
	}

	@Test
	@DisplayName("리뷰 삭제 성공 시 리뷰를 삭제하고 매장 평점을 재계산하여 반영한다")
	void deleteReview_Success_DeletesAndRecalculatesRate() {
		// 1. Given (삭제 후 남은 리뷰 평균이 4.4점)
		Store store = Store.builder().id(STORE_ID).name("역전할머니 맥주").rate(5).build();
		Review review = mock(Review.class);
		when(review.getStore()).thenReturn(store);
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
		when(reviewRepository.standardScoreWithStatus(STORE_ID, ReportedStatus.NORMAL, ReportedStatus.NORMAL))
			.thenReturn(4.4f);
		when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

		// 2. When
		ReviewResponseDTO.DeleteReviewResponseDTO response = reviewService.deleteReview(REVIEW_ID);

		// 3. Then
		assertEquals(REVIEW_ID, response.reviewId());
		verify(reviewRepository, times(1)).deleteById(REVIEW_ID);
		assertEquals(4, store.getRate());
		verify(storeRepository, times(1)).save(store);
	}

	@Test
	@DisplayName("남은 리뷰가 없어 평균이 null이면 매장 평점은 0으로 초기화된다")
	void deleteReview_NoRemainingReviews_ResetsRateToZero() {
		// 1. Given
		Store store = Store.builder().id(STORE_ID).name("역전할머니 맥주").rate(5).build();
		Review review = mock(Review.class);
		when(review.getStore()).thenReturn(store);
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
		when(reviewRepository.standardScoreWithStatus(STORE_ID, ReportedStatus.NORMAL, ReportedStatus.NORMAL))
			.thenReturn(null);
		when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

		// 2. When
		reviewService.deleteReview(REVIEW_ID);

		// 3. Then
		assertEquals(0, store.getRate());
	}

	// ===== standardScore =====

	@Test
	@DisplayName("리뷰가 없는 매장의 평점 조회 시 0점으로 반환한다")
	void standardScore_NoReviews_ReturnsZero() {
		// 1. Given
		when(reviewRepository.standardScoreWithStatus(STORE_ID, ReportedStatus.NORMAL, ReportedStatus.NORMAL))
			.thenReturn(null);

		// 2. When
		ReviewResponseDTO.StandardScoreResponseDTO response = reviewService.standardScore(STORE_ID);

		// 3. Then
		assertEquals(0f, response.score());
	}

	@Test
	@DisplayName("정상/미신고 리뷰만 집계한 평균 평점을 반환한다")
	void standardScore_WithReviews_ReturnsAverage() {
		// 1. Given
		when(reviewRepository.standardScoreWithStatus(STORE_ID, ReportedStatus.NORMAL, ReportedStatus.NORMAL))
			.thenReturn(4.5f);

		// 2. When
		ReviewResponseDTO.StandardScoreResponseDTO response = reviewService.standardScore(STORE_ID);

		// 3. Then
		assertEquals(4.5f, response.score());
	}

	// ===== myStoreAverage =====

	@Test
	@DisplayName("존재하지 않는 파트너가 내 매장 평점을 조회하면 NO_SUCH_PARTNER 예외가 발생한다")
	void myStoreAverage_PartnerNotFound_ThrowsException() {
		// 1. Given
		when(partnerRepository.findById(1L)).thenReturn(Optional.empty());

		// 2. When
		CustomReviewException exception = assertThrows(CustomReviewException.class,
			() -> reviewService.myStoreAverage(1L));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_PARTNER, exception.getCode());
	}

	@Test
	@DisplayName("파트너에게 연결된 매장이 없으면 NO_SUCH_STORE 예외가 발생한다")
	void myStoreAverage_StoreNotFound_ThrowsException() {
		// 1. Given
		Partner partner = Partner.builder().id(1L).name("역전할머니 맥주").isPhoneVerified(false).build();
		when(partnerRepository.findById(1L)).thenReturn(Optional.of(partner));
		when(storeRepository.findByPartner(partner)).thenReturn(Optional.empty());

		// 2. When
		CustomReviewException exception = assertThrows(CustomReviewException.class,
			() -> reviewService.myStoreAverage(1L));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_STORE, exception.getCode());
	}
}
