package com.assu.server.support;

import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.member.repository.MemberRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * application-test.yml에서 RedisAutoConfiguration을 제외하기 때문에,
 * Redis에 의존하는 JwtUtil(@Profile("!test"))을 대신할 목(mock) 빈을 등록한다.
 * 여러 @SpringBootTest에서 중복 정의되던 것을 공용으로 추출했다.
 * 사용하려는 테스트에서 @Import(CommonMockConfig.class)로 가져다 쓴다.
 */
@TestConfiguration
public class CommonMockConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisConnectionFactory connectionFactory = Mockito.mock(RedisConnectionFactory.class);
        RedisConnection connection = Mockito.mock(RedisConnection.class);
        Mockito.when(connectionFactory.getConnection()).thenReturn(connection);
        return connectionFactory;
    }

    @Bean
    @SuppressWarnings("unchecked")
    public RedisTemplate<String, Object> redisTemplate() {
        return Mockito.mock(RedisTemplate.class);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        return Mockito.mock(StringRedisTemplate.class);
    }

    @Bean
    @Primary
    public JwtUtil jwtUtil(
            MemberRepository memberRepository,
            StringRedisTemplate stringRedisTemplate,
            RedisConnectionFactory redisConnectionFactory
    ) {
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(stringRedisTemplate.hasKey(Mockito.anyString())).thenReturn(false);
        Mockito.when(stringRedisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);

        JwtUtil jwtUtil = new JwtUtil(memberRepository, stringRedisTemplate);
        ReflectionTestUtils.setField(jwtUtil, "secretKey", "S3csfifR3TrgwiKeyM2023WClokeyAppWIFNEGIBKWMGJ");
        ReflectionTestUtils.setField(jwtUtil, "accessValidSeconds", 3600);
        ReflectionTestUtils.setField(jwtUtil, "backofficeAccessValidSeconds", 1800);
        ReflectionTestUtils.setField(jwtUtil, "refreshValidSeconds", 1209600);
        return jwtUtil;
    }
}
