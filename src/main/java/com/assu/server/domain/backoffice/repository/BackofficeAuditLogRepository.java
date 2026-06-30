package com.assu.server.domain.backoffice.repository;

import com.assu.server.domain.backoffice.entity.BackofficeAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackofficeAuditLogRepository extends JpaRepository<BackofficeAuditLog, Long> {
}
