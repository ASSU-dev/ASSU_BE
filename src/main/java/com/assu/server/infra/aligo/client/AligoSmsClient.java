package com.assu.server.infra.aligo.client;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.aligo.dto.AligoSendResponse;
import com.assu.server.infra.aligo.exception.AligoException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AligoSmsClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${aligo.key}")
    private String apiKey;

    @Value("${aligo.user-id}")
    private String userId;

    @Value("${aligo.sender}")
    private String sender;

    private static final String SEND_URL = "https://apis.aligo.in/send/";

    public AligoSendResponse sendSms(String phoneNumber, String message, String name) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("key", apiKey);
        params.add("user_id", userId);
        params.add("sender", sender);
        params.add("receiver", phoneNumber);
        params.add("msg", message);
        params.add("msg_type", "SMS");
        params.add("destination", phoneNumber + "|" + name);

        String body = webClient.post()
                .uri(SEND_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(params))
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(errorBody -> {
                                    log.error("Aligo API 호출 실패. status={}, body={}", clientResponse.statusCode(), errorBody);
                                    return Mono.error(new AligoException(ErrorStatus.FAILED_TO_SEND_SMS));
                                })
                )
                .bodyToMono(String.class)
                .block();

        try {
            return objectMapper.readerFor(AligoSendResponse.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(body);
        } catch (Exception e) {
            log.error("Aligo 응답 파싱 실패. 원본 body: {}", body, e);
            throw new AligoException(ErrorStatus.FAILED_TO_PARSE_ALIGO);
        }
    }
}

