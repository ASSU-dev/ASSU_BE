package com.assu.server.domain.member.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findMemberById(Long id);
    List<Member> findByDeletedAtBefore(LocalDateTime deletedAt);
    List<Member> findByRole(UserRole role);

    @Query("SELECT m.id FROM Member m")
    List<Long> findAllIds();

    @Query("SELECT m.id FROM Member m WHERE m.role = :role")
    List<Long> findIdsByRole(@Param("role") UserRole role);

    @Query(
            value = """
                    SELECT m FROM Member m
                    WHERE m.role <> com.assu.server.domain.common.enums.UserRole.BACKOFFICE
                      AND (:role IS NULL OR m.role = :role)
                      AND (:status IS NULL OR m.isActivated = :status)
                      AND (
                        :deletedFilter IS NULL
                        OR (:deletedFilter = TRUE AND m.deletedAt IS NOT NULL)
                        OR (:deletedFilter = FALSE AND m.deletedAt IS NULL)
                      )
                    """,
            countQuery = """
                    SELECT COUNT(m) FROM Member m
                    WHERE m.role <> com.assu.server.domain.common.enums.UserRole.BACKOFFICE
                      AND (:role IS NULL OR m.role = :role)
                      AND (:status IS NULL OR m.isActivated = :status)
                      AND (
                        :deletedFilter IS NULL
                        OR (:deletedFilter = TRUE AND m.deletedAt IS NOT NULL)
                        OR (:deletedFilter = FALSE AND m.deletedAt IS NULL)
                      )
                    """
    )
    Page<Member> searchMemberSummaries(
            @Param("role") UserRole role,
            @Param("status") ActivationStatus status,
            @Param("deletedFilter") Boolean deletedFilter,
            Pageable pageable
    );

    @Query("""
            SELECT m FROM Member m
            JOIN FETCH m.ssuAuth
            JOIN FETCH m.studentProfile
            WHERE m.id = :id
              AND m.role = com.assu.server.domain.common.enums.UserRole.STUDENT
            """)
    Optional<Member> findStudentWithProfileById(@Param("id") Long id);

    @Query("""
            SELECT m FROM Member m
            LEFT JOIN FETCH m.commonAuth
            JOIN FETCH m.adminProfile
            WHERE m.id = :id
              AND m.role = com.assu.server.domain.common.enums.UserRole.ADMIN
            """)
    Optional<Member> findAdminWithProfileById(@Param("id") Long id);

    @Query("""
            SELECT m FROM Member m
            LEFT JOIN FETCH m.commonAuth
            JOIN FETCH m.partnerProfile
            WHERE m.id = :id
              AND m.role = com.assu.server.domain.common.enums.UserRole.PARTNER
            """)
    Optional<Member> findPartnerWithProfileById(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT m FROM Member m
            JOIN FETCH m.ssuAuth
            JOIN FETCH m.studentProfile
            WHERE m.id IN :ids
              AND m.role = com.assu.server.domain.common.enums.UserRole.STUDENT
            """)
    List<Member> findStudentsWithProfileByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT DISTINCT m FROM Member m
            LEFT JOIN FETCH m.commonAuth
            JOIN FETCH m.adminProfile
            WHERE m.id IN :ids
              AND m.role = com.assu.server.domain.common.enums.UserRole.ADMIN
            """)
    List<Member> findAdminsWithProfileByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT DISTINCT m FROM Member m
            LEFT JOIN FETCH m.commonAuth
            JOIN FETCH m.partnerProfile
            WHERE m.id IN :ids
              AND m.role = com.assu.server.domain.common.enums.UserRole.PARTNER
            """)
    List<Member> findPartnersWithProfileByIdIn(@Param("ids") Collection<Long> ids);
}
