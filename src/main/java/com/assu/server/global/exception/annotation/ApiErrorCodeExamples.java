package com.assu.server.global.exception.annotation;

import com.assu.server.global.apiPayload.code.status.SwaggerErrorCodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodeExamples {
    SwaggerErrorCodes value();
}
