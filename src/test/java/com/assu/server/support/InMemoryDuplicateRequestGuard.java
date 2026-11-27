package com.assu.server.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import com.assu.server.global.util.DuplicateRequestGuard;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public final class InMemoryDuplicateRequestGuard {

	private InMemoryDuplicateRequestGuard() {
	}

	@SuppressWarnings("unchecked")
	public static DuplicateRequestGuard create(Map<String, String> store) {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class, withSettings().strictness(Strictness.LENIENT));
		ValueOperations<String, String> valueOperations = mock(ValueOperations.class,
			withSettings().strictness(Strictness.LENIENT));

		doAnswer(invocation -> valueOperations).when(redisTemplate).opsForValue();
		doAnswer(invocation -> store.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null)
			.when(valueOperations).setIfAbsent(anyString(), anyString(), any(Duration.class));
		doAnswer(invocation -> {
			List<String> keys = invocation.getArgument(1);
			String token = invocation.getArgument(2);
			return store.remove(keys.get(0), token) ? 1L : 0L;
		}).when(redisTemplate).execute(any(RedisScript.class), anyList(), any());

		return new DuplicateRequestGuard(redisTemplate, new SimpleMeterRegistry());
	}

	public static DuplicateRequestGuard create() {
		return create(new ConcurrentHashMap<>());
	}
}
