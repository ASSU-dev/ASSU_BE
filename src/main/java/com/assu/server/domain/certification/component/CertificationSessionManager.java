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

	private final StringRedisTemplate redisTemplate;
	private static final String ID_COUNTER_KEY = "certification:id:seq";
	private static final String SESSION_INIT_MARKER = "STATUS_OPEN";

	private String getKey(Long sessionId) {
		return "certification:session:" + sessionId;
	}

	private String getInfoKey(Long sessionId) {
		return "certification:info:" + sessionId;
	}

	public Long openSession(Long storeId, Integer peopleNumber) {
		Long newSessionId = redisTemplate.opsForValue().increment(ID_COUNTER_KEY);
		if (newSessionId == null) throw new RuntimeException("ID 생성 실패");

		String key = getKey(newSessionId);
		String infoKey = getInfoKey(newSessionId);

		redisTemplate.opsForSet().add(key, SESSION_INIT_MARKER);
		redisTemplate.expire(key, 10, TimeUnit.MINUTES);

		redisTemplate.opsForHash().put(infoKey, "storeId", String.valueOf(storeId));
		redisTemplate.opsForHash().put(infoKey, "peopleNumber", String.valueOf(peopleNumber));
		redisTemplate.expire(infoKey, 10, TimeUnit.MINUTES);

		return newSessionId;
	}

	public boolean exists(Long sessionId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(getKey(sessionId)));
	}

	public String getSessionInfo(Long sessionId, String field) {
		return (String) redisTemplate.opsForHash().get(getInfoKey(sessionId), field);
	}

	public void addUserToSession(Long sessionId, Long userId) {
		redisTemplate.opsForSet().add(getKey(sessionId), String.valueOf(userId));
	}

	public boolean hasUser(Long sessionId, Long userId) {
		return redisTemplate.opsForSet().isMember(getKey(sessionId), String.valueOf(userId));
	}

	public List<Long> snapshotUserIds(Long sessionId) {
		Set<String> members = redisTemplate.opsForSet().members(getKey(sessionId));
		if (members == null) return List.of();

		return members.stream()
			.filter(m -> !SESSION_INIT_MARKER.equals(m))
			.map(Long::valueOf)
			.collect(Collectors.toList());
	}

	public void removeSession(Long sessionId) {
		redisTemplate.delete(getKey(sessionId));
		redisTemplate.delete(getInfoKey(sessionId));
	}
}