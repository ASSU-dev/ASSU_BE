package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.user.entity.enums.Major;
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
        com.assu.server.domain.user.entity.enums.Major major = null;

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

                    switch (majorStr) {
                        // 인문대학
                        case "기독교학과" -> major = Major.CHRISTIAN_STUDIES;
                        case "국어국문학과" -> major = Major.KOREAN_LITERATURE;
                        case "영어영문학과" -> major = Major.ENGLISH_LITERATURE;
                        case "독어독문학과" -> major = Major.GERMAN_LITERATURE;
                        case "불어불문학과" -> major = Major.FRENCH_LITERATURE;
                        case "중어중문학과" -> major = Major.CHINESE_LITERATURE;
                        case "일어일문학과" -> major = Major.JAPANESE_LITERATURE;
                        case "철학과" -> major = Major.PHILOSOPHY;
                        case "사학과" -> major = Major.HISTORY;
                        case "예술창작학부" -> major = Major.CREATIVE_ARTS;
                        case "스포츠학부" -> major = Major.SPORTS;

                        // 자연과학대학
                        case "수학과" -> major = Major.MATHEMATICS;
                        case "화학과" -> major = Major.CHEMISTRY;
                        case "의생명시스템학부" -> major = Major.BIOMEDICAL_SYSTEMS;
                        case "물리학과" -> major = Major.PHYSICS;
                        case "정보통계ㆍ보험수리학과" -> major = Major.STATISTICS_ACTUARIAL;

                        // 법과대학
                        case "법학과" -> major = Major.LAW;
                        case "국제법무학과" -> major = Major.INTERNATIONAL_LAW;

                        // 사회과학대학
                        case "사회복지학부" -> major = Major.SOCIAL_WELFARE;
                        case "정치외교학과" -> major = Major.POLITICAL_SCIENCE;
                        case "언론홍보학과" -> major = Major.MEDIA_COMMUNICATION;
                        case "행정학부" -> major = Major.PUBLIC_ADMINISTRATION;
                        case "정보사회학과" -> major = Major.INFORMATION_SOCIETY;
                        case "평생교육학과" -> major = Major.LIFELONG_EDUCATION;

                        // 경제통상대학
                        case "경제학과" -> major = Major.ECONOMICS;
                        case "금융경제학과" -> major = Major.FINANCIAL_ECONOMICS;
                        case "글로벌통상학과" -> major = Major.GLOBAL_TRADE;
                        case "국제무역학과" -> major = Major.INTERNATIONAL_TRADE;

                        // 경영대학
                        case "경영학부" -> major = Major.BUSINESS_ADMINISTRATION;
                        case "회계학과" -> major = Major.ACCOUNTING;
                        case "벤처경영학과" -> major = Major.VENTURE_MANAGEMENT;
                        case "복지경영학과" -> major = Major.WELFARE_MANAGEMENT;
                        case "벤처중소기업학과" -> major = Major.VENTURE_SME;
                        case "금융학부" -> major = Major.FINANCE;
                        case "혁신경영학과" -> major = Major.INNOVATION_MANAGEMENT;
                        case "회계세무학과" -> major = Major.ACCOUNTING_TAX;

                        // 공과대학
                        case "화학공학과" -> major = Major.CHEMICAL_ENGINEERING;
                        case "전기공학부" -> major = Major.ELECTRICAL_ENGINEERING;
                        case "건축학부" -> major = Major.ARCHITECTURE;
                        case "산업ㆍ정보시스템공학과" -> major = Major.INDUSTRIAL_INFO_SYSTEMS;
                        case "기계공학부" -> major = Major.MECHANICAL_ENGINEERING;
                        case "신소재공학과" -> major = Major.MATERIALS_SCIENCE;

                        // IT대학
                        case "컴퓨터학부" -> major = Major.COM;
                        case "소프트웨어학부" -> major = Major.SW;
                        case "글로벌미디어학부" -> major = Major.GM;
                        case "미디어경영학과" -> major = Major.MB;
                        case "AI융합학부" -> major = Major.AI;
                        case "전자정보공학부" -> major = Major.EE;
                        case "정보보호학과" -> major = Major.IP;

                        // 자유전공학부
                        case "자유전공학부" -> major = Major.LIBERAL_ARTS;

                        default -> {
                            log.debug("{} is not a supported major.", majorStr);
                            throw new CustomAuthException(ErrorStatus.SSU_SAINT_UNSUPPORTED_MAJOR);
                        }
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
