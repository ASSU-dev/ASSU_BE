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
@AllArgsConstructor
@Builder
@Setter
public class Admin {

    @Id
    private Long id;  // member_id와 동일

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private Member member;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "office_address", length = 255, nullable = false)
    private String officeAddress;

    @Column(name = "detail", length = 255)
    private String detailAddress;

    private String signImageUrl;

    private Boolean isSignVerified;

    private LocalDateTime signVerifiedAt;

    @Enumerated(EnumType.STRING)
    private Major major;

    @Enumerated(EnumType.STRING)
    private Department department;

    @Enumerated(EnumType.STRING)
    @NotNull
    private University university;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    private Point point;

    private double latitude;
    private double longitude;

    public void updateMember(Member member) {
        this.member = member;
    }
}
