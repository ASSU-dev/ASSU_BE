package com.assu.server.domain.auth.entity;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.member.entity.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
    private Long id;

    @OneToOne @MapsId
    @JoinColumn(name = "member_id", referencedColumnName = "id")
    @NotNull
    private Member member;

    @Column(name = "email", length = 255, nullable = false)
    @NotNull
    @Email
    private String email;

    @Column(name = "password", length = 255, nullable = false)
    @NotNull
    private String hashedPassword;

    @Column(name = "last_login_at")
    @NotNull
    private LocalDateTime lastLoginAt;
}
