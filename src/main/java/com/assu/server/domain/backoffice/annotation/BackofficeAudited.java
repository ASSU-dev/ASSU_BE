package com.assu.server.domain.backoffice.annotation;

import com.assu.server.domain.backoffice.entity.enums.BackofficeAuditType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BackofficeAudited {
    String action();
    String targetId() default "";
    BackofficeAuditType type() default BackofficeAuditType.WRITE;
}
