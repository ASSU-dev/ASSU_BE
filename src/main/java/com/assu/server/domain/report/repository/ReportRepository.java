package com.assu.server.domain.report.repository;

import com.assu.server.domain.report.entity.Report;
import com.assu.server.domain.report.entity.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    // 특정 사용자가 특정 대상을 신고했는지 확인
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    @Override
    @EntityGraph(attributePaths = {
            "reporter",
            "reporter.studentProfile",
            "reporter.adminProfile",
            "reporter.partnerProfile",
            "reporter.backofficeProfile",
            "reported"
    })
    Page<Report> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
            "reporter",
            "reporter.studentProfile",
            "reporter.adminProfile",
            "reporter.partnerProfile",
            "reporter.backofficeProfile",
            "reported"
    })
    Optional<Report> findById(Long id);
}
