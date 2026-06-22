package com.assu.server.domain.backoffice.aop;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.entity.BackofficeAuditLog;
import com.assu.server.domain.backoffice.entity.enums.BackofficeAuditStatus;
import com.assu.server.domain.backoffice.service.BackofficeAuditLogService;
import com.assu.server.global.util.PrincipalDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class BackofficeAuditAspect {

    private final BackofficeAuditLogService backofficeAuditLogService;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    @Around("@annotation(backofficeAudited)")
    public Object audit(ProceedingJoinPoint joinPoint, BackofficeAudited backofficeAudited) throws Throwable {
        long startedAt = System.currentTimeMillis();
        HttpServletRequest request = currentRequest();
        Long backofficeMemberId = currentMemberId();
        String handler = joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName();
        String targetResourceId = resolveTargetId(joinPoint, backofficeAudited.targetId());

        try {
            Object result = joinPoint.proceed();
            backofficeAuditLogService.save(BackofficeAuditLog.builder()
                    .backofficeMemberId(backofficeMemberId)
                    .httpMethod(request != null ? request.getMethod() : "UNKNOWN")
                    .requestUri(request != null ? request.getRequestURI() : "UNKNOWN")
                    .handler(handler)
                    .action(backofficeAudited.action())
                    .targetResourceId(targetResourceId)
                    .clientIp(resolveClientIp(request))
                    .status(BackofficeAuditStatus.SUCCESS)
                    .httpStatusCode(200)
                    .durationMs(System.currentTimeMillis() - startedAt)
                    .build());
            return result;
        } catch (Throwable throwable) {
            backofficeAuditLogService.saveFailure(
                    backofficeMemberId,
                    request != null ? request.getMethod() : "UNKNOWN",
                    request != null ? request.getRequestURI() : "UNKNOWN",
                    handler,
                    backofficeAudited.action(),
                    targetResourceId,
                    resolveClientIp(request),
                    System.currentTimeMillis() - startedAt,
                    throwable.getMessage()
            );
            throw throwable;
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private Long currentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PrincipalDetails pd) {
            return pd.getId();
        }
        return null;
    }

    private String resolveTargetId(ProceedingJoinPoint joinPoint, String spel) {
        if (spel == null || spel.isBlank()) {
            return null;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        StandardEvaluationContext context = new StandardEvaluationContext();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        Object value = expressionParser.parseExpression(spel).getValue(context);
        return value != null ? value.toString() : null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
