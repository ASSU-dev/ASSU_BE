package com.assu.server.domain.report.repository;

import com.assu.server.domain.report.entity.Report;
import com.assu.server.domain.report.entity.enums.ReportStatus;
import com.assu.server.domain.report.entity.enums.ReportTargetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    List<Report> findAllByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);

    List<Report> findAllByStatusIn(List<ReportStatus> statuses);
}
