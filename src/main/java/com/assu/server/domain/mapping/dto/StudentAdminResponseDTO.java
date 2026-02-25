package com.assu.server.domain.mapping.dto;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.partnership.entity.Paper;
import lombok.Builder;
import java.util.List;

public class StudentAdminResponseDTO {

    @Builder
    public record CountAdminAuthResponseDTO(
            Long studentCount,
            Long adminId,
            String adminName
    ) {
        public static CountAdminAuthResponseDTO from(Long adminId, Long total, String adminName) {
            return new CountAdminAuthResponseDTO(total, adminId, adminName);
        }
    }

    @Builder
    public record NewCountAdminResponseDTO(
            Long newStudentCount,
            Long adminId,
            String adminName
    ) {
        public static NewCountAdminResponseDTO from(Long adminId, Long total, String adminName) {
            return new NewCountAdminResponseDTO(total, adminId, adminName);
        }
    }

    @Builder
    public record CountUsagePersonResponseDTO(
            Long usagePersonCount,
            Long adminId,
            String adminName
    ) {
        public static CountUsagePersonResponseDTO from(Long adminId, Long total, String adminName) {
            return new CountUsagePersonResponseDTO(total, adminId, adminName);
        }
    }

    @Builder
    public record CountUsageResponseDTO(
            Long usageCount,
            Long adminId,
            String adminName,
            Long storeId,
            String storeName
    ) {
        public static CountUsageResponseDTO from(Admin admin, Paper paper, Long total) {
            return CountUsageResponseDTO.builder()
                    .usageCount(total)
                    .adminId(admin.getId())
                    .adminName(admin.getName())
                    .storeId(paper.getStore().getId())
                    .storeName(paper.getStore().getName())
                    .build();
        }
    }

    @Builder
    public record CountUsageListResponseDTO(
            List<CountUsageResponseDTO> items
    ) {
        public static CountUsageListResponseDTO from(List<CountUsageResponseDTO> countUsageList) {
            return new CountUsageListResponseDTO(countUsageList);
        }
    }
}