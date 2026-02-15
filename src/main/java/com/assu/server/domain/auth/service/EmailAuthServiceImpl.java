package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.email.EmailVerificationCheckRequestDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailAuthServiceImpl implements EmailAuthService {

    private final CommonAuthRepository commonAuthRepository;

    @Override
    public void checkEmailAvailability(EmailVerificationCheckRequestDTO request) {

        boolean exists = commonAuthRepository.existsByEmail(request.email());

        if (exists) {
            throw new CustomAuthException(ErrorStatus.EXISTED_EMAIL);
        }
    }
}
