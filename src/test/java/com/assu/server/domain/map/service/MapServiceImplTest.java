package com.assu.server.domain.map.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.map.dto.MapRequestDTO;
import com.assu.server.domain.map.dto.PartnerMapResponseDTO;
import com.assu.server.domain.map.dto.StoreMapResponseDTO;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.UserPaper;
import com.assu.server.domain.student.repository.UserPaperRepository;
import com.assu.server.infra.s3.AmazonS3Manager;

@ExtendWith(MockitoExtension.class)
class MapServiceImplTest {

	@InjectMocks
	private MapServiceImpl mapService;

	@Mock
	private AdminRepository adminRepository;

	@Mock
	private PartnerRepository partnerRepository;

	@Mock
	private StoreRepository storeRepository;

	@Mock
	private PaperContentRepository paperContentRepository;

	@Mock
	private PaperRepository paperRepository;

	@Mock
	private AmazonS3Manager amazonS3Manager;

	@Mock
	private UserPaperRepository userPaperRepository;

	private static final Long STUDENT_ID = 1L;

	private MapRequestDTO viewport() {
		return new MapRequestDTO(126.95, 37.51, 126.97, 37.51, 126.97, 37.49, 126.95, 37.49);
	}

	private Admin admin(Long id, String name) {
		return Admin.builder().id(id).name(name).isPhoneVerified(false).build();
	}

	private Store store(Long id, String name) {
		return Store.builder().id(id).name(name).address("서울특별시 동작구")
			.rate(4).latitude(37.50).longitude(126.96).build();
	}

	private UserPaper userPaper(Paper paper) {
		return UserPaper.builder().paper(paper).build();
	}

	// ===== getStores =====

	@Test
	@DisplayName("뷰포트 내 가게가 없으면 빈 리스트를 반환하고 UserPaper 조회를 하지 않는다")
	void getStores_EmptyViewport_ReturnsEmptyList() {
		// 1. Given
		when(storeRepository.findAllWithinViewportWithPartner(anyString())).thenReturn(List.of());

		// 2. When
		List<StoreMapResponseDTO> result = mapService.getStores(viewport(), STUDENT_ID, null, null);

		// 3. Then
		assertTrue(result.isEmpty());
		verify(userPaperRepository, never()).findActivePartnershipsByStudentId(any(), any(), any());
	}

