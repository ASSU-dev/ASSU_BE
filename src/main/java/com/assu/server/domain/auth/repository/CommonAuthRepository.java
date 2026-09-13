package com.assu.server.domain.auth.repository;

import com.assu.server.domain.auth.entity.CommonAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommonAuthRepository extends JpaRepository<CommonAuth, Long> {
    boolean existsByEmail(String email);

    boolean existsByEmailAndMember_DeletedAtIsNull(String email);

    Optional<CommonAuth> findByEmail(String email);
}
