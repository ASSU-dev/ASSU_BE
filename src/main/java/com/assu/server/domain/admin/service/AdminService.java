package com.assu.server.domain.admin.service;
import com.assu.server.domain.admin.dto.AdminResponseDTO;

import java.util.List;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.user.entity.enums.Department;
import com.assu.server.domain.user.entity.enums.Major;
import com.assu.server.domain.user.entity.enums.University;

// PaperQueryServiceImpl 이 AdminService 참조 중 -> 순환참조 문제 발생하지 않도록 주의
public interface AdminService {
	List<Admin> findMatchingAdmins(University university, Department department, Major major);

    AdminResponseDTO suggestRandomPartner(Long adminId);

}
