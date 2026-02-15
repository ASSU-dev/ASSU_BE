package com.assu.server.domain.member.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByPhoneNum(String phoneNum);
    Optional<Member> findMemberById(Long id);
    List<Member> findByDeletedAtBefore(LocalDateTime deletedAt);
}
