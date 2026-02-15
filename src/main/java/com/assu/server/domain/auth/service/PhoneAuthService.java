package com.assu.server.domain.auth.service;

public interface PhoneAuthService {
    void checkAndSendAuthNumber(String phoneNumber);

    void verifyAuthNumber(String phoneNumber, String authNumber);
}
