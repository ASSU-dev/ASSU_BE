package com.assu.server.domain.student.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import com.assu.server.domain.partnership.repository.GoodsRepository;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.entity.enums.StoreCategory;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.dto.StudentResponseDTO;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.entity.UserPaper;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import com.assu.server.domain.student.repository.StampEventApplicantRepository;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.domain.student.repository.UserPaperRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.infra.discord.DiscordNotifier;
import com.assu.server.infra.s3.ImageCdnManager;
import com.assu.server.infra.s3.ImageSize;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.util.function.Consumer;

@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

	@InjectMocks
	private StudentServiceImpl studentService;

	@Mock
	private StudentRepository studentRepository;

	@Mock
	private UserPaperRepository userPaperRepository;

	@Mock
	private PaperContentRepository paperContentRepository;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private PartnershipUsageRepository partnershipUsageRepository;

	@Mock
	private StampEventApplicantRepository stampEventApplicantRepository;

	@Mock
	private GoodsRepository goodsRepository;

	@Mock
	private AdminRepository adminRepository;

	@Mock
	private PaperRepository paperRepository;

	@Mock
	private NotificationCommandService notificationCommandService;

	@Mock
	private DiscordNotifier discordNotifier;

	@Mock
	private ImageCdnManager imageCdnManager;

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private EntityManager entityManager;

	private static final Long STUDENT_ID = 1L;

	// ===== getStamp / addStamp =====

	@Test
	@DisplayName("존재하지 않는 학생이 스탬프를 조회하면 NO_SUCH_STUDENT 예외가 발생한다")
	void getStamp_StudentNotFound_ThrowsException() {
		// 1. Given
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> studentService.getStamp(STUDENT_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_STUDENT, exception.getCode());
	}

	@Test
	@DisplayName("스탬프 적립 시 스탬프가 1 증가한다")
	void addStamp_Success_IncrementsStamp() {
		// 1. Given
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).stamp(3).build();
		when(studentRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		StudentResponseDTO.CheckStampResponseDTO response = studentService.addStamp(STUDENT_ID);

		// 3. Then
		assertEquals(4, response.getStamp());
		assertEquals("스탬프가 적립되었습니다.", response.getMessage());
		verify(stampEventApplicantRepository, never()).save(any());
	}

	@Test
	@DisplayName("스탬프가 10개가 되면 이벤트에 자동 응모되고 스탬프가 0으로 초기화된다")
	void addStamp_TenthStamp_AppliesEventAndResets() {
		// 1. Given
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).stamp(9).build();
		when(studentRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		StudentResponseDTO.CheckStampResponseDTO response = studentService.addStamp(STUDENT_ID);

		// 3. Then
		assertEquals(0, response.getStamp());
		assertEquals("스탬프 10개를 모아 자동 응모 되었습니다.", response.getMessage());
		verify(stampEventApplicantRepository, times(1)).save(any());
		verify(notificationCommandService, times(1)).sendStamp(STUDENT_ID);
		verify(discordNotifier, times(1)).send(any());
	}

	@Test
	@DisplayName("오늘 이미 스탬프를 적립한 학생이 다시 적립하면 STAMP_ALREADY_APPLIED_TODAY 예외가 발생한다")
	void addStamp_AlreadyStampedToday_ThrowsException() {
		// 1. Given
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).stamp(3).build();
		student.setStamp();
		when(studentRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> studentService.addStamp(STUDENT_ID));

		// 3. Then
		assertEquals(ErrorStatus.STAMP_ALREADY_APPLIED_TODAY, exception.getCode());
		verify(stampEventApplicantRepository, never()).save(any());
	}

	@Test
	@DisplayName("오늘 스탬프를 적립하지 않은 학생은 제휴 이용 시 스탬프가 1 증가한다")
	void addStampIfNotStampedToday_NotStampedToday_IncrementsStamp() {
		// 1. Given
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).stamp(3).build();
		when(studentRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		studentService.addStampIfNotStampedToday(STUDENT_ID);

		// 3. Then
		assertEquals(4, student.getStamp());
		assertTrue(student.isStampedToday());
	}

	@Test
	@DisplayName("오늘 이미 스탬프를 적립한 학생은 제휴 이용 시 예외 없이 적립을 건너뛴다")
	void addStampIfNotStampedToday_AlreadyStampedToday_SkipsWithoutException() {
		// 1. Given
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).stamp(3).build();
		student.setStamp();
		when(studentRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		assertDoesNotThrow(() -> studentService.addStampIfNotStampedToday(STUDENT_ID));

		// 3. Then
		assertEquals(4, student.getStamp());
		verify(stampEventApplicantRepository, never()).save(any());
	}

	@Test
	@DisplayName("오늘 스탬프를 적립하지 않은 학생이 제휴 이용으로 10개를 달성하면 이벤트에 자동 응모된다")
	void addStampIfNotStampedToday_TenthStamp_AppliesEventAndResets() {
		// 1. Given
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).stamp(9).build();
		when(studentRepository.findByIdForUpdate(STUDENT_ID)).thenReturn(Optional.of(student));

		// 2. When
		studentService.addStampIfNotStampedToday(STUDENT_ID);

		// 3. Then
		assertEquals(0, student.getStamp());
		verify(stampEventApplicantRepository, times(1)).save(any());
		verify(notificationCommandService, times(1)).sendStamp(STUDENT_ID);
	}

	// ===== getUsablePartnership =====

	private UserPaper usablePartnershipWithPartner(Long paperId, Long contentId, String profileKey) {
		Admin admin = Admin.builder().id(10L).name("총학생회").isPhoneVerified(false).build();
		Member member = mock(Member.class);
		when(member.getProfileUrl()).thenReturn(profileKey);
		Partner partner = mock(Partner.class);
		when(partner.getMember()).thenReturn(member);
		Store store = Store.builder().id(500L).name("역전할머니 맥주").partner(partner).build();
		Paper paper = Paper.builder().id(paperId).admin(admin).store(store).build();
		PaperContent content = PaperContent.builder()
			.id(contentId).paper(paper).category("안주").optionType(OptionType.SERVICE).build();
		return UserPaper.builder().paper(paper).paperContent(content).build();
	}

	private UserPaper paperPartnerOnlyPartnership(Long paperId, Long contentId, String profileKey) {
		Member member = mock(Member.class);
		when(member.getProfileUrl()).thenReturn(profileKey);
		Partner partner = mock(Partner.class);
		when(partner.getMember()).thenReturn(member);
		Store store = Store.builder().id(501L).build();
		Paper paper = Paper.builder().id(paperId).partner(partner).store(store).build();
		PaperContent content = PaperContent.builder()
			.id(contentId).paper(paper).optionType(OptionType.SERVICE).build();
		return UserPaper.builder().paper(paper).paperContent(content).build();
	}

	private Store store(Long storeId) {
		return Store.builder().id(storeId).name("store-" + storeId).build();
	}

	private UserPaper usablePartnership(Long paperId, Long contentId, String category, OptionType optionType) {
		Admin admin = Admin.builder().id(10L).name("총학생회").isPhoneVerified(false).build();
		Store store = Store.builder().id(500L).name("역전할머니 맥주").build();
		Paper paper = Paper.builder().id(paperId).admin(admin).store(store).build();
		PaperContent content = PaperContent.builder()
			.id(contentId).paper(paper).category(category).optionType(optionType).build();
		return UserPaper.builder().paper(paper).paperContent(content).build();
	}

	@Test
	@DisplayName("all=false면 사용 가능한 제휴를 최대 2개까지만 반환한다")
	void getUsablePartnership_NotAll_LimitsToTwo() {
		// 1. Given (사용 가능한 제휴 3건)
		List<UserPaper> userPapers = List.of(
			usablePartnership(100L, 1000L, "안주", OptionType.SERVICE),
			usablePartnership(101L, 1001L, "음료", OptionType.SERVICE),
			usablePartnership(102L, 1002L, "주류", OptionType.SERVICE));
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null)).thenReturn(userPapers);

		// 2. When
		List<StudentResponseDTO.UsablePartnershipDTO> result =
			studentService.getUsablePartnership(STUDENT_ID, false, null, null);

		// 3. Then
		assertEquals(2, result.size());
	}

	@Test
	@DisplayName("all=true면 사용 가능한 제휴 전체를 반환한다")
	void getUsablePartnership_All_ReturnsEverything() {
		// 1. Given
		List<UserPaper> userPapers = List.of(
			usablePartnership(100L, 1000L, "안주", OptionType.SERVICE),
			usablePartnership(101L, 1001L, "음료", OptionType.SERVICE),
			usablePartnership(102L, 1002L, "주류", OptionType.SERVICE));
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null)).thenReturn(userPapers);

		// 2. When
		List<StudentResponseDTO.UsablePartnershipDTO> result =
			studentService.getUsablePartnership(STUDENT_ID, true, null, null);

		// 3. Then
		assertEquals(3, result.size());
		assertEquals("총학생회", result.get(0).getAdminName());
		assertEquals("역전할머니 맥주", result.get(0).getPartnerName());
	}

	@Test
	@DisplayName("SERVICE 혜택의 카테고리가 없으면 연결된 상품의 소속명으로 대체된다")
	void getUsablePartnership_NoCategory_FallsBackToGoodsBelonging() {
		// 1. Given (category가 null인 SERVICE 제휴)
		UserPaper userPaper = usablePartnership(100L, 1000L, null, OptionType.SERVICE);
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null)).thenReturn(List.of(userPaper));

		Goods goods = Goods.builder()
			.content(userPaper.getPaperContent()).belonging("생맥주 500cc").build();
		when(goodsRepository.findByContentIdIn(List.of(1000L))).thenReturn(List.of(goods));

		// 2. When
		List<StudentResponseDTO.UsablePartnershipDTO> result =
			studentService.getUsablePartnership(STUDENT_ID, true, null, null);

		// 3. Then
		assertEquals("생맥주 500cc", result.get(0).getCategory());
	}

	@Test
	@DisplayName("storeCategory=BAR이면 레포지토리에 BAR가 전달되고 반환된 BAR 결과만 노출된다")
	void getUsablePartnership_WithStoreCategory_PassesCategoryToRepository() {
		// 1. Given - DB가 BAR만 필터링해서 2건 반환한다고 가정
		List<UserPaper> barUserPapers = List.of(
			usablePartnership(100L, 1000L, "안주", OptionType.SERVICE),
			usablePartnership(101L, 1001L, "주류", OptionType.SERVICE));
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, StoreCategory.BAR, null))
			.thenReturn(barUserPapers);

		// null이면 전체(BAR 2개 + RESTAURANT 1개) 반환
		List<UserPaper> allUserPapers = List.of(
			usablePartnership(100L, 1000L, "안주", OptionType.SERVICE),
			usablePartnership(101L, 1001L, "주류", OptionType.SERVICE),
			usablePartnership(102L, 1002L, "파스타", OptionType.SERVICE));
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null))
			.thenReturn(allUserPapers);

		// 2. When
		List<StudentResponseDTO.UsablePartnershipDTO> barResult =
			studentService.getUsablePartnership(STUDENT_ID, true, StoreCategory.BAR, null);
		List<StudentResponseDTO.UsablePartnershipDTO> allResult =
			studentService.getUsablePartnership(STUDENT_ID, true, null, null);

		// 3. Then
		assertEquals(2, barResult.size());
		assertEquals(3, allResult.size());
		verify(userPaperRepository).findActivePartnershipsByStudentId(STUDENT_ID, StoreCategory.BAR, null);
		verify(userPaperRepository).findActivePartnershipsByStudentId(STUDENT_ID, null, null);
	}

	@Test
	@DisplayName("adminId를 지정하면 레포지토리에 adminId가 전달되고 반환된 결과만 노출된다")
	void getUsablePartnership_WithAdminId_PassesAdminIdToRepository() {
		// 1. Given - DB가 adminId=10으로 필터링해서 1건 반환한다고 가정
		List<UserPaper> filtered = List.of(usablePartnership(100L, 1000L, "안주", OptionType.SERVICE));
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, 10L)).thenReturn(filtered);

		List<UserPaper> all = List.of(
			usablePartnership(100L, 1000L, "안주", OptionType.SERVICE),
			usablePartnership(101L, 1001L, "음료", OptionType.SERVICE));
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null)).thenReturn(all);

		// 2. When
		List<StudentResponseDTO.UsablePartnershipDTO> filteredResult =
			studentService.getUsablePartnership(STUDENT_ID, true, null, 10L);
		List<StudentResponseDTO.UsablePartnershipDTO> allResult =
			studentService.getUsablePartnership(STUDENT_ID, true, null, null);

		// 3. Then
		assertEquals(1, filteredResult.size());
		assertEquals(2, allResult.size());
		verify(userPaperRepository).findActivePartnershipsByStudentId(STUDENT_ID, null, 10L);
		verify(userPaperRepository).findActivePartnershipsByStudentId(STUDENT_ID, null, null);
	}

	@Test
	@DisplayName("파트너 프로필 이미지가 있으면 getUsablePartnership은 썸네일(THUMBNAIL) 이미지 URL을 반환한다")
	void getUsablePartnership_WithPartnerProfileUrl_ReturnsThumbnailUrl() {
		// given
		UserPaper up = paperPartnerOnlyPartnership(100L, 1000L, "partners/42/profile.jpg");
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null)).thenReturn(List.of(up));
		when(goodsRepository.findByContentIdIn(anyList())).thenReturn(List.of());
		when(imageCdnManager.generateUrl("partners/42/profile.jpg", ImageSize.THUMBNAIL)).thenReturn("https://cdn.url");

		// when
		List<StudentResponseDTO.UsablePartnershipDTO> result =
			studentService.getUsablePartnership(STUDENT_ID, true, null, null);

		// then
		assertEquals("https://cdn.url", result.get(0).getPartnerProfileUrl());
	}

	@Test
	@DisplayName("파트너가 없으면 getUsablePartnership의 partnerProfileUrl은 null이다")
	void getUsablePartnership_NoPartner_ReturnsNullProfileUrl() {
		// given (기존 helper는 partner 없는 store)
		UserPaper up = usablePartnership(100L, 1000L, "안주", OptionType.SERVICE);
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null)).thenReturn(List.of(up));
		when(goodsRepository.findByContentIdIn(anyList())).thenReturn(List.of());

		// when
		List<StudentResponseDTO.UsablePartnershipDTO> result =
			studentService.getUsablePartnership(STUDENT_ID, true, null, null);

		// then
		assertNull(result.get(0).getPartnerProfileUrl());
		verify(imageCdnManager, never()).generateUrl(anyString(), any());
	}

	// ===== getRecommendPartnership =====

	@Test
	@DisplayName("이용 가능한 제휴가 10개 이하면 전체를 반환한다")
	void getRecommendPartnership_LessThan10_ReturnsAll() {
		// given
		List<UserPaper> userPapers = List.of(
			usablePartnership(100L, 1000L, "안주", OptionType.SERVICE),
			usablePartnership(101L, 1001L, "음료", OptionType.SERVICE),
			usablePartnership(102L, 1002L, "주류", OptionType.SERVICE));
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID))
			.thenReturn(userPapers.stream().map(up -> up.getPaper().getStore()).toList());
		when(paperContentRepository.findTopByStoreIdIn(anyList())).thenReturn(List.of());
		when(goodsRepository.findByContentIdIn(anyList())).thenReturn(List.of());

		// when
		List<StudentResponseDTO.RecommendCarouselDTO> result = studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		assertEquals(3, result.size());
	}

	@Test
	@DisplayName("이용 가능한 제휴가 10개를 초과하면 10개만 반환한다")
	void getRecommendPartnership_MoreThan10_Returns10() {
		// given (20개 생성)
		List<UserPaper> userPapers = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			userPapers.add(usablePartnership((long) (100 + i), (long) (1000 + i), "안주", OptionType.SERVICE));
		}
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID))
			.thenReturn(userPapers.stream().map(up -> up.getPaper().getStore()).toList());
		when(paperContentRepository.findTopByStoreIdIn(anyList())).thenReturn(List.of());
		when(goodsRepository.findByContentIdIn(anyList())).thenReturn(List.of());

		// when
		List<StudentResponseDTO.RecommendCarouselDTO> result = studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		assertEquals(10, result.size());
	}

	@Test
	@DisplayName("이용 가능한 제휴가 없으면 빈 리스트를 반환한다")
	void getRecommendPartnership_Empty_ReturnsEmptyList() {
		// given
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID)).thenReturn(List.of());
		when(paperContentRepository.findTopByStoreIdIn(anyList())).thenReturn(List.of());
		when(goodsRepository.findByContentIdIn(anyList())).thenReturn(List.of());

		// when
		List<StudentResponseDTO.RecommendCarouselDTO> result = studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("10개 초과 시 선택된 10개 업체 ID로만 콘텐츠를 조회한다")
	void getRecommendPartnership_MoreThan10_QueriesOnlySelectedStoreIds() {
		// given (20개 생성)
		List<UserPaper> userPapers = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			userPapers.add(usablePartnership((long) (100 + i), (long) (1000 + i), "안주", OptionType.SERVICE));
		}
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID))
			.thenReturn(userPapers.stream().map(up -> up.getPaper().getStore()).toList());
		when(paperContentRepository.findTopByStoreIdIn(anyList())).thenReturn(List.of());
		when(goodsRepository.findByContentIdIn(anyList())).thenReturn(List.of());

		// when
		studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
		verify(paperContentRepository).findTopByStoreIdIn(captor.capture());
		assertEquals(10, captor.getValue().size());
	}

	@Test
	@DisplayName("파트너 프로필 이미지가 있으면 getRecommendCarouselPartnership은 썸네일(THUMBNAIL) 이미지 URL을 반환한다")
	void getRecommendCarouselPartnership_WithPartnerProfileUrl_ReturnsThumbnailUrl() {
		// given
		UserPaper up = usablePartnershipWithPartner(100L, 1000L, "partners/42/profile.jpg");
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID)).thenReturn(List.of(up.getPaper().getStore()));
		when(paperContentRepository.findTopByStoreIdIn(anyList())).thenReturn(List.of());
		when(goodsRepository.findByContentIdIn(anyList())).thenReturn(List.of());
		when(imageCdnManager.generateUrl("partners/42/profile.jpg", ImageSize.THUMBNAIL)).thenReturn("https://cdn.url");

		// when
		List<StudentResponseDTO.RecommendCarouselDTO> result = studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		assertEquals("https://cdn.url", result.get(0).partnerProfileUrl());
	}

	@Test
	@DisplayName("파트너가 없으면 getRecommendCarouselPartnership의 partnerProfileUrl은 null이다")
	void getRecommendCarouselPartnership_NoPartner_ReturnsNullProfileUrl() {
		// given
		UserPaper up = usablePartnership(100L, 1000L, "안주", OptionType.SERVICE);
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID)).thenReturn(List.of(up.getPaper().getStore()));
		when(paperContentRepository.findTopByStoreIdIn(anyList())).thenReturn(List.of());
		when(goodsRepository.findByContentIdIn(anyList())).thenReturn(List.of());

		// when
		List<StudentResponseDTO.RecommendCarouselDTO> result = studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		assertNull(result.get(0).partnerProfileUrl());
		verify(imageCdnManager, never()).generateUrl(anyString(), any());
	}

	@Test
	@DisplayName("업체의 대표 콘텐츠가 있으면 카테고리와 첫 번째 상품명을 반환한다")
	void getRecommendCarouselPartnership_WithContent_ReturnsCategoryAndFirstBelonging() {
		// given
		Store store = store(500L);
		Paper paper = Paper.builder().id(100L).store(store).build();
		PaperContent content = PaperContent.builder()
			.id(1000L).paper(paper).category("안주").optionType(OptionType.SERVICE).build();
		Goods first = Goods.builder().id(1L).content(content).belonging("감자튀김").build();
		Goods second = Goods.builder().id(2L).content(content).belonging("콜라").build();
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID)).thenReturn(List.of(store));
		when(paperContentRepository.findTopByStoreIdIn(List.of(500L))).thenReturn(List.of(content));
		when(goodsRepository.findByContentIdIn(List.of(1000L))).thenReturn(List.of(first, second));

		// when
		List<StudentResponseDTO.RecommendCarouselDTO> result = studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		assertEquals(1, result.size());
		assertEquals("안주", result.get(0).category());
		assertEquals("감자튀김", result.get(0).belonging());
		assertEquals(500L, result.get(0).storeId());
		assertEquals("store-500", result.get(0).partnerName());
	}

	@Test
	@DisplayName("대표 콘텐츠가 없는 업체도 카테고리와 상품명은 null로 반환한다")
	void getRecommendCarouselPartnership_NoContent_ReturnsNullCategoryAndBelonging() {
		// given
		Store store = store(500L);
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID)).thenReturn(List.of(store));
		when(paperContentRepository.findTopByStoreIdIn(List.of(500L))).thenReturn(List.of());
		when(goodsRepository.findByContentIdIn(List.of())).thenReturn(List.of());

		// when
		List<StudentResponseDTO.RecommendCarouselDTO> result = studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		assertEquals(1, result.size());
		assertNull(result.get(0).category());
		assertNull(result.get(0).belonging());
		assertEquals(500L, result.get(0).storeId());
	}

	@Test
	@DisplayName("한 업체에 콘텐츠가 여러 개면 가장 먼저 조회된(최신) 콘텐츠를 사용한다")
	void getRecommendCarouselPartnership_MultipleContentsPerStore_UsesFirstContent() {
		// given
		Store store = store(500L);
		Paper paper = Paper.builder().id(100L).store(store).build();
		PaperContent latest = PaperContent.builder()
			.id(1002L).paper(paper).category("최신").optionType(OptionType.SERVICE).build();
		PaperContent older = PaperContent.builder()
			.id(1001L).paper(paper).category("이전").optionType(OptionType.SERVICE).build();
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID)).thenReturn(List.of(store));
		when(paperContentRepository.findTopByStoreIdIn(List.of(500L))).thenReturn(List.of(latest, older));
		when(goodsRepository.findByContentIdIn(List.of(1002L))).thenReturn(List.of());

		// when
		List<StudentResponseDTO.RecommendCarouselDTO> result = studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		assertEquals(1, result.size());
		assertEquals("최신", result.get(0).category());
		assertNull(result.get(0).belonging());
	}

	@Test
	@DisplayName("상품이 없는 콘텐츠는 상품명을 null로 반환한다")
	void getRecommendCarouselPartnership_ContentWithoutGoods_ReturnsNullBelonging() {
		// given
		Store store = store(500L);
		Paper paper = Paper.builder().id(100L).store(store).build();
		PaperContent content = PaperContent.builder()
			.id(1000L).paper(paper).category("주류").optionType(OptionType.SERVICE).build();
		when(storeRepository.findDistinctActiveStoresByStudentId(STUDENT_ID)).thenReturn(List.of(store));
		when(paperContentRepository.findTopByStoreIdIn(List.of(500L))).thenReturn(List.of(content));
		when(goodsRepository.findByContentIdIn(List.of(1000L))).thenReturn(List.of());

		// when
		List<StudentResponseDTO.RecommendCarouselDTO> result = studentService.getRecommendCarouselPartnership(STUDENT_ID);

		// then
		assertEquals("주류", result.get(0).category());
		assertNull(result.get(0).belonging());
	}

	// ===== syncUserPapersForStudent =====

	@Test
	@DisplayName("매칭되는 관리자가 없으면 UserPaper를 생성하지 않는다")
	void syncUserPapersForStudent_NoMatchingAdmins_DoesNothing() {
		// 1. Given
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).build();
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));
		when(adminRepository.findMatchingAdmins(any(), any(), any())).thenReturn(List.of());

		// 2. When
		studentService.syncUserPapersForStudent(STUDENT_ID);

		// 3. Then
		verify(paperRepository, never()).findActivePapersByAdminIds(anyList(), any(), any());
		verify(userPaperRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("이미 보유한 제휴는 제외하고 새로운 제휴만 UserPaper로 저장한다")
	void syncUserPapersForStudent_SavesOnlyNewPapers() {
		// 1. Given
		Student student = Student.builder().id(STUDENT_ID).university(University.SSU).build();
		when(studentRepository.findById(STUDENT_ID)).thenReturn(Optional.of(student));

		Admin admin = Admin.builder().id(10L).name("총학생회").isPhoneVerified(false).build();
		when(adminRepository.findMatchingAdmins(any(), any(), any())).thenReturn(List.of(admin));

		Paper paper = Paper.builder().id(100L).admin(admin).build();
		when(paperRepository.findActivePapersByAdminIds(anyList(), any(), any())).thenReturn(List.of(paper));

		PaperContent existingContent = PaperContent.builder().id(1000L).paper(paper).build();
		PaperContent newContent = PaperContent.builder().id(1001L).paper(paper).build();
		when(paperContentRepository.findByPaperIdIn(List.of(100L)))
			.thenReturn(List.of(existingContent, newContent));

		// 이미 100_1000 조합의 UserPaper 보유
		UserPaper existing = UserPaper.builder().paper(paper).paperContent(existingContent).student(student).build();
		when(userPaperRepository.findByStudentId(STUDENT_ID)).thenReturn(List.of(existing));

		// 2. When
		studentService.syncUserPapersForStudent(STUDENT_ID);

		// 3. Then (새로운 1001 콘텐츠만 저장)
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<UserPaper>> captor = ArgumentCaptor.forClass(List.class);
		verify(userPaperRepository, times(1)).saveAll(captor.capture());
		assertEquals(1, captor.getValue().size());
		assertEquals(1001L, captor.getValue().get(0).getPaperContent().getId());
	}

	@Test
	void syncUserPapersForAllStudents_whenOneStudentFails_thenContinuesWithOthers() {
		// given
		Student failing = Student.builder().id(1L).university(University.SSU).build();
		Student next = Student.builder().id(2L).university(University.SSU).build();
		when(studentRepository.findAll()).thenReturn(List.of(failing, next));
		doAnswer(inv -> {
			inv.<Consumer<Object>>getArgument(0).accept(null);
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());
		when(studentRepository.findById(1L)).thenThrow(new RuntimeException("boom"));
		when(studentRepository.findById(2L)).thenReturn(Optional.of(next));

		// when
		studentService.syncUserPapersForAllStudents();

		// then
		verify(transactionTemplate, times(2)).executeWithoutResult(any());
		verify(adminRepository).findMatchingAdmins(next.getUniversity(), next.getDepartment(), next.getMajor());
		verify(entityManager, times(2)).clear();
	}
}
