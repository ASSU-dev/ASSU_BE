package com.assu.server.domain.admin.service;

import com.assu.server.domain.admin.dto.AdminResponseDTO;
import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.user.entity.enums.Department;
import com.assu.server.domain.user.entity.enums.Major;
import com.assu.server.domain.user.entity.enums.University;

import java.util.List;

public interface AdminService {
	List<Admin> findMatchingAdmins(University university, Department department, Major major);

    AdminResponseDTO suggestRandomPartner(Long adminId);

}
