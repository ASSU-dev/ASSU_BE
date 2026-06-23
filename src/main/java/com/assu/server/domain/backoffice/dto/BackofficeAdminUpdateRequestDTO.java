package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;
import jakarta.validation.constraints.Email;

public record BackofficeAdminUpdateRequestDTO(
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    String email,

    String password,

    String name,

    String phoneNumber,

    University university,

    Department department,

    Major major,

    String officeAddress,

    String detailAddress,

    Double latitude,

    Double longitude
) {
}
