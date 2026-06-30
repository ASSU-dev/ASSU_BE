package com.assu.server.domain.backoffice.entity;

import com.assu.server.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "backoffice_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BackofficeUser {

    @Id
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "id")
    private Member member;

    @Column(nullable = false, length = 255)
    private String name;

    public void updateName(String name) {
        this.name = name;
    }
}
