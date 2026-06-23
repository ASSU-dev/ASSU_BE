package com.assu.server.domain.backoffice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.service.BackofficePartnershipService;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/partnership")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficePartnershipController {

    private final BackofficePartnershipService backofficePartnershipService;

    @BackofficeAudited(action = "PARTNERSHIP_ALL_READ")
    @Operation(summary = "모든 제휴 목록 조회 API (백오피스용)", description = "시스템에 등록된 모든 제휴 목록을 페이징 조회합니다.")
    @GetMapping
    public BaseResponse<Page<WritePartnershipResponseDTO>> getPartnerships(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficePartnershipService.getPartnerships(pageable));
    }

    @BackofficeAudited(action = "PARTNERSHIP_BY_ADMIN_READ", targetId = "#adminId")

    @Operation(summary = "학생회별 제휴 목록 조회 API (백오피스용)", description = "특정 학생회 ID(adminId) 기준 맺어진 활성화된 제휴 목록을 조회합니다.")
    @GetMapping("/admin/{adminId}")
    public BaseResponse<Page<WritePartnershipResponseDTO>> getPartnershipsByAdmin(
            @PathVariable @Parameter(description = "학생회 ID", required = true) Long adminId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficePartnershipService.getPartnershipsByAdmin(adminId, pageable));
    }

    @BackofficeAudited(action = "PARTNERSHIP_BY_STORE_READ", targetId = "#storeId")
    @Operation(summary = "가게별 제휴 목록 조회 API (백오피스용)", description = "특정 가게 ID(storeId) 기준 맺어진 활성화된 제휴 목록을 조회합니다.")
    @GetMapping("/store/{storeId}")
    public BaseResponse<Page<WritePartnershipResponseDTO>> getPartnershipsByStore(
            @PathVariable @Parameter(description = "가게 ID", required = true) Long storeId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficePartnershipService.getPartnershipsByStore(storeId, pageable));
    }
}

