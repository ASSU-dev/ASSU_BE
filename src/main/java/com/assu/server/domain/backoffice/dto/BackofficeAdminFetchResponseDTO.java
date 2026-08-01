package com.assu.server.domain.backoffice.dto;

import java.util.List;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;

public record BackofficeAdminFetchResponseDTO(
	List<BackofficeAdminInfoDTO> adminList
) {
	public record BackofficeAdminInfoDTO(
		Long adminId,
		String email,
		String name,
		String phoneNumber,
		University university,
		Department department,
		Major major,
		String officeAddress,
		String detailAddress,
		String signImageUrl,
		Boolean isPhoneVerified
	) {
		public static BackofficeAdminInfoDTO from(Admin admin) {
			String email = (admin.getMember() != null && admin.getMember().getCommonAuth() != null) 
				? admin.getMember().getCommonAuth().getEmail() 
				: null;
			return new BackofficeAdminInfoDTO(
				admin.getId(),
				email,
				admin.getName(),
				admin.getPhoneNum(),
				admin.getUniversity(),
				admin.getDepartment(),
				admin.getMajor(),
				admin.getOfficeAddress(),
				admin.getDetailAddress(),
				admin.getSignImageUrl(),
				admin.getIsPhoneVerified()
			);
		}
	}

}
