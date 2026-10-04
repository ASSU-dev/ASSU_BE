package com.assu.server.domain.suggestion.repository;

import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.domain.suggestion.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Suggestion s WHERE s.student.id = :studentId")
    void deleteAllByStudentId(@Param("studentId") Long studentId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Suggestion s WHERE s.admin.id = :adminId")
    void deleteAllByAdminId(@Param("adminId") Long adminId);

    @Query("""
            select s
            from Suggestion s
            join fetch s.student st
            where s.admin.id = :adminId
            AND s.status = :status
            AND s.student.status = :studentStatus
            order by s.createdAt desc
            """)
    List<Suggestion> findAllSuggestionsWithStatus(
            @Param("adminId") Long adminId,
            @Param("status") ReportedStatus status,
            @Param("studentStatus") ReportedStatus studentStatus
    );
}
