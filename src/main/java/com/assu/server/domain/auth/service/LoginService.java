package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.dto.login.LoginResponseDTO;
import com.assu.server.domain.auth.dto.login.RefreshResponseDTO;
import com.assu.server.domain.auth.dto.signup.student.StudentTokenAuthPayloadDTO;

public interface LoginService {
    LoginResponseDTO loginCommon(CommonLoginRequestDTO request);

    LoginResponseDTO loginSsuStudent(StudentTokenAuthPayloadDTO request);

    RefreshResponseDTO refresh(String refreshToken);
}
