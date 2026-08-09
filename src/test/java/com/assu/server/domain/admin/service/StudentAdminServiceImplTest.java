package com.assu.server.domain.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.admin.dto.StoreUsageWithPaper;
import com.assu.server.domain.admin.dto.StudentAdminResponseDTO;
import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.admin.repository.StudentAdminRepository;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;

@ExtendWith(MockitoExtension.class)
class StudentAdminServiceImplTest {

	@InjectMocks
	private StudentAdminServiceImpl studentAdminService;

	@Mock
	private StudentAdminRepository studentAdminRepository;

	@Mock
	private AdminRepository adminRepository;

	@Mock
	private PaperRepository paperRepository;

	private static final Long ADMIN_ID = 10L;

	private Admin givenAdmin() {
		Admin admin = Admin.builder()
			.id(ADMIN_ID)
			.name("총학생회")
			.isPhoneVerified(false)
			.build();
		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
		return admin;
	}

	// ===== getCountAdminAuth =====

	@Test
	@DisplayName("존재하지 않는 관리자 ID로 누적 학생 수를 조회하면 NO_SUCH_ADMIN 예외가 발생한다")
	void getCountAdminAuth_AdminNotFound_ThrowsException() {
		// 1. Given
		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> studentAdminService.getCountAdminAuth(ADMIN_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_ADMIN, exception.getCode());
		verify(studentAdminRepository, never()).countAllByAdminId(anyLong());
	}

	@Test
	@DisplayName("누적 가입 학생 수와 관리자 정보를 함께 반환한다")
	void getCountAdminAuth_Success() {
		// 1. Given
		givenAdmin();
		when(studentAdminRepository.countAllByAdminId(ADMIN_ID)).thenReturn(42L);

		// 2. When
		StudentAdminResponseDTO.CountAdminAuthResponseDTO response =
			studentAdminService.getCountAdminAuth(ADMIN_ID);

		// 3. Then
		assertEquals(42L, response.studentCount());
		assertEquals(ADMIN_ID, response.adminId());
		assertEquals("총학생회", response.adminName());
	}

	// ===== getNewStudentCountAdmin =====

	@Test
	@DisplayName("신규 학생 수 조회 시 오늘 0시부터 내일 0시까지의 범위로 카운트한다")
	void getNewStudentCountAdmin_Success_UsesTodayRange() {
		// 1. Given
		givenAdmin();
		when(studentAdminRepository.countTodayUsersByAdmin(eq(ADMIN_ID), any(), any())).thenReturn(3L);

		// 2. When
		StudentAdminResponseDTO.NewCountAdminResponseDTO response =
			studentAdminService.getNewStudentCountAdmin(ADMIN_ID);

		// 3. Then
		assertEquals(3L, response.newStudentCount());
		assertEquals(ADMIN_ID, response.adminId());
		assertEquals("총학생회", response.adminName());

		// 조회 범위가 [오늘 0시, 내일 0시)인지 검증
		ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(studentAdminRepository).countTodayUsersByAdmin(eq(ADMIN_ID), startCaptor.capture(), endCaptor.capture());

		LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
		assertEquals(startOfDay, startCaptor.getValue());
		assertEquals(startOfDay.plusDays(1), endCaptor.getValue());
	}

