package com.assu.server.domain.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.admin.dto.AdminResponseDTO;
import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

	@InjectMocks
	private AdminServiceImpl adminService;

	@Mock
	private AdminRepository adminRepository;

	@Mock
	private PartnerRepository partnerRepository;

	@Test
	@DisplayName("학적 정보로 매칭되는 관리자 조회 시 리포지토리에 그대로 위임한다")
	void findMatchingAdmins_DelegatesToRepository() {
		// 1. Given
		Admin matchingAdmin = Admin.builder().id(77L).isPhoneVerified(false).build();
		when(adminRepository.findMatchingAdmins(University.SSU, Department.IT, Major.COMPUTER_SCIENCE))
			.thenReturn(List.of(matchingAdmin));

		// 2. When
		List<Admin> result = adminService.findMatchingAdmins(University.SSU, Department.IT, Major.COMPUTER_SCIENCE);

		// 3. Then
		assertEquals(1, result.size());
		assertEquals(77L, result.get(0).getId());
		verify(adminRepository, times(1)).findMatchingAdmins(University.SSU, Department.IT, Major.COMPUTER_SCIENCE);
	}

	@Test
	@DisplayName("존재하지 않는 관리자 ID로 제휴업체 추천을 요청하면 NO_SUCH_ADMIN 예외가 발생한다")
	void suggestRandomPartner_AdminNotFound_ThrowsException() {
		// 1. Given
		Long adminId = 1L;
		when(adminRepository.findById(adminId)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> adminService.suggestRandomPartner(adminId));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_ADMIN, exception.getCode());
		verify(partnerRepository, never()).countUnpartneredActiveByAdmin(any(), any());
	}

	@Test
	@DisplayName("추천 가능한 미제휴 활성 업체가 없으면 NO_AVAILABLE_PARTNER 예외가 발생한다")
	void suggestRandomPartner_NoAvailablePartner_ThrowsException() {
		// 1. Given
		Long adminId = 1L;
		Admin admin = Admin.builder().id(adminId).isPhoneVerified(false).build();
		when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
		when(partnerRepository.countUnpartneredActiveByAdmin(adminId, ActivationStatus.ACTIVE)).thenReturn(0L);

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> adminService.suggestRandomPartner(adminId));

		// 3. Then
		assertEquals(ErrorStatus.NO_AVAILABLE_PARTNER, exception.getCode());
		verify(partnerRepository, never()).findUnpartneredActiveByAdminWithOffset(any(), any(), any());
	}

	@Test
	@DisplayName("카운트 이후 데이터가 사라져 오프셋 조회 결과가 비어있으면 NO_AVAILABLE_PARTNER 예외가 발생한다")
	void suggestRandomPartner_EmptyOffsetResult_ThrowsException() {
		// 1. Given (count 시점에는 1건이었으나 조회 시점에 사라진 동시성 상황)
		Long adminId = 1L;
		Admin admin = Admin.builder().id(adminId).isPhoneVerified(false).build();
		when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
		when(partnerRepository.countUnpartneredActiveByAdmin(adminId, ActivationStatus.ACTIVE)).thenReturn(1L);
		when(partnerRepository.findUnpartneredActiveByAdminWithOffset(eq(adminId), eq(ActivationStatus.ACTIVE), any(Pageable.class)))
			.thenReturn(List.of());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> adminService.suggestRandomPartner(adminId));

		// 3. Then
		assertEquals(ErrorStatus.NO_AVAILABLE_PARTNER, exception.getCode());
	}

	@Test
	@DisplayName("추천 가능한 업체가 있으면 업체 정보가 담긴 DTO를 반환한다")
	void suggestRandomPartner_Success_ReturnsPartnerInfo() {
		// 1. Given
		Long adminId = 1L;
		Admin admin = Admin.builder().id(adminId).isPhoneVerified(false).build();

		Member partnerMember = mock(Member.class);
		when(partnerMember.getProfileUrl()).thenReturn("https://assu-bucket.s3.amazonaws.com/profile.png");

		Partner partner = Partner.builder()
			.id(101L)
			.member(partnerMember)
			.name("역전할머니 맥주 숭실대점")
			.isPhoneVerified(false)
			.address("서울특별시 동작구")
			.detailAddress("2층 201호")
			.phoneNum("02-123-4567")
			.build();

		when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
		when(partnerRepository.countUnpartneredActiveByAdmin(adminId, ActivationStatus.ACTIVE)).thenReturn(1L);
		when(partnerRepository.findUnpartneredActiveByAdminWithOffset(eq(adminId), eq(ActivationStatus.ACTIVE), any(Pageable.class)))
			.thenReturn(List.of(partner));

		// 2. When
		AdminResponseDTO response = adminService.suggestRandomPartner(adminId);

		// 3. Then
		assertEquals(101L, response.partnerId());
		assertEquals("역전할머니 맥주 숭실대점", response.partnerName());
		assertEquals("서울특별시 동작구", response.partnerAddress());
		assertEquals("2층 201호", response.partnerDetailAddress());
		assertEquals("https://assu-bucket.s3.amazonaws.com/profile.png", response.partnerUrl());
		assertEquals("02-123-4567", response.partnerPhone());
	}

	@Test
	@DisplayName("추천된 업체에 연결된 멤버가 없으면 프로필 URL은 null로 반환된다")
	void suggestRandomPartner_PartnerWithoutMember_ReturnsNullUrl() {
		// 1. Given
		Long adminId = 1L;
		Admin admin = Admin.builder().id(adminId).isPhoneVerified(false).build();

		Partner partner = Partner.builder()
			.id(101L)
			.member(null)
			.name("역전할머니 맥주 숭실대점")
			.isPhoneVerified(false)
			.address("서울특별시 동작구")
			.build();

		when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));
		when(partnerRepository.countUnpartneredActiveByAdmin(adminId, ActivationStatus.ACTIVE)).thenReturn(1L);
		when(partnerRepository.findUnpartneredActiveByAdminWithOffset(eq(adminId), eq(ActivationStatus.ACTIVE), any(Pageable.class)))
			.thenReturn(List.of(partner));

		// 2. When
		AdminResponseDTO response = adminService.suggestRandomPartner(adminId);

		// 3. Then
		assertNull(response.partnerUrl());
		assertEquals(101L, response.partnerId());
	}
}
