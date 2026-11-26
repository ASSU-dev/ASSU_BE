package com.assu.server.domain.auth.repository;

import com.assu.server.domain.auth.entity.CommonAuth;
import com.assu.server.domain.common.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommonAuthRepository extends JpaRepository<CommonAuth, Long> {
    boolean existsByEmail(String email);

    boolean existsByEmailAndMember_DeletedAtIsNull(String email);

    Optional<CommonAuth> findByEmail(String email);

    @Query("SELECT ca.member.role FROM CommonAuth ca WHERE ca.email = :email")
    Optional<UserRole> findRoleByEmail(@Param("email") String email);
}
