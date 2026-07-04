package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeOutboxResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficePushLogResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficePushSendRequestDTO;
import com.assu.server.domain.common.dto.PageResponseDTO;

public interface BackofficeNotificationService {

    /** 그룹/개인 자유 메시지 푸시 발송 */
    void sendPush(BackofficePushSendRequestDTO request, Long sentByMemberId);

    /** 푸시 발송 이력 목록 조회 */
    PageResponseDTO<BackofficePushLogResponseDTO> getPushLogs(String keyword, int page, int size);

    /** 전송 실패(FAILED) 알림 목록 페이징 조회 */
    PageResponseDTO<BackofficeOutboxResponseDTO> getFailedOutboxes(int page, int size);

    /** 특정 실패 알림 수동 재전송 */
    void retryOutbox(Long outboxId);
}