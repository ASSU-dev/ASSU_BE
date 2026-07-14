package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeMemberDetailDTO;
import com.assu.server.domain.backoffice.dto.BackofficeMemberSummaryDTO;
import com.assu.server.domain.backoffice.service.BackofficeMemberService;
import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/members")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeMemberController {

    private final BackofficeMemberService backofficeMemberService;

    @Operation(
            summary = "회원 목록 조회 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- STUDENT/ADMIN/PARTNER 회원 목록을 페이지네이션으로 조회합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Query Parameters:**\n" +
                    "- `role` (UserRole, optional): 역할 필터\n" +
                    "- `status` (ActivationStatus, optional): 활성화 상태 필터\n" +
                    "- `deleted` (Boolean, optional): true=탈퇴 회원만, false=활성 회원만, omit=전체\n" +
                    "- `page`, `size`, `sort` (Pageable)\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `PageResponseDTO<BackofficeMemberSummaryDTO>` 반환\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음"
    )
    @GetMapping
    public BaseResponse<PageResponseDTO<BackofficeMemberSummaryDTO>> listMembers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) ActivationStatus status,
            @RequestParam(required = false) Boolean deleted,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(
                SuccessStatus._OK,
                backofficeMemberService.listMembers(role, status, deleted, pageable)
        );
    }

    @Operation(
            summary = "탈퇴 회원 목록 조회 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- `deletedAt`이 설정된 회원 목록을 조회합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `PageResponseDTO<BackofficeMemberSummaryDTO>` 반환\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음"
    )
    @GetMapping("/deleted")
    public BaseResponse<PageResponseDTO<BackofficeMemberSummaryDTO>> listDeletedMembers(
            @PageableDefault(size = 20, sort = "deletedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(
                SuccessStatus._OK,
                backofficeMemberService.listDeletedMembers(pageable)
        );
    }

    @Operation(
            summary = "회원 상세 조회 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- 회원 ID로 상세 정보를 조회합니다.\n" +
                    "- 역할별 프로필 필드를 `base` + `student`/`admin`/`partner` 중 하나로 반환합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): 회원 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `BackofficeMemberDetailDTO` 반환\n" +
                    "  - `base` (BackofficeMemberBaseDetailDTO): 공통 회원 정보\n" +
                    "  - `student` (BackofficeStudentProfileDetailDTO): STUDENT일 때만\n" +
                    "  - `admin` (BackofficeAdminProfileDetailDTO): ADMIN일 때만\n" +
                    "  - `partner` (BackofficePartnerProfileDetailDTO): PARTNER일 때만\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 회원 ID"
    )
    @GetMapping("/{memberId}")
    public BaseResponse<BackofficeMemberDetailDTO> getMemberDetail(
            @Parameter(description = "회원 ID") @PathVariable Long memberId
    ) {
        return BaseResponse.onSuccess(
                SuccessStatus._OK,
                backofficeMemberService.getMemberDetail(memberId)
        );
    }

    @BackofficeAudited(action = "MEMBER_APPROVE", targetId = "#memberId")
    @Operation(
            summary = "회원 가입 승인 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- `SUSPEND`(대기) 또는 `INACTIVE`(거절) 상태의 ADMIN/PARTNER 회원을 `ACTIVE`로 승인합니다.\n" +
                    "- 거절된 회원도 재승인할 수 있습니다.\n" +
                    "- Partner는 사업자등록증 검증 완료 처리 및 Store 활성화를 함께 수행합니다.\n" +
                    "- Admin은 인감 검증 완료 처리를 함께 수행합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): 회원 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `BackofficeMemberSummaryDTO` 반환\n" +
                    "- 400(BAD_REQUEST): 이미 승인(ACTIVE)된 회원\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 회원 ID"
    )
    @PatchMapping("/{memberId}/approve")
    public BaseResponse<BackofficeMemberSummaryDTO> approveMember(@PathVariable Long memberId) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeMemberService.approveMember(memberId));
    }

    @BackofficeAudited(action = "MEMBER_REJECT", targetId = "#memberId")
    @Operation(
            summary = "회원 가입 거절 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- `SUSPEND` 상태의 ADMIN/PARTNER 회원을 `INACTIVE`로 거절합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): 회원 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `BackofficeMemberSummaryDTO` 반환\n" +
                    "- 400(BAD_REQUEST): 승인 대기 상태가 아님\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 회원 ID"
    )
    @PatchMapping("/{memberId}/reject")
    public BaseResponse<BackofficeMemberSummaryDTO> rejectMember(@PathVariable Long memberId) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeMemberService.rejectMember(memberId));
    }

    @BackofficeAudited(action = "MEMBER_FORCE_WITHDRAW", targetId = "#memberId")
    @Operation(
            summary = "회원 강제 탈퇴 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- 회원을 소프트 탈퇴 처리하고 refresh token을 삭제합니다.\n" +
                    "- BACKOFFICE 운영자는 강제 탈퇴할 수 없습니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): 회원 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)\n" +
                    "- 400(BAD_REQUEST): 이미 탈퇴된 회원 또는 BACKOFFICE 대상\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 회원 ID"
    )
    @PatchMapping("/{memberId}/force-withdraw")
    public BaseResponse<String> forceWithdrawMember(@PathVariable Long memberId) {
        backofficeMemberService.forceWithdrawMember(memberId);
        return BaseResponse.onSuccess(SuccessStatus._OK, "회원이 강제 탈퇴 처리되었습니다.");
    }

    @BackofficeAudited(action = "MEMBER_RESTORE", targetId = "#memberId")
    @Operation(
            summary = "탈퇴 회원 복구 API",
            description = "# [v1.0 (2026-07-03)]\n" +
                    "- `deletedAt`을 null로 설정하여 탈퇴 회원을 복구합니다.\n" +
                    "- `isActivated` 상태는 변경하지 않습니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `memberId` (Long, required): 회원 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 `BackofficeMemberSummaryDTO` 반환\n" +
                    "- 400(BAD_REQUEST): 탈퇴 상태가 아님\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 회원 ID"
    )
    @PatchMapping("/{memberId}/restore")
    public BaseResponse<BackofficeMemberSummaryDTO> restoreMember(@PathVariable Long memberId) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeMemberService.restoreMember(memberId));
    }
}
