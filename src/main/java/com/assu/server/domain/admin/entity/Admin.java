package com.assu.server.domain.admin.entity;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.user.entity.enums.Department;
import com.assu.server.domain.user.entity.enums.Major;
import com.assu.server.domain.user.entity.enums.University;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Admin {

    @Id
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    @NotNull
    private Member member;

    @NotNull
    @Column(nullable = false)
    private String name;

    @NotNull
    @Column(nullable = false)
    private String officeAddress;

    private String detailAddress;

    private String signImageUrl;

    @NotNull
    @Builder.Default
    @Column(nullable = false)
    private Boolean isSignVerified = false;

    private LocalDateTime signVerifiedAt;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private Major major;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private Department department;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private University university;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    private Point point;

    @NotNull
    @Column(nullable = false)
    private Double latitude;

    @NotNull
    @Column(nullable = false)
    private Double longitude;

    // --- 비즈니스 로직 및 연관관계 메서드 ---

    /**
     * @Setter 대신 사용하는 연관 관계 메서드
     * Member의 ID를 Admin의 PK로 동기화
     */
    public void linkMember(Member member) {
        this.member = member;
        if (member != null) {
            this.id = member.getId();
        }
    }
}