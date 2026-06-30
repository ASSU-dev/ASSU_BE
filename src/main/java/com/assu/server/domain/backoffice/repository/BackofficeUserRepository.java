package com.assu.server.domain.backoffice.repository;

import com.assu.server.domain.backoffice.entity.BackofficeUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackofficeUserRepository extends JpaRepository<BackofficeUser, Long> {

    long countAllBy();
}
