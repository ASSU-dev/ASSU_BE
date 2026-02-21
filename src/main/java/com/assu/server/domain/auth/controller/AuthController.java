package com.assu.server.domain.auth.controller;

import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.dto.login.LoginResponseDTO;
import com.assu.server.domain.auth.dto.login.RefreshResponseDTO;
import com.assu.server.domain.auth.dto.phone.PhoneAuthSendRequestDTO;
import com.assu.server.domain.auth.dto.phone.PhoneAuthVerifyRequestDTO;
import com.assu.server.domain.auth.dto.signup.AdminSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.PartnerSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.SignUpResponseDTO;
import com.assu.server.domain.auth.dto.signup.StudentTokenSignUpRequestDTO;
import com.assu.server.domain.auth.dto.signup.student.StudentTokenAuthPayloadDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthRequestDTO;
import com.assu.server.domain.auth.dto.ssu.USaintAuthResponseDTO;
import com.assu.server.domain.auth.dto.email.EmailVerificationCheckRequestDTO;
import com.assu.server.domain.auth.service.*;
import com.assu.server.domain.user.entity.enums.University;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Auth", description = "인증/인가 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final PhoneAuthService phoneAuthService;
    private final EmailAuthService emailAuthService;
    private final SignUpService signUpService;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final SSUAuthService ssuAuthService;
    private final WithdrawalService withdrawalService;

    @Operation(
            summary = "휴대폰 번호 중복가입 확인 및 인증번호 발송 API",
            description = "# [v1.1 (2025-09-25)](https://clumsy-seeder-416.notion.site/2241197c19ed801bbcd9f61c3e5f5457?source=copy_link)\n" +
                    "- 입력한 휴대폰 번호로 1회용 인증번호(OTP)를 발송합니다.\n" +
                    "- 중복된 전화번호가 있으면 에러를 반환합니다.\n" +
                    "- 유효시간/재요청 제한 정책은 서버 설정에 따릅니다.\n" +
                    "\n**Request Body:**\n" +
                    "  - `phoneNumber` (String, required): 인증번호를 받을 휴대폰 번호\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 성공 메시지 반환"
    )
    @PostMapping("/phone-verification/check-and-send")
    public BaseResponse<Void> checkPhoneAvailabilityAndSendAuthNumber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "휴대폰 인증번호 발송 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PhoneAuthSendRequestDTO.class)
                    )
            )
            @RequestBody
            @Valid
            PhoneAuthSendRequestDTO request
    ) {
        phoneAuthService.checkAndSendAuthNumber(request.phoneNumber());
        return BaseResponse.onSuccess(SuccessStatus.SEND_AUTH_NUMBER_SUCCESS, null);
    }

    @Operation(
            summary = "휴대폰 인증번호 검증 API",
            description = "# [v1.0 (2025-09-03)](https://clumsy-seeder-416.notion.site/2241197c19ed81bb8c05d9061c0306c0?source=copy_link)\n" +
                    "- 발송된 인증번호(OTP)를 검증합니다.\n" +
                    "- 성공 시 서버에 휴대폰 인증 상태가 기록됩니다.\n" +
                    "\n**Request Body:**\n" +
                    "  - `phoneNumber` (String, required): 인증받을 휴대폰 번호\n" +
                    "  - `authNumber` (String, required): 발송받은 인증번호(OTP)\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 성공 메시지 반환"
    )
    @PostMapping("/phone-verification/verify")
    public BaseResponse<Void> checkAuthNumber(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "휴대폰 인증번호 검증 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PhoneAuthVerifyRequestDTO.class)
                    )
            )
            @RequestBody
            @Valid
            PhoneAuthVerifyRequestDTO request
    ) {
        phoneAuthService.verifyAuthNumber(
                request.phoneNumber(),
                request.authNumber()
        );
        return BaseResponse.onSuccess(SuccessStatus.VERIFY_AUTH_NUMBER_SUCCESS, null);
    }

    @Operation(
            summary = "이메일 형식 및 중복가입 확인 API",
            description = "# [v1.0 (2025-09-18)](https://clumsy-seeder-416.notion.site/2551197c19ed802d8f6dd373dd045f3a?source=copy_link)\n" +
                    "- 입력한 이메일이 이미 가입된 사용자가 있는지 확인합니다.\n" +
                    "- 중복된 이메일이 있으면 에러를 반환합니다.\n" +
                    "\n**Request Body:**\n" +
                    "  - `email` (String, required): 확인할 이메일 주소\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 사용 가능 메시지 반환\n" +
                    "  - 중복 시 404(NOT_FOUND)와 에러 메시지 반환"
    )
    @PostMapping("/email-verification/check")
    public BaseResponse<Void> checkEmailAvailability(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "이메일 중복 확인 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EmailVerificationCheckRequestDTO.class)
                    )
            )
            @RequestBody
            @Valid
            EmailVerificationCheckRequestDTO request
    ) {
        emailAuthService.checkEmailAvailability(request);
        return BaseResponse.onSuccess(SuccessStatus._OK, null);
    }

    @Operation(
            summary = "학생 회원가입 API",
            description = "# [v1.2 (2025-09-13)](https://clumsy-seeder-416.notion.site/2241197c19ed81129c85cf5bbe1f7971)\n" +
                    "- `application/json` 요청 바디를 사용합니다.\n" +
                    "- 처리: 유세인트 인증 → 학생 정보 추출 → 회원가입 완료\n" +
                    "- 성공 시 201(Created)과 생성된 memberId, JWT 토큰, 기본 정보 반환.\n" +
                    "\n**Request Body:**\n" +
                    "  - `StudentTokenSignUpRequest` 객체 (JSON, required): 숭실대 학생 토큰 가입 정보\n" +
                    "  - `phoneNumber` (String, required): 휴대폰 번호\n" +
                    "  - `marketingAgree` (Boolean, required): 마케팅 수신 동의\n" +
                    "  - `locationAgree` (Boolean, required): 위치 정보 수집 동의\n" +
                    "  - `StudentTokenAuthPayload` (Object, required): 유세인트 토큰 정보\n" +
                    "    - `sToken` (String, required): 유세인트 sToken\n" +
                    "    - `sIdno` (Integer, required): 유세인트 sIdno\n" +
                    "    - `university` (University enum, required): 대학 이름 (SSU)\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 201(Created)과 `SignUpResponse` 객체 반환\n" +
                    "  - `memberId` (Long): 회원 ID\n" +
                    "  - `role` (UserRole): 회원 역할 (STUDENT)\n" +
                    "  - `status` (ActivationStatus): 회원 상태 (ACTIVE)\n" +
                    "  - `tokens` (Object): JWT 토큰 정보 (accessToken, refreshToken, expiresAt)\n" +
                    "  - `basicInfo` (UserBasicInfo): 사용자 기본 정보 (프론트 캐싱용)\n" +
                    "    - `name` (String): 학생 이름\n" +
                    "    - `university` (String): 대학교 (한글명)\n" +
                    "    - `department` (String): 단과대 (한글명)\n" +
                    "    - `major` (String): 전공/학과 (한글명)"
    )
    @PostMapping(value = "/students/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<SignUpResponseDTO> signupStudent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "JSON 형식의 학생 유저 가입 정보",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = StudentTokenSignUpRequestDTO.class)
                    )
            )
            @RequestBody
            @Valid
            StudentTokenSignUpRequestDTO request
    ) {
        SignUpResponseDTO response;
        if(request.studentTokenAuth().university().equals(University.SSU)){
            response = signUpService.signupSsuStudent(request);
        } else {
            response = null;
        }
        return BaseResponse.onSuccess(SuccessStatus._OK, response);
    }

    @Operation(
            summary = "제휴업체 회원가입 API",
            description = "# [v1.2 (2025-09-13)](https://clumsy-seeder-416.notion.site/2501197c19ed80d7a8f2c3a6fcd8b537)\n" +
                    "- `multipart/form-data`로 호출합니다.\n" +
                    "- 파트: `payload`(JSON, PartnerSignUpRequest) + `licenseImage`(파일, 사업자등록증).\n" +
                    "- 처리: users + common_auth 생성, 이메일 중복/비밀번호 규칙 검증.\n" +
                    "- 성공 시 201(Created)과 생성된 memberId, JWT 토큰, 기본 정보 반환.\n" +
                    "\n**Request Parts:**\n" +
                    "  - `request` (JSON, required): `PartnerSignUpRequest` 객체\n" +
                    "  - `email` (String, required): 이메일 주소\n" +
                    "  - `password` (String, required): 비밀번호\n" +
                    "  - `phoneNumber` (String, required): 휴대폰 번호\n" +
                    "  - `companyName` (String, required): 회사명\n" +
                    "  - `businessNumber` (String, required): 사업자등록번호\n" +
                    "  - `representativeName` (String, required): 대표자명\n" +
                    "  - `address` (String, required): 회사 주소\n" +
                    "  - `licenseImage` (MultipartFile, required): 사업자등록증 이미지 파일\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 201(Created)과 `SignUpResponse` 객체 반환\n" +
                    "  - `memberId` (Long): 회원 ID\n" +
                    "  - `role` (UserRole): 회원 역할 (PARTNER)\n" +
                    "  - `status` (ActivationStatus): 회원 상태 (ACTIVE)\n" +
                    "  - `tokens` (Object): JWT 토큰 정보 (accessToken, refreshToken, expiresAt)\n" +
                    "  - `basicInfo` (UserBasicInfo): 사용자 기본 정보 (프론트 캐싱용)\n" +
                    "    - `name` (String): 업체명\n" +
                    "    - `university`, `department`, `major`: null (Partner는 해당 없음)"
    )
    @PostMapping(value = "/partners/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<SignUpResponseDTO> signupPartner(
            @RequestPart("request")
            @Parameter(
                    description = "JSON 형식의 제휴업체 가입 정보",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PartnerSignUpRequestDTO.class)
                    )
            )
            @Valid
            PartnerSignUpRequestDTO request,
            @RequestPart("licenseImage")
            @Parameter(
                    description = "사업자등록증 이미지 파일",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
            MultipartFile licenseImage
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, signUpService.signupPartner(request, licenseImage));
    }

    @Operation(
            summary = "관리자 회원가입 API",
            description = "# [v1.2 (2025-09-13)](https://clumsy-seeder-416.notion.site/2501197c19ed80cdb98bc2b4d5042b48)\n" +
                    "- `multipart/form-data`로 호출합니다.\n" +
                    "- 파트: `payload`(JSON, AdminSignUpRequest) + `signImage`(파일, 신분증).\n" +
                    "- 처리: users + common_auth 생성, 이메일 중복/비밀번호 규칙 검증.\n" +
                    "- 성공 시 201(Created)과 생성된 memberId, JWT 토큰, 기본 정보 반환.\n" +
                    "\n**Request Parts:**\n" +
                    "  - `request` (JSON, required): `AdminSignUpRequest` 객체\n" +
                    "  - `email` (String, required): 이메일 주소\n" +
                    "  - `password` (String, required): 비밀번호\n" +
                    "  - `university` (String, required): 대학교 Enum\n" +
                    "  - `department` (String, required): 단과대 Enum\n" +
                    "  - `major` (String, required): 전공 Enum\n" +
                    "  - `phoneNumber` (String, required): 휴대폰 번호\n" +
                    "  - `name` (String, required): 관리자 이름\n" +
                    "  - `department` (String, required): 소속 부서\n" +
                    "  - `position` (String, required): 직책\n" +
                    "  - `signImage` (MultipartFile, required): 인감 이미지 파일\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 201(Created)과 `SignUpResponse` 객체 반환\n" +
                    "  - `memberId` (Long): 회원 ID\n" +
                    "  - `role` (UserRole): 회원 역할 (ADMIN)\n" +
                    "  - `status` (ActivationStatus): 회원 상태 (ACTIVE)\n" +
                    "  - `tokens` (Object): JWT 토큰 정보 (accessToken, refreshToken, expiresAt)\n" +
                    "  - `basicInfo` (UserBasicInfo): 사용자 기본 정보 (프론트 캐싱용)\n" +
                    "    - `name` (String): 단체명/관리자 이름\n" +
                    "    - `university` (String): 대학교 (한글명)\n" +
                    "    - `department` (String): 단과대 (한글명)\n" +
                    "    - `major` (String): 전공/학과 (한글명)"
    )
    @PostMapping(value = "/admins/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<SignUpResponseDTO> signupAdmin(
            @RequestPart("request")
            @Parameter(
                    description = "JSON 형식의 관리자 가입 정보",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AdminSignUpRequestDTO.class)
                    )
            )
            @Valid
            AdminSignUpRequestDTO request,
            @RequestPart("signImage")
            @Parameter(
                    description = "인감 이미지 파일 (Multipart Part)",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
            MultipartFile signImage
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, signUpService.signupAdmin(request, signImage));
    }

    @Operation(
            summary = "공통 로그인 API",
            description = "# [v1.1 (2025-09-13)](https://clumsy-seeder-416.notion.site/2241197c19ed811c961be6a474de0e50)\n" +
                    "- `application/json`로 호출합니다.\n" +
                    "- 바디: `LoginRequest(email, password)`.\n" +
                    "- 처리: 자격 증명 검증 후 Access/Refresh 토큰 발급 및 저장.\n" +
                    "- 성공 시 200(OK)과 토큰, 만료시각, 기본 정보 반환.\n" +
                    "\n**Request Body:**\n" +
                    "  - `CommonLoginRequest` 객체 (JSON, required): 로그인 정보\n" +
                    "  - `email` (String, required): 이메일 주소\n" +
                    "  - `password` (String, required): 비밀번호\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 `LoginResponse` 객체 반환\n" +
                    "  - `memberId` (Long): 회원 ID\n" +
                    "  - `role` (UserRole): 회원 역할 (PARTNER/ADMIN)\n" +
                    "  - `status` (ActivationStatus): 회원 상태\n" +
                    "  - `tokens` (Object): JWT 토큰 정보 (accessToken, refreshToken, expiresAt)\n" +
                    "  - `basicInfo` (UserBasicInfo): 사용자 기본 정보 (프론트 캐싱용)\n" +
                    "    - `name` (String): 업체명/단체명/관리자 이름\n" +
                    "    - `university`, `department`, `major`: Admin의 경우 한글명, Partner의 경우 null"
    )
    @PostMapping(value = "/commons/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<LoginResponseDTO> loginCommon(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "공통 로그인 요청 (파트너/관리자)",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CommonLoginRequestDTO.class)
                    )
            )
            @RequestBody
            @Valid
            CommonLoginRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, loginService.loginCommon(request));
    }

    @Operation(
            summary = "학생 로그인 API",
            description = "# [v1.2 (2025-09-13)](https://clumsy-seeder-416.notion.site/2501197c19ed80f6b495fa37f8c084a8)\n" +
                    "- `application/json`로 호출합니다.\n" +
                    "- 바디: `StudentTokenLoginRequest(sToken, sIdno, university)`.\n" +
                    "- 처리: 유세인트 인증 → 기존 회원 확인 → JWT 토큰 발급.\n" +
                    "- 성공 시 200(OK)과 토큰, 만료시각, 기본 정보 반환.\n" +
                    "\n**Request Body:**\n" +
                    "  - `StudentTokenAuthPayload` 객체 (JSON, required): 숭실대 학생 토큰 로그인 정보\n" +
                    "  - `sToken` (String, required): 유세인트 sToken\n" +
                    "  - `sIdno` (Integer, required): 유세인트 sIdno\n" +
                    "  - `university` (University enum, required): 대학 이름 (SSU)\n" +
                   "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 `LoginResponse` 객체 반환\n" +
                    "  - `memberId` (Long): 회원 ID\n" +
                    "  - `role` (UserRole): 회원 역할 (STUDENT)\n" +
                    "  - `status` (ActivationStatus): 회원 상태\n" +
                    "  - `tokens` (Object): JWT 토큰 정보 (accessToken, refreshToken, expiresAt)\n" +
                    "  - `basicInfo` (UserBasicInfo): 사용자 기본 정보 (프론트 캐싱용)\n" +
                    "    - `name` (String): 학생 이름\n" +
                    "    - `university` (String): 대학교 (한글명)\n" +
                    "    - `department` (String): 단과대 (한글명)\n" +
                    "    - `major` (String): 전공/학과 (한글명)"
    )
    @PostMapping(value = "/students/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<LoginResponseDTO> loginStudent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "학생 토큰 로그인 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = StudentTokenAuthPayloadDTO.class)
                    )
            )
            @RequestBody
            @Valid
            StudentTokenAuthPayloadDTO request
    ) {
        LoginResponseDTO response;
        if(request.university().equals(University.SSU)){
            response = loginService.loginSsuStudent(request);
        } else {
            response = null;
        }
        return BaseResponse.onSuccess(SuccessStatus._OK, response);
    }

    @Operation(
            summary = "Access Token 갱신 API",
            description = "# [v1.0 (2025-09-03)](https://clumsy-seeder-416.notion.site/2501197c19ed806ea8cff29f9cd8695a?source=copy_link)\n" +
                    "- 헤더로 호출합니다.\n" +
                    "- 헤더: `Authorization: Bearer <accessToken>`(만료 허용), `RefreshToken: <refreshToken>`.\n" +
                    "- 처리: Refresh 검증/회전 후 신규 Access/Refresh 발급 및 저장.\n" +
                    "- 성공 시 200(OK)과 새 토큰/만료시각 반환.\n" +
                    "\n**Headers:**\n" +
                    "  - `Authorization` (String, required): Bearer 토큰 형식의 액세스 토큰 (만료 허용)\n" +
                    "  - `RefreshToken` (String, required): 리프레시 토큰\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 `RefreshResponse` 객체 반환\n" +
                    "  - 성공 시 200(OK)과 `RefreshResponse` 객체 반환\n" +
                    "  - `accessToken` (String): 새로운 액세스 토큰\n" +
                    "  - `refreshToken` (String): 새로운 리프레시 토큰\n" +
                    "  - `expiresAt` (LocalDateTime): 새 토큰 만료 시각"
    )
    @PostMapping("/tokens/refresh")
    public BaseResponse<RefreshResponseDTO> refreshToken(
            @Parameter(
                    name = "RefreshToken",
                    description = "Refresh Token",
                    required = true,
                    in = ParameterIn.HEADER,
                    schema = @Schema(type = "string")
            )
            @RequestHeader("RefreshToken")
            String refreshToken
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, loginService.refresh(refreshToken));
    }

    @Operation(
            summary = "로그아웃 API",
            description = "# [v1.0 (2025-09-03)](https://clumsy-seeder-416.notion.site/23a1197c19ed809e9a09fcd741f554c8?source=copy_link)\n" +
                    "- 헤더로 호출합니다.\n" +
                    "- 헤더: `Authorization: Bearer <accessToken>`.\n" +
                    "- 처리: Refresh 무효화(선택), Access 블랙리스트 등록.\n" +
                    "- 성공 시 200(OK)."
    )
    @PostMapping("/logout")
    public BaseResponse<Void> logout(
            @Parameter(
                    name = "Authorization",
                    description = "Access Token. 형식: `Bearer <token>`",
                    required = true,
                    in = ParameterIn.HEADER,
                    schema = @Schema(type = "string")
            )
            @RequestHeader("Authorization")
            String authorization
    ) {
        logoutService.logout(authorization);
        return BaseResponse.onSuccess(SuccessStatus._OK, null);
    }

    @Operation(
            summary = "숭실대 유세인트 인증 API",
            description = "# [v1.0 (2025-09-03)](https://clumsy-seeder-416.notion.site/23a1197c19ed808d9266e641e5c4ea14?source=copy_link)\n" +
                    "- `application/json`으로 호출합니다.\n" +
                    "- 요청 바디: `USaintAuthRequest(sToken, sIdno)`.\n" +
                    "- 처리 순서:\n" +
                    "  1) 유세인트 SSO 로그인 시도 (sToken, sIdno 검증)\n" +
                    "  2) 응답 Body 검증 후 세션 쿠키 추출\n" +
                    "  3) 유세인트 포털 페이지 접근 및 HTML 파싱\n" +
                    "  4) 이름, 학번, 소속, 학적 상태, 학년/학기 정보 추출\n" +
                    "  5) 소속 문자열을 전공 Enum(`Major`)으로 매핑\n" +
                    "  6) 인증 결과를 `USaintAuthResponse` DTO로 반환"
    )
    @PostMapping(value = "/students/ssu-verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<USaintAuthResponseDTO> ssuAuth(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "유세인트 인증 요청",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = USaintAuthRequestDTO.class)
                    )
            )
            @RequestBody
            @Valid
            USaintAuthRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, ssuAuthService.uSaintAuth(request));
    }

    @Operation(
            summary = "회원 탈퇴 API",
            description = "# [v1.0 (2025-09-13)](https://clumsy-seeder-416.notion.site/2501197c19ed800a844bdafa2e2e8d2e?source=copy_link)\n" +
                    "- 현재 로그인한 사용자의 회원 탈퇴를 처리합니다.\n" +
                    "- 소프트 삭제 방식으로, 한 달 후 완전히 삭제됩니다.\n" +
                    "- 탈퇴 즉시 모든 토큰이 무효화됩니다.\n" +
                    "\n**Headers:**\n" +
                    "  - `Authorization` (String, required): Bearer 토큰 형식의 액세스 토큰\n" +
                    "\n**Response:**\n" +
                    "  - 성공 시 200(OK)과 성공 메시지 반환\n" +
                    "  - 탈퇴 후 재로그인 가능"
    )
    @PatchMapping("/withdraw")
    public BaseResponse<Void> withdrawMember(
            @Parameter(
                    name = "Authorization",
                    description = "Access Token. 형식: `Bearer <token>`",
                    required = true,
                    in = ParameterIn.HEADER,
                    schema = @Schema(type = "string")
            )
            @RequestHeader("Authorization")
            String authorization
    ) {
        withdrawalService.withdrawCurrentUser(authorization);
        return BaseResponse.onSuccess(SuccessStatus._OK, null);
    }
}