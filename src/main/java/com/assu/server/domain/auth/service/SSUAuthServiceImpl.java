package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
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

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SSUAuthServiceImpl implements SSUAuthService {

    private final WebClient webClient;

    private static final String USaintSSOUrl = "https://saint.ssu.ac.kr/webSSO/sso.jsp";
    private static final String USaintPortalUrl = "https://saint.ssu.ac.kr/webSSUMain/main_student.jsp";

    @Override
    public USaintAuthResponseDTO uSaintAuth(USaintAuthRequestDTO uSaintAuthRequest) {

        String sToken = uSaintAuthRequest.sToken();
        String sIdno = uSaintAuthRequest.sIdno();

        // 1) SSO 로그인 요청
        ResponseEntity<String> uSaintSSOResponseEntity;
        try {
            uSaintSSOResponseEntity = requestUSaintSSO(sToken, sIdno);
        } catch (Exception e) {
            log.error("API request to uSaint SSO failed.", e);
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_SSO_FAILED);
        }

        if (uSaintSSOResponseEntity == null || uSaintSSOResponseEntity.getBody() == null) {
            log.error("Empty response from USaint SSO. sToken={}, sIdno={}", sToken, sIdno);
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_SSO_FAILED);
        }

        String body = uSaintSSOResponseEntity.getBody();
        if (!body.contains("location.href = \"/irj/portal\";")) {
            log.error("Invalid SSO response. sToken={}, sIdno={}", sToken, sIdno);
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
            log.error("API request to uSaint Portal failed.", e);
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_PORTAL_FAILED);
        }

        if (portalResponse == null || portalResponse.getBody() == null) {
            log.error("Empty response from uSaint Portal. cookie={}", uSaintPortalCookie);
            throw new CustomAuthException(ErrorStatus.SSU_SAINT_PORTAL_FAILED);
        }

        String uSaintPortalResponseBody = portalResponse.getBody();

        String studentNumber = null;
        String name = null;
        String enrollmentStatus = null;
        String yearSemester = null;
        Major major = null;

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
            log.debug(uSaintPortalResponseBody);
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
                log.error("Missing dt/strong in infoBox. li={}", li);
                throw new CustomAuthException(ErrorStatus.SSU_SAINT_PARSE_FAILED);
            }

            switch (dt.text()) {
                case "학번" -> {
                    try {
                        studentNumber = strong.text();
                    } catch (NumberFormatException e) {
                        log.error("Invalid studentId format: {}", strong.text());
                        throw new CustomAuthException(ErrorStatus.SSU_SAINT_PARSE_FAILED);
                    }
                }
                case "소속" -> {
                    String majorStr = strong.text();
                    major = Major.fromDisplayName(majorStr);
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
                major
        );
    }

    private ResponseEntity<String> requestUSaintSSO(String sToken, String sIdno) {
        String url = USaintSSOUrl + "?sToken=" + sToken + "&sIdno=" + sIdno;

        return webClient.get()
                .uri(url)
                .header("Cookie", "sToken=" + sToken + "; sIdno=" + sIdno)
                .retrieve()
                .toEntity(String.class) // ResponseEntity<String> 전체 반환 (body + header 포함)
                .block();
    }

    private ResponseEntity<String> requestUSaintPortal(StringBuilder cookie) {
        return webClient.get()
                .uri(USaintPortalUrl)
                .header(HttpHeaders.COOKIE, cookie.toString())
                .retrieve()
                .toEntity(String.class)
                .block();
    }
}
