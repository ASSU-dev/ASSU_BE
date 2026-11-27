package com.assu.server.domain.certification.component;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.assu.server.domain.certification.entity.enums.SessionJoinStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CertificationSessionManager {

	private final StringRedisTemplate redisTemplate;
	private static final String ID_COUNTER_KEY = "certification:id:seq";
	private static final String SESSION_INIT_MARKER = "STATUS_OPEN";

	// 존재 확인·중복 판정(SADD 반환값)·정원 검사를 한 번에 처리한다. SCARD에는 초기 마커 1개가 포함된다.
	private static final RedisScript<List> JOIN_SCRIPT = RedisScript.of("""
		if redis.call('EXISTS', KEYS[1]) == 0 then return {'NO_SESSION'} end
		if redis.call('SADD', KEYS[1], ARGV[1]) == 0 then return {'DUPLICATE'} end
		if redis.call('SCARD', KEYS[1]) - 1 > tonumber(ARGV[2]) then
			redis.call('SREM', KEYS[1], ARGV[1])
			return {'FULL'}
		end
		local members = redis.call('SMEMBERS', KEYS[1])
		table.insert(members, 1, 'JOINED')
		return members
		""", List.class);

	public record JoinResult(SessionJoinStatus status, List<Long> userIds) {}


	public Long openSession(Long storeId, Integer peopleNumber, Long ownerId, Long adminId, Long contentId) {
		Long newSessionId = redisTemplate.opsForValue().increment(ID_COUNTER_KEY);
		if (newSessionId == null) throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "인증 세션 ID 생성 실패");

		String key = getKey(newSessionId);
		String infoKey = getInfoKey(newSessionId);

		redisTemplate.opsForSet().add(key, SESSION_INIT_MARKER);
		redisTemplate.expire(key, 10, TimeUnit.MINUTES);

		redisTemplate.opsForHash().put(infoKey, "storeId", String.valueOf(storeId));
		redisTemplate.opsForHash().put(infoKey, "peopleNumber", String.valueOf(peopleNumber));
		redisTemplate.opsForHash().put(infoKey, "ownerId", String.valueOf(ownerId));
		redisTemplate.opsForHash().put(infoKey, "adminId", String.valueOf(adminId));
		if (contentId != null) {
			redisTemplate.opsForHash().put(infoKey, "contentId", String.valueOf(contentId));
		}
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

	public JoinResult joinSession(Long sessionId, Long userId, int capacity) {
		List<?> result = redisTemplate.execute(JOIN_SCRIPT, List.of(getKey(sessionId)),
			String.valueOf(userId), String.valueOf(capacity));
		if (result == null || result.isEmpty()) {
			throw new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR, "인증 세션 참여 결과를 받지 못했습니다.");
		}

		SessionJoinStatus status = SessionJoinStatus.valueOf(String.valueOf(result.get(0)));
		List<Long> userIds = result.stream()
			.skip(1)
			.map(String::valueOf)
			.filter(m -> !SESSION_INIT_MARKER.equals(m))
			.map(Long::valueOf)
			.collect(Collectors.toList());
		return new JoinResult(status, userIds);
	}

	public List<Long> snapshotUserIds(Long sessionId) {
		Set<String> members = redisTemplate.opsForSet().members(getKey(sessionId));
		if (members == null) return List.of();

		return members.stream()
			.filter(m -> !SESSION_INIT_MARKER.equals(m))
			.map(Long::valueOf)
			.collect(Collectors.toList());
	}

	// 세션 키를 실제로 삭제한 호출만 true를 받는다. 완료·만료 처리의 선점 기준으로 쓴다.
	public boolean removeSession(Long sessionId) {
		Boolean removed = redisTemplate.delete(getKey(sessionId));
		redisTemplate.delete(getInfoKey(sessionId));
		return Boolean.TRUE.equals(removed);
	}

	private String getKey(Long sessionId) {
		return "certification:session:" + sessionId;
	}

	private String getInfoKey(Long sessionId) {
		return "certification:info:" + sessionId;
	}
}
