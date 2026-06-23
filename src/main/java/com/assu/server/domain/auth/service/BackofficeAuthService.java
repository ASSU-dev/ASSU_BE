package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.dto.login.RefreshResponseDTO;
import com.assu.server.domain.auth.dto.backoffice.BackofficeLoginResponseDTO;

public interface BackofficeAuthService {
    BackofficeLoginResponseDTO login(CommonLoginRequestDTO request);

    RefreshResponseDTO refresh(String refreshToken);
}
