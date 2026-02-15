package com.assu.server.domain.mapping.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class StudentAdminResponseDTO {


    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CountAdminAuthResponseDTO{ // admin에 따른 총 누적 가입자 수
        private Long studentCount;
        private Long adminId;
        private String adminName;
    }
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NewCountAdminResponseDTO{ //신규 가입자수 (매달 1일 초기화)
        private Long newStudentCount;
        private Long adminId;
        private String adminName;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CountUsagePersonResponseDTO{
        private Long usagePersonCount;
        private Long adminId;
        private String adminName;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CountUsageResponseDTO{ //제휴 업체별 누적 제휴 이용현황
        private Long usageCount;
        private Long adminId;
        private String adminName;
        private Long storeId;
        private String storeName;

    }
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CountUsageListResponseDTO {
        private List<CountUsageResponseDTO> items; // 사용량 내림차순 정렬됨
    }
    public record StoreUsageWithPaper(
            Long paperId,
            Long storeId,
            String storeName,
            Long usageCount
    ) {}

}
