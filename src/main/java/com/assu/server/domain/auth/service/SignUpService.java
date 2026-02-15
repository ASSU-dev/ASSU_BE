package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.signup.AdminSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.PartnerSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.SignUpResponseDTO;
import com.assu.server.domain.auth.dto.signup.StudentTokenSignUpRequestDTO;
import org.springframework.web.multipart.MultipartFile;

public interface SignUpService {
    SignUpResponseDTO signupSsuStudent(StudentTokenSignUpRequestDTO req);

    SignUpResponseDTO signupPartner(PartnerSignUpRequestDTO req, MultipartFile licenseImage);

    SignUpResponseDTO signupAdmin(AdminSignUpRequestDTO req, MultipartFile signImage);
}
