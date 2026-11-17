package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.util.LogMaskUtil;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SSUAuthServiceImpl implements SSUAuthService {

    private final WebClient webClient;
    private final MeterRegistry meterRegistry;

    private static final String USaintSSOUrl = "https://saint.ssu.ac.kr/webSSO/sso.jsp";
    private static final String USaintPortalUrl = "https://saint.ssu.ac.kr/webSSUMain/main_student.jsp";

    private static final DefaultUriBuilderFactory URI_BUILDER_FACTORY = new DefaultUriBuilderFactory();

    // 조회(GET)라 멱등이므로 연결 실패/타임아웃 같은 일시적 오류에만 1회 재시도한다
    private static final Retry TRANSIENT_RETRY = Retry.backoff(1, Duration.ofMillis(500))
            .filter(throwable -> throwable instanceof WebClientRequestException);

    @Override
    public USaintAuthResponseDTO uSaintAuth(USaintAuthRequestDTO uSaintAuthRequest) {
        try {
            USaintAuthResponseDTO response = authenticate(uSaintAuthRequest);
            meterRegistry.counter("auth.ssu.sso", "result", "success").increment();
            return response;
        } catch (CustomAuthException e) {
            meterRegistry.counter("auth.ssu.sso", "result", resolveFailureResult(e)).increment();
            throw e;
        }
    }

    private String resolveFailureResult(CustomAuthException e) {
        return switch (e.getErrorReasonHttpStatus().getCode()) {
            case "SSU4000" -> "sso_failed";
            case "SSU4001" -> "portal_failed";
            case "SSU4002" -> "parse_failed";
            case "SSU4003" -> "unsupported_major";
            default -> "other";
        };
    }

    private USaintAuthResponseDTO authenticate(USaintAuthRequestDTO uSaintAuthRequest) {

        String sToken = uSaintAuthRequest.sToken();
        String sIdno = uSaintAuthRequest.sIdno();

        // 1) SSO 로그인 요청
        ResponseEntity<String> uSaintSSOResponseEntity;
        try {
            uSaintSSOResponseEntity = requestUSaintSSO(sToken, sIdno);
        } catch (Exception e) {
            log.error("API request to uSaint SSO failed. cause={}", summarize(e));
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_SSO_FAILED);
        }

        if (uSaintSSOResponseEntity == null || uSaintSSOResponseEntity.getBody() == null) {
            log.error("Empty response from USaint SSO. sToken={}, sIdno={}",
                    LogMaskUtil.mask(sToken), LogMaskUtil.mask(sIdno));
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_SSO_FAILED);
        }

        String body = uSaintSSOResponseEntity.getBody();
        if (!body.contains("location.href = \"/irj/portal\";")) {
            log.error("Invalid SSO response. sToken={}, sIdno={}",
                    LogMaskUtil.mask(sToken), LogMaskUtil.mask(sIdno));
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_SSO_FAILED);
        }

        HttpHeaders headers = uSaintSSOResponseEntity.getHeaders();
        List<String> setCookieList = headers.get(HttpHeaders.SET_COOKIE);

        StringBuilder uSaintPortalCookie = new StringBuilder();
        if (setCookieList != null) {
            for (String setCookie : setCookieList) {
                setCookie = setCookie.split(";")[0];
                uSaintPortalCookie.append(setCookie).append("; ");
            }
        }

        // 2) 포털 접근
        ResponseEntity<String> portalResponse;
        try {
            portalResponse = requestUSaintPortal(uSaintPortalCookie);
        } catch (Exception e) {
            log.error("API request to uSaint Portal failed. cause={}", summarize(e));
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_PORTAL_FAILED);
        }

        if (portalResponse == null || portalResponse.getBody() == null) {
            log.error("Empty response from uSaint Portal.");
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_PORTAL_FAILED);
        }

        String uSaintPortalResponseBody = portalResponse.getBody();

        String studentNumber = null;
        String name = null;
        String enrollmentStatus = null;
        String yearSemester = null;
        String majorStr = null;

        // 3) HTML 파싱
        Document doc;
        try {
            doc = Jsoup.parse(uSaintPortalResponseBody);
        } catch (Exception e) {
            log.error("Jsoup parsing failed.", e);
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_PARSE_FAILED);
        }

        Element nameBox = doc.getElementsByClass("main_box09").first();
        Element infoBox = doc.getElementsByClass("main_box09_con").first();

        if (nameBox == null || infoBox == null) {
            log.error("Portal HTML structure parsing failed.");
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_PARSE_FAILED);
        }

        Element span = nameBox.getElementsByTag("span").first();
        if (span == null || span.text().isEmpty()) {
            log.error("Student name span not found or empty.");
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_PARSE_FAILED);
        }
        name = span.text().split("님")[0];

        Elements infoLis = infoBox.getElementsByTag("li");
        for (Element li : infoLis) {
            Element dt = li.getElementsByTag("dt").first();
            Element strong = li.getElementsByTag("strong").first();

            if (dt == null || strong == null || strong.text().isEmpty()) {
                log.error("Missing dt/strong in infoBox.");
                throw new CustomAuthException(ErrorStatus.SSU_SAINT_PARSE_FAILED);
            }

            switch (dt.text()) {
                case "학번" -> {
                    try {
                        studentNumber = strong.text();
                    } catch (NumberFormatException e) {
                        log.error("Invalid studentId format.");
                        throw new CustomAuthException(ErrorStatus.SSU_SAINT_PARSE_FAILED);
                    }
                }
                case "소속" -> {
                    majorStr = strong.text();
                    if (!Major.existsByDisplayName(majorStr)) {
                        throw new CustomAuthException((ErrorStatus.SSU_SAINT_UNSUPPORTED_MAJOR));
                    }
                }
                case "과정/학기" -> enrollmentStatus = strong.text();
                case "학년/학기" -> yearSemester = strong.text();
            }
        }

        return USaintAuthResponseDTO.of(
                studentNumber,
                name,
                enrollmentStatus,
                yearSemester,
                majorStr
        );
    }

    // 예외 메시지에는 요청 URL(sToken 포함)이 들어갈 수 있어 종류만 남긴다
    private String summarize(Exception e) {
        if (e instanceof WebClientResponseException responseException) {
            return "HTTP " + responseException.getStatusCode().value();
        }
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private ResponseEntity<String> requestUSaintSSO(String sToken, String sIdno) {
        // 문자열로 넘기면 토큰이 든 URL 전체가 메트릭 uri 태그가 되므로 URI 객체로 넘긴다 (인코딩 방식은 기존과 동일)
        URI uri = URI_BUILDER_FACTORY.expand(USaintSSOUrl + "?sToken=" + sToken + "&sIdno=" + sIdno);

        return webClient.get()
                .uri(uri)
                .header("Cookie", "sToken=" + sToken + "; sIdno=" + sIdno)
                .retrieve()
                .toEntity(String.class) // ResponseEntity<String> 전체 반환 (body + header 포함)
                .retryWhen(TRANSIENT_RETRY)
                .block();
    }

    private ResponseEntity<String> requestUSaintPortal(StringBuilder cookie) {
        return webClient.get()
                .uri(USaintPortalUrl)
                .header(HttpHeaders.COOKIE, cookie.toString())
                .retrieve()
                .toEntity(String.class)
                .retryWhen(TRANSIENT_RETRY)
                .block();
    }
}
