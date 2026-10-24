package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.deviceToken.repository.DeviceTokenRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

	@InjectMocks
	private WithdrawalServiceImpl withdrawalService;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private DeviceTokenRepository deviceTokenRepository;

	@Mock
	private PaperRepository paperRepository;

	@Mock
	private JwtUtil jwtUtil;

	private static final String AUTHORIZATION = "Bearer access-token";
	private static final String RAW_TOKEN = "access-token";
	private static final Long MEMBER_ID = 7L;

	private void stubTokenExtraction() {
		Claims claims = mock(Claims.class);
		when(claims.get("userId")).thenReturn(MEMBER_ID.intValue());
		when(jwtUtil.getTokenFromHeader(AUTHORIZATION)).thenReturn(RAW_TOKEN);
		when(jwtUtil.validateTokenOnlySignature(RAW_TOKEN)).thenReturn(claims);
	}

	@Test
	@DisplayName("존재하지 않는 회원이 탈퇴를 요청하면 NO_SUCH_MEMBER 예외가 발생한다")
	void withdrawCurrentUser_MemberNotFound_ThrowsException() {
		// 1. Given
		stubTokenExtraction();
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> withdrawalService.withdrawCurrentUser(AUTHORIZATION));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_MEMBER, exception.getCode());
		verify(jwtUtil, never()).blacklistAccess(anyString());
	}

	@Test
	@DisplayName("이미 탈퇴한 회원이 다시 탈퇴를 요청하면 MEMBER_ALREADY_WITHDRAWN 예외가 발생한다")
	void withdrawCurrentUser_AlreadyWithdrawn_ThrowsException() {
		// 1. Given
		stubTokenExtraction();
		Member member = mock(Member.class);
		when(member.isWithdrawn()).thenReturn(true);
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> withdrawalService.withdrawCurrentUser(AUTHORIZATION));

		// 3. Then
		assertEquals(ErrorStatus.MEMBER_ALREADY_WITHDRAWN, exception.getCode());
		verify(member, never()).withdraw();
		verify(memberRepository, never()).save(any());
		verify(deviceTokenRepository, never()).deleteAllByMemberId(anyLong());
		verify(jwtUtil, never()).removeAllRefreshTokens(anyLong());
		verify(paperRepository, never()).deactivateByPartnerWithdrawal(anyLong(), any(), any());
	}

	@Test
	@DisplayName("탈퇴 성공 시 소프트 삭제 처리하고 Refresh 토큰 제거 및 Access 토큰을 블랙리스트에 등록한다")
	void withdrawCurrentUser_Success_SoftDeletesAndRevokesTokens() {
		// 1. Given
		stubTokenExtraction();
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.isWithdrawn()).thenReturn(false);
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		// 2. When
		withdrawalService.withdrawCurrentUser(AUTHORIZATION);

		// 3. Then
		verify(member, times(1)).withdraw();
		verify(memberRepository, times(1)).save(member);
		verify(jwtUtil, times(1)).removeAllRefreshTokens(MEMBER_ID);
		verify(jwtUtil, times(1)).revokeAllAccessTokensSince(MEMBER_ID);
		verify(jwtUtil, times(1)).blacklistAccess(RAW_TOKEN);
	}

	@Test
	@DisplayName("백오피스 강제 탈퇴(withdrawMember(Member)) 시에도 회원 단위 Access 토큰 무효화가 적용된다")
	void withdrawMember_ByMemberEntity_RevokesAllAccessTokens() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.isWithdrawn()).thenReturn(false);

		// 2. When
		withdrawalService.withdrawMember(member);

		// 3. Then
		verify(jwtUtil, times(1)).removeAllRefreshTokens(MEMBER_ID);
		verify(jwtUtil, times(1)).revokeAllAccessTokensSince(MEMBER_ID);
		verify(jwtUtil, never()).blacklistAccess(anyString());
	}

	@Test
	@DisplayName("탈퇴 시 등록된 FCM 디바이스 토큰을 모두 삭제한다")
	void withdrawCurrentUser_Success_ClearsDeviceTokens() {
		// 1. Given
		stubTokenExtraction();
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.isWithdrawn()).thenReturn(false);
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		// 2. When
		withdrawalService.withdrawCurrentUser(AUTHORIZATION);

		// 3. Then
		verify(deviceTokenRepository, times(1)).deleteAllByMemberId(MEMBER_ID);
	}

	@Test
	@DisplayName("제휴업체가 탈퇴하면 ACTIVE/SUSPEND 제휴를 INACTIVE로 전환한다")
	void withdrawMember_Partner_DeactivatesPapers() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.isWithdrawn()).thenReturn(false);
		when(member.getRole()).thenReturn(UserRole.PARTNER);

		// 2. When
		withdrawalService.withdrawMember(member);

		// 3. Then
		verify(paperRepository, times(1)).deactivateByPartnerWithdrawal(
			MEMBER_ID,
			List.of(ActivationStatus.ACTIVE, ActivationStatus.SUSPEND),
			ActivationStatus.INACTIVE);
	}

	@Test
	@DisplayName("제휴업체가 아닌 회원이 탈퇴하면 제휴 상태를 변경하지 않는다")
	void withdrawMember_NonPartner_DoesNotTouchPapers() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.isWithdrawn()).thenReturn(false);
		when(member.getRole()).thenReturn(UserRole.ADMIN);

		// 2. When
		withdrawalService.withdrawMember(member);

		// 3. Then
		verify(paperRepository, never()).deactivateByPartnerWithdrawal(anyLong(), any(), any());
	}

	@Test
	@DisplayName("제휴업체를 복구하면 탈퇴 시 비활성화된 제휴를 원래 상태로 되돌린다")
	void restoreMember_Partner_RestoresPapers() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(UserRole.PARTNER);

		// 2. When
		withdrawalService.restoreMember(member);

		// 3. Then
		verify(member, times(1)).restore();
		verify(memberRepository, times(1)).save(member);
		verify(paperRepository, times(1)).restoreByPartnerRestoration(MEMBER_ID);
	}

	@Test
	@DisplayName("제휴업체가 아닌 회원을 복구하면 제휴 상태를 변경하지 않는다")
	void restoreMember_NonPartner_DoesNotTouchPapers() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getRole()).thenReturn(UserRole.STUDENT);

		// 2. When
		withdrawalService.restoreMember(member);

		// 3. Then
		verify(member, times(1)).restore();
		verify(paperRepository, never()).restoreByPartnerRestoration(anyLong());
	}
}
