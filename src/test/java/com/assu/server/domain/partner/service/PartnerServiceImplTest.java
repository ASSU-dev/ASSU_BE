package com.assu.server.domain.partner.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partner.dto.PartnerResponseDTO;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;

@ExtendWith(MockitoExtension.class)
class PartnerServiceImplTest {

	@InjectMocks
	private PartnerServiceImpl partnerService;

	@Mock
	private PartnerRepository partnerRepository;

	@Mock
	private AdminRepository adminRepository;

	private static final Long PARTNER_ID = 20L;

	private Partner givenPartner() {
		Partner partner = Partner.builder().id(PARTNER_ID).name("역전할머니 맥주").isPhoneVerified(false).build();
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
		return partner;
	}

	@Test
	@DisplayName("존재하지 않는 파트너가 관리자 추천을 요청하면 NO_SUCH_PARTNER 예외가 발생한다")
	void getRandomAdmin_PartnerNotFound_ThrowsException() {
		// 1. Given
		when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> partnerService.getRandomAdmin(PARTNER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_PARTNER, exception.getCode());
		verify(adminRepository, never()).countPartner(anyLong());
	}

	@Test
	@DisplayName("추천 가능한 미제휴 관리자가 없으면 NO_SUCH_ADMIN 예외가 발생한다")
	void getRandomAdmin_NoAvailableAdmin_ThrowsException() {
		// 1. Given
		givenPartner();
		when(adminRepository.countPartner(PARTNER_ID)).thenReturn(0L);

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> partnerService.getRandomAdmin(PARTNER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_ADMIN, exception.getCode());
		verify(adminRepository, never()).findPartnerWithOffset(anyLong(), any());
	}

	@Test
	@DisplayName("추천 가능한 관리자가 1명이면 offset 0, limit 1로 조회하여 반환한다")
	void getRandomAdmin_SingleAdmin_ReturnsOne() {
		// 1. Given
		givenPartner();
		when(adminRepository.countPartner(PARTNER_ID)).thenReturn(1L);

		Member adminMember = mock(Member.class);
		when(adminMember.getProfileUrl()).thenReturn("admins/10/profile.png");
		Admin admin = Admin.builder()
			.id(10L).member(adminMember).name("총학생회").isPhoneVerified(false)
			.officeAddress("서울특별시 동작구").detailAddress("학생회관 201호").phoneNum("01012345678")
			.build();
		when(adminRepository.findPartnerWithOffset(eq(PARTNER_ID), any(Pageable.class)))
			.thenReturn(List.of(admin));

		// 2. When
		PartnerResponseDTO response = partnerService.getRandomAdmin(PARTNER_ID);

		// 3. Then
		assertEquals(1, response.admins().size());
		PartnerResponseDTO.AdminLiteDTO adminDto = response.admins().get(0);
		assertEquals(10L, adminDto.adminId());
		assertEquals("총학생회", adminDto.adminName());
		assertEquals("서울특별시 동작구", adminDto.adminAddress());
		assertEquals("학생회관 201호", adminDto.adminDetailAddress());
		assertEquals("admins/10/profile.png", adminDto.adminUrl());
		assertEquals("01012345678", adminDto.adminPhone());

		// 1명뿐이므로 offset 0, limit 1 고정
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(adminRepository).findPartnerWithOffset(eq(PARTNER_ID), pageableCaptor.capture());
		assertEquals(0, pageableCaptor.getValue().getPageNumber());
		assertEquals(1, pageableCaptor.getValue().getPageSize());
	}

	@Test
	@DisplayName("추천 가능한 관리자가 2명이면 최대 2명까지 반환한다")
	void getRandomAdmin_TwoAdmins_ReturnsBoth() {
		// 1. Given
		givenPartner();
		when(adminRepository.countPartner(PARTNER_ID)).thenReturn(2L);

		Admin admin1 = Admin.builder().id(10L).name("총학생회").isPhoneVerified(false).build();
		Admin admin2 = Admin.builder().id(11L).name("IT대 학생회").isPhoneVerified(false).build();
		when(adminRepository.findPartnerWithOffset(eq(PARTNER_ID), any(Pageable.class)))
			.thenReturn(List.of(admin1, admin2));

		// 2. When
		PartnerResponseDTO response = partnerService.getRandomAdmin(PARTNER_ID);

		// 3. Then (member가 없는 관리자는 adminUrl이 null)
		assertEquals(2, response.admins().size());
		assertNull(response.admins().get(0).adminUrl());

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(adminRepository).findPartnerWithOffset(eq(PARTNER_ID), pageableCaptor.capture());
		assertEquals(2, pageableCaptor.getValue().getPageSize());
	}
}
