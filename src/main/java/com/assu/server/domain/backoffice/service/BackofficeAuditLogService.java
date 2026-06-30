package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.entity.BackofficeAuditLog;
import com.assu.server.domain.backoffice.entity.enums.BackofficeAuditStatus;
import com.assu.server.domain.backoffice.repository.BackofficeAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BackofficeAuditLogService {

    private final BackofficeAuditLogRepository backofficeAuditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(BackofficeAuditLog log) {
        backofficeAuditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailure(
            Long backofficeMemberId,
            String httpMethod,
            String requestUri,
            String handler,
            String action,
            String targetResourceId,
            String clientIp,
            long durationMs,
            String errorMessage
    ) {
        save(BackofficeAuditLog.builder()
                .backofficeMemberId(backofficeMemberId)
                .httpMethod(httpMethod)
                .requestUri(requestUri)
                .handler(handler)
                .action(action)
                .targetResourceId(targetResourceId)
                .clientIp(clientIp)
                .status(BackofficeAuditStatus.FAILURE)
                .httpStatusCode(500)
                .durationMs(durationMs)
                .errorMessage(truncate(errorMessage))
                .build());
    }

    static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
