package com.assu.server.domain.admin.service;
import com.assu.server.domain.admin.dto.AdminResponseDTO;

import java.util.List;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.user.entity.enums.Department;
import com.assu.server.domain.user.entity.enums.Major;
import com.assu.server.domain.user.entity.enums.University;

public interface AdminService {
	List<Admin> findMatchingAdmins(University university, Department department, Major major);

    AdminResponseDTO suggestRandomPartner(Long adminId);

}
