package com.assu.server.domain.certification.component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CertificationSessionManager {

	// RedisTemplate을 주입받습니다.
	private final StringRedisTemplate redisTemplate;

	// 세션 ID를 위한 KEY를 만드는 헬퍼 메서드
	private String getKey(Long sessionId) {
		return "certification:session:" + sessionId;
	}

	public void openSession(Long sessionId) {
		String key = getKey(sessionId);
		// 세션을 연다는 것은 키를 만드는 것과 같습니다.
		// addUserToSession에서 자동으로 키가 생성되므로 이 메서드는 비워두거나,
		// 만료 시간 설정 등 초기화 로직을 넣을 수 있습니다.
		// 예: 10분 후 만료
		redisTemplate.expire(key, 10, TimeUnit.MINUTES);
	}

	public void addUserToSession(Long sessionId, Long userId) {
		String key = getKey(sessionId);
		// Redis의 Set 자료구조에 userId를 추가합니다.
		redisTemplate.opsForSet().add(key, String.valueOf(userId));
	}

	public int getCurrentUserCount(Long sessionId) {
		String key = getKey(sessionId);
		// Redis Set의 크기를 반환합니다.
		Long size = redisTemplate.opsForSet().size(key);
		return size != null ? size.intValue() : 0;
	}

	public boolean hasUser(Long sessionId, Long userId) {
		String key = getKey(sessionId);
		// Redis Set에 해당 멤버가 있는지 확인합니다.
		return redisTemplate.opsForSet().isMember(key, String.valueOf(userId));
	}

	public List<Long> snapshotUserIds(Long sessionId) {
		String key = getKey(sessionId);
		// Redis Set의 모든 멤버를 가져옵니다.
		Set<String> members = redisTemplate.opsForSet().members(key);
		if (members == null) {
			return List.of();
		}
		return members.stream()
			.map(Long::valueOf)
			.collect(Collectors.toList());
	}

	public void removeSession(Long sessionId) {
		String key = getKey(sessionId);
		// 세션 키 자체를 삭제합니다.
		redisTemplate.delete(key);
	}
}