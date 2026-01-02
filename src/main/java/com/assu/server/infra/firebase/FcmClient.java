package com.assu.server.infra.firebase;

import com.assu.server.domain.deviceToken.entity.DeviceToken;
import com.assu.server.domain.deviceToken.repository.DeviceTokenRepository;
import com.assu.server.domain.deviceToken.service.DeviceTokenService;
import com.google.api.core.ApiFuture;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class FcmClient {

    private final FirebaseMessaging messaging;
    private final DeviceTokenRepository tokenRepo;
    private final DeviceTokenService deviceTokenService;

    // 운영에서 3s는 다소 공격적 — 5s 정도 권장
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);

    /**
     * 멤버의 활성 토큰 전체에 멀티캐스트 전송.
     * - 실패 토큰(UNREGISTERED/INVALID_ARGUMENT)은 즉시 비활성화
     * - 결과 요약을 반환
     */

    public FcmResult sendToMemberId(Long memberId, String title, String body, Map<String, String> data)
            throws TimeoutException, InterruptedException, FirebaseMessagingException, ExecutionException {
        if (memberId == null) throw new IllegalArgumentException("receiverId is null");

        // 1) 토큰 조회
        List<DeviceToken> activeTokens = tokenRepo.findAllByMemberIdAndActiveTrue(memberId);
        List<String> tokens = activeTokens.stream().map(DeviceToken::getToken).toList();
        if (tokens.isEmpty()) {
            return FcmResult.empty();
        }

        // 2) 널 세이프
        final String _title = title == null ? "" : title;
        final String _body  = body  == null ? "" : body;

        String type           = data != null ? data.getOrDefault("type", "") : "";
        String refId          = data != null ? data.getOrDefault("refId", "") : "";
        String deeplink       = data != null ? data.getOrDefault("deeplink", "") : "";
        String notificationId = data != null ? data.getOrDefault("notificationId", "") : "";

        com.google.firebase.messaging.MulticastMessage msg =
                com.google.firebase.messaging.MulticastMessage.builder()
                        .addAllTokens(tokens)
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build())
                        .putData("title", _title)
                        .putData("body",  _body)
                        .putData("type",  type)
                        .putData("refId", refId)
                        .putData("deeplink", deeplink)
                        .putData("notificationId", notificationId)
                        .build();

        try {
            ApiFuture<BatchResponse> future = messaging.sendEachForMulticastAsync(msg);
            BatchResponse br = future.get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            int success = 0, fail = 0;
            List<String> invalidTokens = new ArrayList<>();

            List<SendResponse> responses = br.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse r = responses.get(i);
                if (r.isSuccessful()) {
                    success++;
                } else {
                    fail++;
                    FirebaseMessagingException fme = r.getException(); // per-token 예외
                    if (fme != null && (
                            fme.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                                    fme.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT)) {
                        invalidTokens.add(tokens.get(i));
                    }
                    log.warn("[FCM] per-token fail memberId={} idx={} code={} root={}",
                            memberId, i,
                            (fme != null ? fme.getMessagingErrorCode() : null),
                            rootSummary(fme));
                }
            }

            if (!invalidTokens.isEmpty()) {
                try {
                    deviceTokenService.deactivateTokens(invalidTokens);
                } catch (Exception e) {
                    log.error("[FCM] deactivateTokens failed size={} memberId={} root={}",
                            invalidTokens.size(), memberId, rootSummary(e), e);
                }
            }

            return new FcmResult(success, fail, invalidTokens);

        } catch (TimeoutException te) {
            log.warn("[FCM] timeout ({} ms) memberId={}", SEND_TIMEOUT.toMillis(), memberId);
            throw te;

        } catch (ExecutionException ee) {
            // ★ 핵심: Future가 싸서 던진 예외를 원형으로 복원
            Throwable c = ee.getCause();
            if (c instanceof FirebaseMessagingException fme) {
                log.error("[FCM] FirebaseMessagingException memberId={} http={} code={} root={}",
                        memberId, httpStatusOf(fme), fme.getMessagingErrorCode(), rootSummary(fme), fme);
                throw fme; // 리스너에서 코드/HTTP 기반 분류 가능
            }
            throw ee; // 그 외는 그대로 위로

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw ie;
        }
    }

    public static Integer httpStatusOf(com.google.firebase.messaging.FirebaseMessagingException fme) {
        try {
            var resp = fme.getHttpResponse();
            return resp != null ? resp.getStatusCode() : null;
        } catch (Throwable ignore) { return null; }
    }

    private String rootSummary(Throwable t) {
        if (t == null) return "null";
        Throwable r = t; while (r.getCause() != null) r = r.getCause();
        return r.getClass().getName() + ": " + String.valueOf(r.getMessage());
    }

    public record FcmResult(int successCount, int failureCount, List<String> invalidTokens) {
        static FcmResult empty() { return new FcmResult(0, 0, java.util.List.of()); }
    }
}