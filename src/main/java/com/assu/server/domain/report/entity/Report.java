package com.assu.server.domain.report.entity;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.report.entity.enums.ReportStatus;
import com.assu.server.domain.report.entity.enums.ReportTargetType;
import com.assu.server.domain.report.entity.enums.ReportType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private Member reporter; // 신고자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportTargetType targetType; // 신고 대상 타입 (STUDENT_USER, REVIEW, SUGGESTION)

    @Column(nullable = false)
    private Long targetId; // 신고 대상 ID (사용자 ID, 리뷰 ID, 건의 ID 등)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_id")
    private Member reported; // 피신고자 (사용자 신고인 경우에만)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportType reportType; // 신고 유형

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING; // 신고 상태

    // Todo 관리자용 업데이트 로직 추가
    // 신고 상태 업데이트 메서드
    public void updateStatus(ReportStatus status) {
        this.status = status;
    }
}
