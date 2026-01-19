package com.assu.server.domain.auth.entity;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "common_auth",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_common_auth_email", columnNames = {"email"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonAuth extends BaseEntity {

    @Id
    @Column(name = "member_id")
    private Long id;

    @OneToOne @MapsId
    @JoinColumn(name = "member_id", referencedColumnName = "id")
    private Member member;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "password", length = 255, nullable = false)
    @NotNull
    private String hashedPassword;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
