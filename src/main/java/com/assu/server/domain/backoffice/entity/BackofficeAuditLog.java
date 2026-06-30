package com.assu.server.domain.backoffice.entity;

import com.assu.server.domain.backoffice.entity.enums.BackofficeAuditStatus;
import com.assu.server.domain.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "backoffice_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BackofficeAuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long backofficeMemberId;

    @Column(nullable = false, length = 16)
    private String httpMethod;

    @Column(nullable = false, length = 512)
    private String requestUri;

    @Column(nullable = false, length = 255)
    private String handler;

    @Column(length = 64)
    private String action;

    @Column(length = 128)
    private String targetResourceId;

    @Column(length = 64)
    private String clientIp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BackofficeAuditStatus status;

    private Integer httpStatusCode;

    private Long durationMs;

    @Column(length = 500)
    private String errorMessage;
}
