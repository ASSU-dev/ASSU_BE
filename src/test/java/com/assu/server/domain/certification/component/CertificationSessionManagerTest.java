package com.assu.server.domain.certification.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.assu.server.domain.certification.entity.enums.SessionJoinStatus;

@ExtendWith(MockitoExtension.class)
class CertificationSessionManagerTest {

	private static final Long SESSION_ID = 5L;
	private static final String SESSION_KEY = "certification:session:5";

	@Mock
	private StringRedisTemplate redisTemplate;

	@InjectMocks
	private CertificationSessionManager sessionManager;

	@Test
	void joinSession_whenJoined_thenReturnsUserIdsWithoutInitMarker() {
		// given
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
			.thenReturn(List.of("JOINED", "STATUS_OPEN", "1", "2"));

		// when
		CertificationSessionManager.JoinResult result = sessionManager.joinSession(SESSION_ID, 2L, 2);

		// then
		assertThat(result.status()).isEqualTo(SessionJoinStatus.JOINED);
		assertThat(result.userIds()).containsExactlyInAnyOrder(1L, 2L);
	}

	@Test
	void joinSession_whenRejected_thenReturnsStatusWithoutUserIds() {
		// given
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any()))
			.thenReturn(List.of("FULL"));

		// when
		CertificationSessionManager.JoinResult result = sessionManager.joinSession(SESSION_ID, 3L, 2);

		// then
		assertThat(result.status()).isEqualTo(SessionJoinStatus.FULL);
		assertThat(result.userIds()).isEmpty();
	}

	@Test
	void removeSession_whenKeyAlreadyDeleted_thenReturnsFalse() {
		// given
		lenient().when(redisTemplate.delete(SESSION_KEY)).thenReturn(true, false);

		// when & then
		assertThat(sessionManager.removeSession(SESSION_ID)).isTrue();
		assertThat(sessionManager.removeSession(SESSION_ID)).isFalse();
	}
}
