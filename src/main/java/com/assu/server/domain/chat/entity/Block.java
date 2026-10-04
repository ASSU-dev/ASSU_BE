package com.assu.server.domain.chat.entity;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Block extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id")
    private Member blocker; // 차단한 사람

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id")
    private Member blocked; // 차단당한 사람

    @Builder
    public Block(Member blocker, Member blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
    }
}
