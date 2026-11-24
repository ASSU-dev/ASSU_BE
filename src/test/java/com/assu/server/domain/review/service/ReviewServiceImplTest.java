package com.assu.server.domain.review.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.review.dto.ReviewRequestDTO;
import com.assu.server.domain.review.dto.ReviewResponseDTO;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.exception.CustomReviewException;
import com.assu.server.domain.review.repository.ReviewRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.PartnershipUsage;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
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
	private static final Long USAGE_ID = 300L;
	private static final Long MEMBER_ID = 1L;
	private static final Long OTHER_MEMBER_ID = 2L;

	private Student studentWithId(Long id) {
		return Student.builder().id(id).university(University.SSU).build();
	}

	private ReviewRequestDTO.WriteReviewRequestDTO writeRequest() {
		return ReviewRequestDTO.WriteReviewRequestDTO.builder()
			.partnershipUsageId(USAGE_ID).storeId(STORE_ID).adminName("총학생회").build();
	}

	// ===== writeReview =====

	@Test
	@DisplayName("다른 학생의 제휴 사용 내역으로 리뷰를 작성하면 FORBIDDEN_USAGE 예외가 발생한다")
	void writeReview_UsageOfAnotherStudent_ThrowsException() {
		// 1. Given
		when(partnershipUsageRepository.markReviewedIfUnreviewed(USAGE_ID, MEMBER_ID)).thenReturn(0);
		when(partnershipUsageRepository.findById(USAGE_ID)).thenReturn(Optional.of(
			PartnershipUsage.builder().id(USAGE_ID).student(studentWithId(OTHER_MEMBER_ID)).isReviewed(false).build()));

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> reviewService.writeReview(writeRequest(), MEMBER_ID, null));

		// 3. Then
		assertEquals(ErrorStatus.FORBIDDEN_USAGE, exception.getCode());
		verify(reviewRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 리뷰를 작성한 제휴 사용 내역으로 다시 작성하면 ALREADY_REVIEWED_USAGE 예외가 발생한다")
	void writeReview_UsageAlreadyReviewed_ThrowsException() {
		// 1. Given
		when(partnershipUsageRepository.markReviewedIfUnreviewed(USAGE_ID, MEMBER_ID)).thenReturn(0);
		when(partnershipUsageRepository.findById(USAGE_ID)).thenReturn(Optional.of(
			PartnershipUsage.builder().id(USAGE_ID).student(studentWithId(MEMBER_ID)).isReviewed(true).build()));

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> reviewService.writeReview(writeRequest(), MEMBER_ID, null));

		// 3. Then
		assertEquals(ErrorStatus.ALREADY_REVIEWED_USAGE, exception.getCode());
		verify(reviewRepository, never()).save(any());
	}

	@Test
	@DisplayName("존재하지 않는 제휴 사용 내역으로 리뷰를 작성하면 NO_SUCH_USAGE 예외가 발생한다")
	void writeReview_UsageNotFound_ThrowsException() {
		// 1. Given
		when(partnershipUsageRepository.markReviewedIfUnreviewed(USAGE_ID, MEMBER_ID)).thenReturn(0);
		when(partnershipUsageRepository.findById(USAGE_ID)).thenReturn(Optional.empty());

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> reviewService.writeReview(writeRequest(), MEMBER_ID, null));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_USAGE, exception.getCode());
		verify(reviewRepository, never()).save(any());
	}

	@Test
	@DisplayName("사용 내역 선점에 성공한 경우에만 리뷰 생성 단계로 넘어간다")
	void writeReview_UsageClaimed_ProceedsToCreateReview() {
		// 1. Given (선점 성공 후 가게 조회에서 중단시켜 생성 단계 진입만 확인)
		when(partnershipUsageRepository.markReviewedIfUnreviewed(USAGE_ID, MEMBER_ID)).thenReturn(1);
		when(storeRepository.findById(STORE_ID)).thenReturn(Optional.empty());

		// 2. When
		CustomReviewException exception = assertThrows(CustomReviewException.class,
			() -> reviewService.writeReview(writeRequest(), MEMBER_ID, null));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_STORE, exception.getCode());
		verify(partnershipUsageRepository, never()).findById(anyLong());
	}

	// ===== deleteReview =====

	@Test
	@DisplayName("존재하지 않는 리뷰를 삭제하면 NO_SUCH_REVIEW 예외가 발생한다")
	void deleteReview_ReviewNotFound_ThrowsException() {
		// 1. Given
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

		// 2. When
		CustomReviewException exception = assertThrows(CustomReviewException.class,
			() -> reviewService.deleteReview(REVIEW_ID, MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_REVIEW, exception.getCode());
		verify(reviewRepository, never()).deleteById(anyLong());
	}

	@Test
	@DisplayName("다른 학생이 작성한 리뷰를 삭제하면 FORBIDDEN_REVIEW 예외가 발생한다")
	void deleteReview_NotAuthor_ThrowsException() {
		// 1. Given
		Review review = mock(Review.class);
		when(review.getStudent()).thenReturn(studentWithId(OTHER_MEMBER_ID));
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

		// 2. When
		CustomReviewException exception = assertThrows(CustomReviewException.class,
			() -> reviewService.deleteReview(REVIEW_ID, MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.FORBIDDEN_REVIEW, exception.getCode());
		verify(reviewRepository, never()).deleteById(anyLong());
	}

	@Test
	@DisplayName("리뷰 삭제 성공 시 리뷰를 삭제하고 매장 평점을 재계산하여 반영한다")
	void deleteReview_Success_DeletesAndRecalculatesRate() {
		// 1. Given (삭제 후 남은 리뷰 평균이 4.4점)
		Store store = Store.builder().id(STORE_ID).name("역전할머니 맥주").rate(5).build();
		Review review = mock(Review.class);
		when(review.getStore()).thenReturn(store);
		when(review.getStudent()).thenReturn(studentWithId(MEMBER_ID));
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
		when(reviewRepository.standardScoreWithStatus(STORE_ID, ReportedStatus.NORMAL, ReportedStatus.NORMAL))
			.thenReturn(4.4f);
		when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

		// 2. When
		ReviewResponseDTO.DeleteReviewResponseDTO response = reviewService.deleteReview(REVIEW_ID, MEMBER_ID);

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
		when(review.getStudent()).thenReturn(studentWithId(MEMBER_ID));
		when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
		when(reviewRepository.standardScoreWithStatus(STORE_ID, ReportedStatus.NORMAL, ReportedStatus.NORMAL))
			.thenReturn(null);
		when(storeRepository.findById(STORE_ID)).thenReturn(Optional.of(store));

		// 2. When
		reviewService.deleteReview(REVIEW_ID, MEMBER_ID);

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

	// ===== 페이지 번호 보정 (1-based 계약) =====

	@Test
	@DisplayName("page=1로 조회하면 첫 페이지(0번 인덱스)를 조회한다")
	void checkStudentReview_OneIndexedPage_QueriesFirstPage() {
		// 1. Given
		when(reviewRepository.findByMemberId(anyLong(), any(Pageable.class))).thenReturn(Page.empty());

		// 2. When
		reviewService.checkStudentReview(1L, PageRequest.of(1, 10));

		// 3. Then
		assertEquals(0, capturedPageNumber());
	}

	@Test
	@DisplayName("page를 생략하면 음수가 되지 않고 첫 페이지를 조회한다")
	void checkStudentReview_PageOmitted_QueriesFirstPageWithoutError() {
		// 1. Given (page 미지정 시 Pageable 기본값은 0)
		when(reviewRepository.findByMemberId(anyLong(), any(Pageable.class))).thenReturn(Page.empty());

		// 2. When
		assertDoesNotThrow(() -> reviewService.checkStudentReview(1L, PageRequest.of(0, 10)));

		// 3. Then
		assertEquals(0, capturedPageNumber());
	}

	@Test
	@DisplayName("page=2로 조회하면 두 번째 페이지(1번 인덱스)를 조회한다")
	void checkStudentReview_SecondPage_QueriesIndexOne() {
		// 1. Given
		when(reviewRepository.findByMemberId(anyLong(), any(Pageable.class))).thenReturn(Page.empty());

		// 2. When
		reviewService.checkStudentReview(1L, PageRequest.of(2, 10));

		// 3. Then
		assertEquals(1, capturedPageNumber());
	}

	@Test
	@DisplayName("정렬 조건은 페이지 번호 보정 후에도 유지된다")
	void checkStudentReview_PreservesSort() {
		// 1. Given
		Sort sort = Sort.by(Sort.Direction.DESC, "id");
		when(reviewRepository.findByMemberId(anyLong(), any(Pageable.class))).thenReturn(Page.empty());

		// 2. When
		reviewService.checkStudentReview(1L, PageRequest.of(1, 10, sort));

		// 3. Then
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(reviewRepository).findByMemberId(anyLong(), captor.capture());
		assertEquals(sort, captor.getValue().getSort());
		assertEquals(10, captor.getValue().getPageSize());
	}

	private int capturedPageNumber() {
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(reviewRepository).findByMemberId(anyLong(), captor.capture());
		return captor.getValue().getPageNumber();
	}
}
