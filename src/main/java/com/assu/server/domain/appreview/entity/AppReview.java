package com.assu.server.domain.appreview.entity;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.member.entity.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AppReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    @NotNull
    private Member member;

    @NotNull
    @Column(nullable = false)
    private Integer rate;

    @NotNull
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
