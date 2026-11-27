package com.assu.server.domain.admin.entity;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;

@Entity
@Table(indexes = @Index(name = "idx_admin_phone_num", columnList = "phone_num"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Admin {

    @Id
    private Long id;  // member_id와 동일

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private Member member;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    // Todo: 2학기 버전 출시시에 NotNull 처리
    private String phoneNum;

    @Column(name = "is_phone_verified", nullable = false)
    @NotNull
    private Boolean isPhoneVerified;

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

    public void updateOrganizationInfo(String name, String phoneNum, String officeAddress, String detailAddress,
                                       String signImageUrl, Major major, Department department, University university,
                                       Point point, Double latitude, Double longitude) {
        this.name = name;
        this.phoneNum = phoneNum;
        this.isPhoneVerified = true;
        this.officeAddress = officeAddress;
        this.detailAddress = detailAddress;
        this.signImageUrl = signImageUrl;
        this.major = major;
        this.department = department;
        this.university = university;
        this.point = point;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void verifySign(LocalDateTime verifiedAt) {
        this.isSignVerified = true;
        this.signVerifiedAt = verifiedAt;
    }

    public void changePhoneNum(String phoneNum) {
        this.phoneNum = phoneNum;
        this.isPhoneVerified = phoneNum != null;
    }

    public void updateProfile(String name, University university, Department department, Major major,
                                  String officeAddress, String detailAddress) {
        if (name != null && !name.isBlank()) this.name = name;
        if (university != null) this.university = university;
        if (department != null) this.department = department;
        if (major != null) this.major = major;
        if (officeAddress != null) this.officeAddress = officeAddress;
        if (detailAddress != null) this.detailAddress = detailAddress;
    }

    public void relocate(double latitude, double longitude, Point point) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.point = point;
    }
}
