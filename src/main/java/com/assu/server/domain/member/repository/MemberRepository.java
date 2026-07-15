package com.assu.server.domain.member.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
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
}
