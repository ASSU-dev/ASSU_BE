package com.assu.server.infra.firebase;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * firebase.enabled 프로퍼티로 FCM 관련 빈 등록 여부를 제어한다.
 * 키를 여러 클래스에 문자열로 중복 기입하지 않도록 여기서 한 곳에서 관리한다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "firebase.enabled", havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnFirebaseEnabled {
}
