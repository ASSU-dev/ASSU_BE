package com.assu.server.domain.partner.entity;


import com.assu.server.domain.member.entity.Member;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Id;
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
public class Partner {

    @Id
    private Long id;  // member_id와 동일

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private Member member;

    private String name;

    private String address;

    private String detailAddress;

    private String licenseUrl;

    private Boolean isLicenseVerified;

    private LocalDateTime licenseVerifiedAt;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    private Point point;

    private double latitude;
    private double longitude;

}
