package com.assu.server.domain.admin.service;

import com.assu.server.domain.admin.dto.StudentAdminResponseDTO;

public interface StudentAdminService {
    StudentAdminResponseDTO.CountAdminAuthResponseDTO getCountAdminAuth(Long memberId);
    StudentAdminResponseDTO.NewCountAdminResponseDTO getNewStudentCountAdmin(Long memberId);
    StudentAdminResponseDTO.CountUsagePersonResponseDTO getCountUsagePerson(Long memberId);
    StudentAdminResponseDTO.CountUsageResponseDTO getCountUsage(Long memberId);
    StudentAdminResponseDTO.CountUsageListResponseDTO getCountUsageList(Long memberId);
}
