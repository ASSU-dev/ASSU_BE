package com.assu.server.domain.deviceToken.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

import com.assu.server.domain.deviceToken.entity.DeviceToken;
import com.assu.server.domain.deviceToken.repository.DeviceTokenRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.global.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceImplTest {

	@InjectMocks
	private DeviceTokenServiceImpl deviceTokenService;

	@Mock
	private DeviceTokenRepository deviceTokenRepository;

	@Mock
	private MemberRepository memberRepository;

	private static final Long MEMBER_ID = 1L;
	private static final String TOKEN = "fcm-token-abc";

	@Test
	@DisplayName("존재하지 않는 회원이 디바이스 토큰을 등록하면 NO_SUCH_MEMBER 예외가 발생한다")
	void register_MemberNotFound_ThrowsException() {
		// 1. Given
		when(memberRepository.findMemberById(MEMBER_ID)).thenReturn(Optional.empty());

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> deviceTokenService.register(TOKEN, MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_MEMBER, exception.getCode());
		verify(deviceTokenRepository, never()).save(any());
	}

	@Test
	@DisplayName("같은 회원이 같은 토큰을 다시 등록하면 기존 토큰을 활성화만 하고 새로 저장하지 않는다")
	void register_SameToken_ReactivatesExisting() {
		// 1. Given
		Member member = mock(Member.class);
		when(memberRepository.findMemberById(MEMBER_ID)).thenReturn(Optional.of(member));

		DeviceToken existing = DeviceToken.builder().id(10L).member(member).token(TOKEN).active(false).build();
		when(deviceTokenRepository.findByMemberIdAndToken(MEMBER_ID, TOKEN)).thenReturn(Optional.of(existing));

		// 2. When
		Long resultId = deviceTokenService.register(TOKEN, MEMBER_ID);

		// 3. Then
		assertEquals(10L, resultId);
		assertTrue(existing.isActive());
		verify(deviceTokenRepository, never()).save(any());
	}

	@Test
	@DisplayName("새 토큰을 등록하면 기존 활성 토큰들을 비활성화하고 새 토큰을 저장한다")
	void register_NewToken_DeactivatesOldAndSavesNew() {
		// 1. Given
		Member member = mock(Member.class);
		when(memberRepository.findMemberById(MEMBER_ID)).thenReturn(Optional.of(member));
		when(deviceTokenRepository.findByMemberIdAndToken(MEMBER_ID, TOKEN)).thenReturn(Optional.empty());

		DeviceToken oldToken = DeviceToken.builder().id(10L).member(member).token("old-token").active(true).build();
		when(deviceTokenRepository.findAllByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(List.of(oldToken));

		// 2. When
		deviceTokenService.register(TOKEN, MEMBER_ID);

		// 3. Then (기존 토큰은 비활성화, 새 토큰은 활성 상태로 저장)
		assertFalse(oldToken.isActive());

		ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
		verify(deviceTokenRepository, times(1)).save(captor.capture());
		assertEquals(TOKEN, captor.getValue().getToken());
		assertTrue(captor.getValue().isActive());
		assertEquals(member, captor.getValue().getMember());
	}

	@Test
	@DisplayName("존재하지 않는 토큰을 해제하면 DEVICE_TOKEN_NOT_FOUND 예외가 발생한다")
	void unregister_TokenNotFound_ThrowsException() {
		// 1. Given
		when(deviceTokenRepository.findById(10L)).thenReturn(Optional.empty());

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> deviceTokenService.unregister(10L, MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.DEVICE_TOKEN_NOT_FOUND, exception.getCode());
	}

	@Test
	@DisplayName("다른 회원의 토큰을 해제하려고 하면 DEVICE_TOKEN_NOT_OWNED 예외가 발생한다")
	void unregister_NotOwnedToken_ThrowsException() {
		// 1. Given
		Member owner = mock(Member.class);
		when(owner.getId()).thenReturn(999L);
		DeviceToken token = DeviceToken.builder().id(10L).member(owner).token(TOKEN).active(true).build();
		when(deviceTokenRepository.findById(10L)).thenReturn(Optional.of(token));

		// 2. When
		DatabaseException exception = assertThrows(DatabaseException.class,
			() -> deviceTokenService.unregister(10L, MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.DEVICE_TOKEN_NOT_OWNED, exception.getCode());
		assertTrue(token.isActive());
	}

	@Test
	@DisplayName("본인의 토큰을 해제하면 토큰이 비활성화된다")
	void unregister_Success_DeactivatesToken() {
		// 1. Given
		Member owner = mock(Member.class);
		when(owner.getId()).thenReturn(MEMBER_ID);
		DeviceToken token = DeviceToken.builder().id(10L).member(owner).token(TOKEN).active(true).build();
		when(deviceTokenRepository.findById(10L)).thenReturn(Optional.of(token));

		// 2. When
		deviceTokenService.unregister(10L, MEMBER_ID);

		// 3. Then
		assertFalse(token.isActive());
	}

	@Test
	@DisplayName("무효 토큰 목록을 전달하면 해당 토큰들이 모두 비활성화된다")
	void deactivateTokens_DeactivatesAllInvalidTokens() {
		// 1. Given
		Member member = mock(Member.class);
		DeviceToken token1 = DeviceToken.builder().id(10L).member(member).token("t1").active(true).build();
		DeviceToken token2 = DeviceToken.builder().id(11L).member(member).token("t2").active(true).build();
		when(deviceTokenRepository.findAllByTokenIn(List.of("t1", "t2"))).thenReturn(List.of(token1, token2));

		// 2. When
		deviceTokenService.deactivateTokens(List.of("t1", "t2"));

		// 3. Then
		assertFalse(token1.isActive());
		assertFalse(token2.isActive());
	}
}
