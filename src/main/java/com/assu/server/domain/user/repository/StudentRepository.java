package com.assu.server.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.assu.server.domain.user.entity.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {
}
