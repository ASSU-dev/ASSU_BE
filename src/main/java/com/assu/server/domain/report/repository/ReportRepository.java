package com.assu.server.domain.report.repository;

import com.assu.server.domain.report.entity.Report;
import com.assu.server.domain.report.entity.enums.ReportStatus;
import com.assu.server.domain.report.entity.enums.ReportTargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    boolean existsByReporterIdAndTargetTypeAndTargetId(Long reporterId, ReportTargetType targetType, Long targetId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Report r SET r.reporter = null WHERE r.reporter.id = :memberId")
    void anonymizeReporterByMemberId(@Param("memberId") Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Report r SET r.reported = null WHERE r.reported.id = :memberId")
    void anonymizeReportedByMemberId(@Param("memberId") Long memberId);

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

    List<Report> findAllByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);

    @EntityGraph(attributePaths = {
            "reporter",
            "reporter.studentProfile",
            "reporter.adminProfile",
            "reporter.partnerProfile",
            "reporter.backofficeProfile",
            "reported"
    })
    List<Report> findAllByStatusIn(List<ReportStatus> statuses);
}
