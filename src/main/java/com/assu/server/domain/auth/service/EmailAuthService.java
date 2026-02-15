package com.assu.server.domain.auth.service;


import com.assu.server.domain.auth.dto.email.EmailVerificationCheckRequestDTO;

public interface EmailAuthService {
    void checkEmailAvailability(EmailVerificationCheckRequestDTO request);
}
