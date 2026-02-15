package com.assu.server.domain.member.entity;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.auth.entity.CommonAuth;
import com.assu.server.domain.auth.entity.SSUAuth;
import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.user.entity.Student;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@DynamicUpdate
@DynamicInsert
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private String phoneNum;

    @NotNull
    private Boolean isPhoneVerified;

    @NotNull
    private Boolean isLocationTermAgreed;

    @NotNull
    private Boolean isMarketingTermAgreed;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @NotNull
    private UserRole role; // STUDENT, ADMIN, PARTNER

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private ActivationStatus isActivated;  // ACTIVE, INACTIVE, SUSPEND

    private String profileUrl;

    // 소프트 삭제를 위한 삭제 시점
    private LocalDateTime deletedAt;

    // 역할별 프로필 - 선택적으로 연관
    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL)
    private Student studentProfile;

    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL)
    private Admin adminProfile;

    @OneToOne(mappedBy = "member", cascade = CascadeType.ALL)
    private Partner partnerProfile;

    // 연관관계 (1:1) — 양방향 필요 없으면 아래 필드 제거해도 됨
    @OneToOne(mappedBy = "member", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private SSUAuth ssuAuth;

    @OneToOne(mappedBy = "member", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private CommonAuth commonAuth;
}
