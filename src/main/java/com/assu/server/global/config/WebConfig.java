package com.assu.server.global.config;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(6000);
    }

    // 학교 와이파이처럼 여러 사용자가 IP 하나를 함께 쓰는 환경을 고려해 계정·번호별 한도보다 넉넉히 잡는다
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IpRateLimitInterceptor(
                        redisTemplate, meterRegistry, "login", 30, Duration.ofMinutes(15)))
                .addPathPatterns("/auth/commons/login", "/auth/backoffice/login");
        registry.addInterceptor(new IpRateLimitInterceptor(
                        redisTemplate, meterRegistry, "sms-send", 20, Duration.ofHours(1)))
                .addPathPatterns("/auth/phone-verification/check-and-send");
    }
}