	@Test
	@DisplayName("활성 제휴가 없는 학생이 주변 가게를 조회하면 빈 리스트를 반환한다")
	void getStores_NoActivePartnership_ReturnsEmptyList() {
		// 1. Given
		when(storeRepository.findAllWithinViewportWithPartner(anyString())).thenReturn(List.of(store(500L, "숭실카페")));
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null)).thenReturn(List.of());

		// 2. When
		List<StoreMapResponseDTO> result = mapService.getStores(viewport(), STUDENT_ID, null, null);

		// 3. Then
		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("adminId 필터링 시 해당 학생회 제휴가 있는 가게만 반환한다")
	void getStores_WithAdminIdFilter_ReturnsOnlyMatchingAdminStores() {
		// 1. Given - A학생회(가게1), B학생회(가게2) 각각 제휴
		Store store1 = store(1L, "가게1");
		Store store2 = store(2L, "가게2");
		Admin adminA = admin(10L, "A학생회");
		Admin adminB = admin(20L, "B학생회");
		Paper paperA = Paper.builder().id(100L).store(store1).admin(adminA).build();
		Paper paperB = Paper.builder().id(200L).store(store2).admin(adminB).build();

		when(storeRepository.findAllWithinViewportWithPartner(anyString())).thenReturn(List.of(store1, store2));
		// DB가 adminId=10으로 필터링해서 paperA만 반환
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, 10L))
			.thenReturn(List.of(userPaper(paperA)));

		PaperContent contentA = PaperContent.builder().id(1000L).paper(paperA).note("A학생회 혜택").build();
		when(paperContentRepository.findByPaperIdIn(anyList())).thenReturn(List.of(contentA));

		// 2. When - adminId=10(A학생회)으로 필터링
		List<StoreMapResponseDTO> result = mapService.getStores(viewport(), STUDENT_ID, null, 10L);

		// 3. Then - 가게1만 반환
		assertEquals(1, result.size());
		assertEquals(1L, result.get(0).storeId());
		assertEquals("A학생회", result.get(0).partnerships().get(0).adminName());
		verify(userPaperRepository).findActivePartnershipsByStudentId(STUDENT_ID, null, 10L);
	}

	@Test
	@DisplayName("여러 학생회가 같은 가게와 제휴하면 학생회별로 혜택이 그룹화된다")
	void getStores_MultipleAdmins_GroupsBenefitsByAdmin() {
		// 1. Given - A학생회, B학생회 모두 가게1과 제휴
		Store store1 = store(1L, "가게1");
		Admin adminA = admin(10L, "A학생회");
		Admin adminB = admin(20L, "B학생회");
		Paper paperA = Paper.builder().id(100L).store(store1).admin(adminA).build();
		Paper paperB = Paper.builder().id(200L).store(store1).admin(adminB).build();

		when(storeRepository.findAllWithinViewportWithPartner(anyString())).thenReturn(List.of(store1));
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null))
			.thenReturn(List.of(userPaper(paperA), userPaper(paperB)));

		PaperContent contentA = PaperContent.builder().id(1000L).paper(paperA).note("A혜택").build();
		PaperContent contentB = PaperContent.builder().id(2000L).paper(paperB).note("B혜택").build();
		when(paperContentRepository.findByPaperIdIn(anyList())).thenReturn(List.of(contentA, contentB));

		// 2. When
		List<StoreMapResponseDTO> result = mapService.getStores(viewport(), STUDENT_ID, null, null);

		// 3. Then - 가게1에 2개의 학생회 혜택
		assertEquals(1, result.size());
		assertEquals(2, result.get(0).partnerships().size());
	}

	// ===== getPartners =====

	@Test
	@DisplayName("뷰포트 내 제휴업체가 없으면 빈 리스트를 반환한다")
	void getPartners_EmptyViewport_ReturnsEmptyList() {
		// 1. Given
		when(partnerRepository.findAllWithinViewportWithMember(anyString())).thenReturn(List.of());

		// 2. When
		List<PartnerMapResponseDTO> result = mapService.getPartners(viewport(), STUDENT_ID);

		// 3. Then
		assertTrue(result.isEmpty());
		verify(paperRepository, never()).findByAdminIdAndPartnerIdInAndIsActivated(any(), anyList(), any());
	}

	@Test
	@DisplayName("활성 제휴가 없는 학생이 매장을 검색하면 빈 리스트를 반환한다")
	void searchStores_NoActivePartnership_ReturnsEmptyList() {
		// 1. Given
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null)).thenReturn(List.of());

		// 2. When
		List<StoreMapResponseDTO> result = mapService.searchStores("맥주", STUDENT_ID);

		// 3. Then
		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("검색 키워드와 일치하는 매장이 없으면 빈 리스트를 반환한다")
	void searchStores_NoMatchingKeyword_ReturnsEmptyList() {
		// 1. Given
		Store store = store(500L, "숭실마트");
		Paper paper = Paper.builder().id(100L).store(store).admin(admin(10L, "총학생회")).build();
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null))
			.thenReturn(List.of(userPaper(paper)));

		// 2. When
		List<StoreMapResponseDTO> result = mapService.searchStores("맥주", STUDENT_ID);

		// 3. Then
		assertTrue(result.isEmpty());
	}

	@Test
	@DisplayName("키워드는 공백 무시·대소문자 무시로 매장명과 부분 일치하며 note가 있으면 note가 혜택으로 노출된다")
	void searchStores_MatchingKeyword_UsesNoteAsBenefit() {
		// 1. Given ("역전 할머니 맥주" 매장을 "할머니맥주"로 검색)
		Store store = store(500L, "역전 할머니 맥주");
		Paper paper = Paper.builder().id(100L).store(store).admin(admin(10L, "총학생회")).build();
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null))
			.thenReturn(List.of(userPaper(paper)));

		PaperContent content = PaperContent.builder()
			.id(1000L).paper(paper).note("생맥주 500cc 1잔 무료").build();
		when(paperContentRepository.findByPaperIdIn(List.of(100L))).thenReturn(List.of(content));

		// 2. When
		List<StoreMapResponseDTO> result = mapService.searchStores("할머니맥주", STUDENT_ID);

		// 3. Then
		assertEquals(1, result.size());
		assertEquals(500L, result.get(0).storeId());
		assertEquals(1, result.get(0).partnerships().size());

		StoreMapResponseDTO.PartnershipInfo partnership = result.get(0).partnerships().get(0);
		assertEquals(10L, partnership.adminId());
		assertEquals("총학생회", partnership.adminName());
		assertEquals(List.of("생맥주 500cc 1잔 무료"), partnership.benefits());
	}

	@Test
	@DisplayName("note가 없으면 옵션·기준 타입 조합으로 혜택 문구를 생성한다 (금액 기준 할인)")
	void searchStores_NoNote_GeneratesDiscountBenefitText() {
		// 1. Given
		Store store = store(500L, "숭실분식");
		Paper paper = Paper.builder().id(100L).store(store).admin(admin(10L, "총학생회")).build();
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null))
			.thenReturn(List.of(userPaper(paper)));

		PaperContent content = PaperContent.builder()
			.id(1000L).paper(paper)
			.optionType(OptionType.DISCOUNT).criterionType(CriterionType.PRICE)
			.cost(10000L).discount(10L)
			.build();
		when(paperContentRepository.findByPaperIdIn(List.of(100L))).thenReturn(List.of(content));

		// 2. When
		List<StoreMapResponseDTO> result = mapService.searchStores("숭실분식", STUDENT_ID);

		// 3. Then
		assertEquals(1, result.size());
		assertEquals(List.of("10000원 이상 구매 시 10% 할인"),
			result.get(0).partnerships().get(0).benefits());
	}

	@Test
	@DisplayName("note가 없으면 옵션·기준 타입 조합으로 혜택 문구를 생성한다 (인원 기준 서비스 증정)")
	void searchStores_NoNote_GeneratesServiceBenefitText() {
		// 1. Given
		Store store = store(500L, "숭실분식");
		Paper paper = Paper.builder().id(100L).store(store).admin(admin(10L, "총학생회")).build();
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null))
			.thenReturn(List.of(userPaper(paper)));

		PaperContent content = PaperContent.builder()
			.id(1000L).paper(paper)
			.optionType(OptionType.SERVICE).criterionType(CriterionType.HEADCOUNT)
			.people(4).category("떡볶이")
			.build();
		when(paperContentRepository.findByPaperIdIn(List.of(100L))).thenReturn(List.of(content));

		// 2. When
		List<StoreMapResponseDTO> result = mapService.searchStores("숭실분식", STUDENT_ID);

		// 3. Then
		assertEquals(1, result.size());
		assertEquals(List.of("4명 이상 방문 시 떡볶이 증정"),
			result.get(0).partnerships().get(0).benefits());
	}

	@Test
	@DisplayName("제휴 내용(PaperContent)이 하나도 없는 매장은 검색 결과에서 제외된다")
	void searchStores_NoPaperContent_ExcludesStore() {
		// 1. Given
		Store store = store(500L, "숭실분식");
		Paper paper = Paper.builder().id(100L).store(store).admin(admin(10L, "총학생회")).build();
		when(userPaperRepository.findActivePartnershipsByStudentId(STUDENT_ID, null, null))
			.thenReturn(List.of(userPaper(paper)));
		when(paperContentRepository.findByPaperIdIn(List.of(100L))).thenReturn(List.of());

		// 2. When
		List<StoreMapResponseDTO> result = mapService.searchStores("숭실분식", STUDENT_ID);

		// 3. Then (혜택이 없는 제휴는 노출되지 않음)
		assertTrue(result.isEmpty());
	}
}
