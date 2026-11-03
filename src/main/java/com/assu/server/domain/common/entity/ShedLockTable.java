package com.assu.server.domain.common.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// ShedLock이 JDBC로 직접 사용하는 테이블. ddl-auto로 모든 환경에 생성되도록 엔티티로만 매핑한다.
@Entity
@Table(name = "shedlock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShedLockTable {

    @Id
    @Column(name = "name", length = 64)
    private String name;

    @Column(name = "lock_until", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime lockUntil;

    @Column(name = "locked_at", nullable = false, columnDefinition = "DATETIME(3)")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by", nullable = false)
    private String lockedBy;
}
