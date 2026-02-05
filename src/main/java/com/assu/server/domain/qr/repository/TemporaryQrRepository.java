package com.assu.server.domain.qr.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.assu.server.domain.qr.entity.Qr;

@Repository
public interface TemporaryQrRepository extends JpaRepository<Qr, Long> {
}
