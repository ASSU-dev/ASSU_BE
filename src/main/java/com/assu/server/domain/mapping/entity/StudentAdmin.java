package com.assu.server.domain.mapping.entity;

import com.assu.server.domain.common.entity.BaseEntity;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.admin.entity.Admin;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "student_admin_mapping")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StudentAdmin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Student 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    // Admin 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id", nullable = false)
    private Admin admin;

}
