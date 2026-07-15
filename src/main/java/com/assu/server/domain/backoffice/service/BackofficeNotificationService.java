package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeOutboxResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficePushLogResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficePushSendRequestDTO;
import com.assu.server.domain.common.dto.PageResponseDTO;

public interface BackofficeNotificationService {

    void sendPush(BackofficePushSendRequestDTO request, Long sentByMemberId);

    PageResponseDTO<BackofficePushLogResponseDTO> getPushLogs(String keyword, int page, int size);

    PageResponseDTO<BackofficeOutboxResponseDTO> getFailedOutboxes(int page, int size);

    void retryOutbox(Long outboxId);
}