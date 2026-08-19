package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.auth.dto.signup.PartnerBatchSignUpItemDTO;
import com.assu.server.domain.auth.dto.signup.SignUpResponseDTO;
import com.assu.server.domain.auth.service.SignUpService;
import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeDocumentUrlResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeMemberSummaryDTO;
import com.assu.server.domain.backoffice.service.BackofficeMemberService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/partners")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficePartnerController {

    private final BackofficeMemberService backofficeMemberService;
    private final SignUpService signUpService;

    @Operation(
            summary = "사업자등록증 조회 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- Partner 회원의 사업자등록증 S3 presigned URL을 반환합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): Partner 회원 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 presigned URL 반환 (약 10분 유효)\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): Partner가 아니거나 사업자등록증 없음"
    )
    @GetMapping("/{memberId}/license")
    public BaseResponse<BackofficeDocumentUrlResponseDTO> getLicenseUrl(
            @Parameter(description = "Partner 회원 ID") @PathVariable Long memberId
    ) {
        return BaseResponse.onSuccess(
                SuccessStatus._OK,
                backofficeMemberService.getPartnerLicenseUrl(memberId)
        );
    }

    @BackofficeAudited(action = "PARTNER_LICENSE_VERIFY", targetId = "#memberId")
    @Operation(
            summary = "사업자등록증 검증 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- Partner 회원의 사업자등록증을 검증 완료 처리합니다.\n" +
                    "- 가입 승인과 독립적으로 동작합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): Partner 회원 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `BackofficeMemberSummaryDTO` 반환\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): Partner가 아니거나 사업자등록증 없음"
    )
    @PatchMapping("/{memberId}/license/verify")
    public BaseResponse<BackofficeMemberSummaryDTO> verifyLicense(@PathVariable Long memberId) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeMemberService.verifyPartnerLicense(memberId));
    }

    @BackofficeAudited(action = "PARTNER_BATCH_SIGNUP")
    @Operation(
            summary = "제휴업체 단체 회원가입 API",
            description = "이메일, 비밀번호, 업체명, 도로명 주소, 위도, 경도 목록을 받아 제휴업체 계정들을 일괄 생성합니다."
    )
    @PostMapping(value = "/batch-signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<List<SignUpResponseDTO>> signupBatchPartner(
            @RequestBody
            @Valid
            List<PartnerBatchSignUpItemDTO> requests
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, signUpService.signupBatchPartner(requests));
    }
}
