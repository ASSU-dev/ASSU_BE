package com.assu.server.domain.common.entity;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.student.entity.Student;
import jakarta.persistence.*;

@Entity
public class AdminUser extends BaseEntity{

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "admin_id")
    private Admin admin;

    @ManyToOne
    @JoinColumn(name = "student_id")
    private Student student;

}