package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.dto.login.LoginResponseDTO;
import com.assu.server.domain.auth.dto.login.RefreshResponseDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;

public interface LoginService {
    LoginResponseDTO loginCommon(CommonLoginRequestDTO request);

    LoginResponseDTO loginSsuStudent(USaintAuthResponseDTO authResponse);

    RefreshResponseDTO refresh(String refreshToken);
}
