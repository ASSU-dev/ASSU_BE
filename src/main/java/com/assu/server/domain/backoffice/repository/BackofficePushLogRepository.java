package com.assu.server.domain.backoffice.repository;

import com.assu.server.domain.backoffice.entity.BackofficePushLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackofficePushLogRepository extends JpaRepository<BackofficePushLog, Long> {

    Page<BackofficePushLog> findByTitleContainingOrBodyContaining(
            String title, String body, Pageable pageable
    );
}