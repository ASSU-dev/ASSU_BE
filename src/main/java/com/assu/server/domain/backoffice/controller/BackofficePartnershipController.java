package com.assu.server.domain.backoffice.controller;

import org.springdoc.core.annotations.ParameterObject;
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
    @Operation(
            summary = "모든 제휴 목록 조회 API (백오피스용)",
            description = "시스템에 등록된 모든 제휴 목록을 페이징 조회합니다.\n\n" +
                    "**Response:**\n" +
                    "  - 성공 시 200(OK)과 `WritePartnershipResponse` 객체 목록(페이징) 반환.\n" +
                    "  - `partnershipId` (Long): 제안서 ID\n" +
                    "  - `partnershipPeriodStart` (LocalDate): 제휴 시작일\n" +
                    "  - `partnershipPeriodEnd` (LocalDate): 제휴 마감일\n" +
                    "  - `adminId` (Long): 관리자 ID\n" +
                    "  - `partnerId` (Long): 제휴업체 ID\n" +
                    "  - `storeId` (Long): 가게 ID\n" +
                    "  - `storeName` (String): 가게 이름\n" +
                    "  - `adminName` (String): 관리자 이름\n" +
                    "  - `isActivated` (ActivationStatus): 제안서 활성화 여부\n" +
                    "  - `options` (JSON): 제휴 옵션\n" +
                    "    - `optionType` (OptionType): 제공 서비스 종류 (SERVICE/DISCOUNT)\n" +
                    "    - `criterionType` (CriterionType): 서비스 제공 기준 (PRICE/HEADCOUNT)\n" +
                    "    - `anotherType` (Boolean): 기타 제공 서비스 여부\n" +
                    "    - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "    - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "    - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "    - `category` (String): 서비스 카테고리\n" +
                    "    - `discountRate` (Long): 할인율\n" +
                    "    - `goods` (JSON): 서비스 제공 항목 목록\n" +
                    "      - `goodsId` (Long): 서비스 제공 항목 ID\n" +
                    "      - `goodsName` (String): 서비스 제공 항목명\n"
    )
    @GetMapping
    public BaseResponse<Page<WritePartnershipResponseDTO>> getPartnerships(
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficePartnershipService.getPartnerships(pageable));
    }

    @BackofficeAudited(action = "PARTNERSHIP_BY_ADMIN_READ", targetId = "#adminId")
    @Operation(
            summary = "학생회별 제휴 목록 조회 API (백오피스용)",
            description = "특정 학생회 ID(adminId) 기준 맺어진 활성화된 제휴 목록을 조회합니다.\n\n" +
                    "**Parameters:**\n" +
                    "  - `adminId` (Long, required): 학생회 ID\n\n" +
                    "**Response:**\n" +
                    "  - 성공 시 200(OK)과 `WritePartnershipResponse` 객체 목록(페이징) 반환.\n" +
                    "  - `partnershipId` (Long): 제안서 ID\n" +
                    "  - `partnershipPeriodStart` (LocalDate): 제휴 시작일\n" +
                    "  - `partnershipPeriodEnd` (LocalDate): 제휴 마감일\n" +
                    "  - `adminId` (Long): 관리자 ID\n" +
                    "  - `partnerId` (Long): 제휴업체 ID\n" +
                    "  - `storeId` (Long): 가게 ID\n" +
                    "  - `storeName` (String): 가게 이름\n" +
                    "  - `adminName` (String): 관리자 이름\n" +
                    "  - `isActivated` (ActivationStatus): 제안서 활성화 여부\n" +
                    "  - `options` (JSON): 제휴 옵션\n" +
                    "    - `optionType` (OptionType): 제공 서비스 종류 (SERVICE/DISCOUNT)\n" +
                    "    - `criterionType` (CriterionType): 서비스 제공 기준 (PRICE/HEADCOUNT)\n" +
                    "    - `anotherType` (Boolean): 기타 제공 서비스 여부\n" +
                    "    - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "    - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "    - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "    - `category` (String): 서비스 카테고리\n" +
                    "    - `discountRate` (Long): 할인율\n" +
                    "    - `goods` (JSON): 서비스 제공 항목 목록\n" +
                    "      - `goodsId` (Long): 서비스 제공 항목 ID\n" +
                    "      - `goodsName` (String): 서비스 제공 항목명\n"
    )
    @GetMapping("/admin/{adminId}")
    public BaseResponse<Page<WritePartnershipResponseDTO>> getPartnershipsByAdmin(
            @PathVariable @Parameter(description = "학생회 ID", required = true) Long adminId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficePartnershipService.getPartnershipsByAdmin(adminId, pageable));
    }

    @BackofficeAudited(action = "PARTNERSHIP_BY_STORE_READ", targetId = "#storeId")
    @Operation(
            summary = "가게별 제휴 목록 조회 API (백오피스용)",
            description = "특정 가게 ID(storeId) 기준 맺어진 활성화된 제휴 목록을 조회합니다.\n\n" +
                    "**Parameters:**\n" +
                    "  - `storeId` (Long, required): 가게 ID\n\n" +
                    "**Response:**\n" +
                    "  - 성공 시 200(OK)과 `WritePartnershipResponse` 객체 목록(페이징) 반환.\n" +
                    "  - `partnershipId` (Long): 제안서 ID\n" +
                    "  - `partnershipPeriodStart` (LocalDate): 제휴 시작일\n" +
                    "  - `partnershipPeriodEnd` (LocalDate): 제휴 마감일\n" +
                    "  - `adminId` (Long): 관리자 ID\n" +
                    "  - `partnerId` (Long): 제휴업체 ID\n" +
                    "  - `storeId` (Long): 가게 ID\n" +
                    "  - `storeName` (String): 가게 이름\n" +
                    "  - `adminName` (String): 관리자 이름\n" +
                    "  - `isActivated` (ActivationStatus): 제안서 활성화 여부\n" +
                    "  - `options` (JSON): 제휴 옵션\n" +
                    "    - `optionType` (OptionType): 제공 서비스 종류 (SERVICE/DISCOUNT)\n" +
                    "    - `criterionType` (CriterionType): 서비스 제공 기준 (PRICE/HEADCOUNT)\n" +
                    "    - `anotherType` (Boolean): 기타 제공 서비스 여부\n" +
                    "    - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "    - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "    - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "    - `category` (String): 서비스 카테고리\n" +
                    "    - `discountRate` (Long): 할인율\n" +
                    "    - `goods` (JSON): 서비스 제공 항목 목록\n" +
                    "      - `goodsId` (Long): 서비스 제공 항목 ID\n" +
                    "      - `goodsName` (String): 서비스 제공 항목명\n"
    )
    @GetMapping("/store/{storeId}")
    public BaseResponse<Page<WritePartnershipResponseDTO>> getPartnershipsByStore(
            @PathVariable @Parameter(description = "가게 ID", required = true) Long storeId,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficePartnershipService.getPartnershipsByStore(storeId, pageable));
    }
}
