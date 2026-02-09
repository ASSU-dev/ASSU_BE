package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;

public interface SSUAuthService {
    USaintAuthResponseDTO uSaintAuth(USaintAuthRequestDTO uSaintAuthRequest);
}
