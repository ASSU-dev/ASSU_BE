package com.assu.server.infra.discord;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class DiscordNotifier {

    private final RestClient restClient;

    @Value("${discord.webhook.service-alert:}")
    private String serviceAlertWebhookUrl;

    public DiscordNotifier(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    public void sendInquiryAlert() {
        send("📬 새 문의가 접수되었습니다. 백오피스에서 확인해 주세요.");
    }

    private void send(String message) {
        if (serviceAlertWebhookUrl.isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri(serviceAlertWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("[Discord] 알림 전송 실패 message={}", message, e);
        }
    }
}