package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.signup.AdminSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.PartnerBatchSignUpItemDTO;
import com.assu.server.domain.auth.dto.signup.PartnerSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.SignUpResponseDTO;
import com.assu.server.domain.auth.dto.signup.StudentTokenSignUpRequestDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SignUpService {
    SignUpResponseDTO signupSsuStudent(StudentTokenSignUpRequestDTO req);

    SignUpResponseDTO signupPartner(PartnerSignUpRequestDTO req, MultipartFile licenseImage);

    List<SignUpResponseDTO> signupBatchPartner(List<PartnerBatchSignUpItemDTO> requests);

    SignUpResponseDTO signupAdmin(AdminSignUpRequestDTO req, MultipartFile signImage);
}
