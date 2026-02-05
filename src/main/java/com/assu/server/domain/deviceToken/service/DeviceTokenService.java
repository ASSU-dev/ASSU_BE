package com.assu.server.domain.deviceToken.service;

import java.util.List;

public interface DeviceTokenService {
    Long register(String token, Long memberId);
    void unregister(Long tokenId, Long memberId);
    void deactivateTokens(List<String> invalidTokens);
}
