package com.assu.server.domain.mapping.converter;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.mapping.dto.StudentAdminResponseDTO;
import com.assu.server.domain.partnership.entity.Paper;

import java.util.List;

public class StudentAdminConverter {

    public static StudentAdminResponseDTO.CountAdminAuthResponseDTO countAdminAuthDTO(Long adminId, Long total, String adminName) {
        return StudentAdminResponseDTO.CountAdminAuthResponseDTO.builder()
            .adminId(adminId)
            .studentCount(total)
            .adminName(adminName)
            .build();
    }

    public static StudentAdminResponseDTO.NewCountAdminResponseDTO newCountAdminResponseDTO(Long adminId, Long total, String adminName){
        return StudentAdminResponseDTO.NewCountAdminResponseDTO.builder()
                .adminId(adminId)
                .newStudentCount(total)
                .adminName(adminName)
                .build();
    }
    //오늘 사용자수
    public static StudentAdminResponseDTO.CountUsagePersonResponseDTO countUsagePersonDTO(Long adminId, Long total, String adminName){
        return StudentAdminResponseDTO.CountUsagePersonResponseDTO.builder()
                .adminId(adminId)
                .usagePersonCount(total)
                .adminName(adminName)
                .build();
    }
    //업체별 누적 사용건수
    public static StudentAdminResponseDTO.CountUsageResponseDTO countUsageResponseDTO(Admin admin, Paper paper, Long total) {
        return StudentAdminResponseDTO.CountUsageResponseDTO.builder()
                .usageCount(total)
                .adminId(admin.getId())
                .adminName(admin.getName())
                .storeId(paper.getStore().getId())
                .storeName(paper.getStore().getName())
                .build();
    }
    public static StudentAdminResponseDTO.CountUsageListResponseDTO countUsageListResponseDTO(List<StudentAdminResponseDTO.CountUsageResponseDTO> countUsageList) {
        return StudentAdminResponseDTO.CountUsageListResponseDTO.builder()
                .items(countUsageList)
                .build();
    }
}
