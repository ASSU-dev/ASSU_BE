package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;
import java.time.LocalDateTime;

public record BackofficeAdminUpdateResponseDTO(
    Long adminId,
    String email,
    String name,
    String phoneNumber,
    University university,
    Department department,
    Major major,
    String officeAddress,
    String detailAddress,
    LocalDateTime updatedAt
) {
}
