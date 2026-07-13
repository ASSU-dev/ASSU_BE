package com.assu.server.domain.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceImplTest {

	@InjectMocks
	private WithdrawalServiceImpl withdrawalService;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private JwtUtil jwtUtil;

	private static final String AUTHORIZATION = "Bearer access-token";
	private static final String RAW_TOKEN = "access-token";
	private static final Long MEMBER_ID = 7L;

	@BeforeEach
	void setUpToken() {
		Claims claims = mock(Claims.class);
		when(claims.get("userId")).thenReturn(MEMBER_ID.intValue());
		when(jwtUtil.getTokenFromHeader(AUTHORIZATION)).thenReturn(RAW_TOKEN);
		when(jwtUtil.validateTokenOnlySignature(RAW_TOKEN)).thenReturn(claims);
	}

	@Test
	@DisplayName("존재하지 않는 회원이 탈퇴를 요청하면 NO_SUCH_MEMBER 예외가 발생한다")
	void withdrawCurrentUser_MemberNotFound_ThrowsException() {
		// 1. Given
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
		Member member = mock(Member.class);
		when(member.getDeletedAt()).thenReturn(LocalDateTime.now().minusDays(1));
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> withdrawalService.withdrawCurrentUser(AUTHORIZATION));

		// 3. Then
		assertEquals(ErrorStatus.MEMBER_ALREADY_WITHDRAWN, exception.getCode());
		verify(memberRepository, never()).save(any());
		verify(jwtUtil, never()).removeAllRefreshTokens(anyLong());
	}

	@Test
	@DisplayName("탈퇴 성공 시 소프트 삭제 처리하고 Refresh 토큰 제거 및 Access 토큰을 블랙리스트에 등록한다")
	void withdrawCurrentUser_Success_SoftDeletesAndRevokesTokens() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getDeletedAt()).thenReturn(null);
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		// 2. When
		withdrawalService.withdrawCurrentUser(AUTHORIZATION);

		// 3. Then
		verify(member, times(1)).setDeletedAt(any(LocalDateTime.class));
		verify(memberRepository, times(1)).save(member);
		verify(jwtUtil, times(1)).removeAllRefreshTokens(MEMBER_ID);
		verify(jwtUtil, times(1)).blacklistAccess(RAW_TOKEN);
	}
}