	@Test
	@DisplayName("존재하지 않는 관리자 ID로 신규 학생 수를 조회하면 NO_SUCH_ADMIN 예외가 발생한다")
	void getNewStudentCountAdmin_AdminNotFound_ThrowsException() {
		// 1. Given
		when(adminRepository.findById(ADMIN_ID)).thenReturn(Optional.empty());

		// 2. When & Then
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> studentAdminService.getNewStudentCountAdmin(ADMIN_ID));
		assertEquals(ErrorStatus.NO_SUCH_ADMIN, exception.getCode());
	}

	// ===== getCountUsagePerson =====

	@Test
	@DisplayName("오늘 제휴를 이용한 사용자 수와 관리자 정보를 함께 반환한다")
	void getCountUsagePerson_Success() {
		// 1. Given
		givenAdmin();
		when(studentAdminRepository.countTodayUsersByAdmin(eq(ADMIN_ID), any(), any())).thenReturn(7L);

		// 2. When
		StudentAdminResponseDTO.CountUsagePersonResponseDTO response =
			studentAdminService.getCountUsagePerson(ADMIN_ID);

		// 3. Then
		assertEquals(7L, response.usagePersonCount());
		assertEquals(ADMIN_ID, response.adminId());
		assertEquals("총학생회", response.adminName());
	}

	// ===== getCountUsage =====

	@Test
	@DisplayName("이용 내역이 없으면 NO_USAGE_DATA 예외가 발생한다")
	void getCountUsage_NoUsageData_ThrowsException() {
		// 1. Given
		givenAdmin();
		when(studentAdminRepository.findUsageByStoreWithPaper(ADMIN_ID)).thenReturn(List.of());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> studentAdminService.getCountUsage(ADMIN_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_USAGE_DATA, exception.getCode());
		verify(paperRepository, never()).findById(anyLong());
	}

	@Test
	@DisplayName("1위 업체의 paper가 존재하지 않으면 NO_PAPER_FOR_STORE 예외가 발생한다")
	void getCountUsage_PaperNotFound_ThrowsException() {
		// 1. Given
		givenAdmin();
		StoreUsageWithPaper top = new StoreUsageWithPaper(500L, 200L, "역전할머니 맥주", 30L);
		when(studentAdminRepository.findUsageByStoreWithPaper(ADMIN_ID)).thenReturn(List.of(top));
		when(paperRepository.findById(500L)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> studentAdminService.getCountUsage(ADMIN_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_PAPER_FOR_STORE, exception.getCode());
	}

	@Test
	@DisplayName("누적 이용 1위 업체의 정보와 이용 횟수를 반환한다")
	void getCountUsage_Success_ReturnsTopStore() {
		// 1. Given (repository 쿼리가 이용 횟수 내림차순 정렬을 보장하므로 첫 번째 행이 1위)
		givenAdmin();
		StoreUsageWithPaper top = new StoreUsageWithPaper(500L, 200L, "역전할머니 맥주", 30L);
		StoreUsageWithPaper second = new StoreUsageWithPaper(501L, 201L, "숭실분식", 10L);
		when(studentAdminRepository.findUsageByStoreWithPaper(ADMIN_ID)).thenReturn(List.of(top, second));

		Store store = Store.builder().id(200L).name("역전할머니 맥주").build();
		Paper paper = Paper.builder().id(500L).store(store).build();
		when(paperRepository.findById(500L)).thenReturn(Optional.of(paper));

		// 2. When
		StudentAdminResponseDTO.CountUsageResponseDTO response = studentAdminService.getCountUsage(ADMIN_ID);

		// 3. Then
		assertEquals(30L, response.usageCount());
		assertEquals(ADMIN_ID, response.adminId());
		assertEquals("총학생회", response.adminName());
		assertEquals(200L, response.storeId());
		assertEquals("역전할머니 맥주", response.storeName());
	}

	// ===== getCountUsageList =====

	@Test
	@DisplayName("이용 내역이 없으면 예외 대신 빈 리스트를 정상 반환한다")
	void getCountUsageList_NoUsageData_ReturnsEmptyList() {
		// 1. Given (getCountUsage와 달리 빈 데이터를 예외로 처리하지 않음)
		givenAdmin();
		when(studentAdminRepository.findUsageByStoreWithPaper(ADMIN_ID)).thenReturn(List.of());

		// 2. When
		StudentAdminResponseDTO.CountUsageListResponseDTO response =
			studentAdminService.getCountUsageList(ADMIN_ID);

		// 3. Then
		assertTrue(response.items().isEmpty());
		verify(paperRepository, never()).findAllById(any());
	}

	@Test
	@DisplayName("이용 내역의 paperId가 일괄 조회 결과에 없으면 NO_PAPER_FOR_STORE 예외가 발생한다")
	void getCountUsageList_PaperMissingInBatch_ThrowsException() {
		// 1. Given (500L은 조회되지만 501L은 조회되지 않는 상황)
		givenAdmin();
		StoreUsageWithPaper first = new StoreUsageWithPaper(500L, 200L, "역전할머니 맥주", 30L);
		StoreUsageWithPaper second = new StoreUsageWithPaper(501L, 201L, "숭실분식", 10L);
		when(studentAdminRepository.findUsageByStoreWithPaper(ADMIN_ID)).thenReturn(List.of(first, second));

		Store store = Store.builder().id(200L).name("역전할머니 맥주").build();
		Paper paper = Paper.builder().id(500L).store(store).build();
		when(paperRepository.findAllById(List.of(500L, 501L))).thenReturn(List.of(paper));

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> studentAdminService.getCountUsageList(ADMIN_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_PAPER_FOR_STORE, exception.getCode());
	}

	@Test
	@DisplayName("업체별 누적 이용 내역을 이용 횟수 내림차순 그대로 리스트로 반환한다")
	void getCountUsageList_Success_ReturnsAllStores() {
		// 1. Given
		givenAdmin();
		StoreUsageWithPaper first = new StoreUsageWithPaper(500L, 200L, "역전할머니 맥주", 30L);
		StoreUsageWithPaper second = new StoreUsageWithPaper(501L, 201L, "숭실분식", 10L);
		when(studentAdminRepository.findUsageByStoreWithPaper(ADMIN_ID)).thenReturn(List.of(first, second));

		Store store1 = Store.builder().id(200L).name("역전할머니 맥주").build();
		Store store2 = Store.builder().id(201L).name("숭실분식").build();
		Paper paper1 = Paper.builder().id(500L).store(store1).build();
		Paper paper2 = Paper.builder().id(501L).store(store2).build();
		when(paperRepository.findAllById(List.of(500L, 501L))).thenReturn(List.of(paper1, paper2));

		// 2. When
		StudentAdminResponseDTO.CountUsageListResponseDTO response =
			studentAdminService.getCountUsageList(ADMIN_ID);

		// 3. Then (repository 정렬 순서가 그대로 유지되는지 확인)
		assertEquals(2, response.items().size());

		StudentAdminResponseDTO.CountUsageResponseDTO firstItem = response.items().get(0);
		assertEquals(200L, firstItem.storeId());
		assertEquals("역전할머니 맥주", firstItem.storeName());
		assertEquals(30L, firstItem.usageCount());

		StudentAdminResponseDTO.CountUsageResponseDTO secondItem = response.items().get(1);
		assertEquals(201L, secondItem.storeId());
		assertEquals("숭실분식", secondItem.storeName());
		assertEquals(10L, secondItem.usageCount());
	}
}
