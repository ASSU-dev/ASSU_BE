package com.assu.server.domain.suggestion.repository;

import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.domain.suggestion.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

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
