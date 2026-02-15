package com.assu.server.domain.auth.dto.ssu;

import com.assu.server.domain.user.entity.enums.Major;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "유세인트 인증 응답")
public record USaintAuthResponseDTO(
        @Schema(description = "학번", example = "20211438")
        String studentNumber,
        
        @Schema(description = "이름", example = "홍길동")
        String name,
        
        @Schema(description = "학적 상태", example = "재학")
        String enrollmentStatus,
        
        @Schema(description = "학년/학기", example = "4학년 1학기")
        String yearSemester,
        
        @Schema(description = "전공/학과")
        Major major
) {
    public static USaintAuthResponseDTO of(
            String studentNumber,
            String name,
            String enrollmentStatus,
            String yearSemester,
            Major major
    ) {
        return new USaintAuthResponseDTO(studentNumber, name, enrollmentStatus, yearSemester, major);
    }
}
