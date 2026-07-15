package com.assu.server.domain.backoffice.dto;

import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BackofficeAdminCreateRequestDTO(
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다.")
    String password,

    @NotBlank(message = "이름은 필수입니다.")
    String name,

    String phoneNumber,

    @NotNull(message = "대학교는 필수입니다.")
    University university,

    @NotNull(message = "학과는 필수입니다.")
    Department department,

    @NotNull(message = "전공은 필수입니다.")
    Major major,

    String officeAddress,

    String detailAddress,

    Double latitude,

    Double longitude
) {
}
