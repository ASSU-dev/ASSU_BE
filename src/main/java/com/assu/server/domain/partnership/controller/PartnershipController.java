package com.assu.server.domain.partnership.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.partnership.dto.PartnershipRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipResponseDTO;
import com.assu.server.domain.partnership.service.PartnershipService;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
@Tag(name = "Partnership", description = "제휴 제안 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/partnership")
public class PartnershipController {

	private final PartnershipService partnershipService;
    private final NotificationCommandService notificationCommandService;
    private final StoreRepository storeRepository;

	@PostMapping("/usage")
	@Operation(summary= "유저의 인증 후 최종적으로 호출", description = "인증완료 화면 전에 바로 호출되어 유저의 제휴 내역에 데이터가 들어가게 됩니다. (개인 인증인 경우도 포함됩니다.)")
	public ResponseEntity<BaseResponse<Void>> finalPartnershipRequest(
		@AuthenticationPrincipal PrincipalDetails userDetails,@RequestBody PartnershipRequestDTO.finalRequest dto
	) {
		Member member = userDetails.getMember();
		partnershipService.recordPartnershipUsage(dto, member);

		return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus.USER_PAPER_REQUEST_SUCCESS, null));
	}

    @Operation(
            summary = "제휴 제안서 초안 생성 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/2fe1197c19ed8043a511cc8ea005d5b4)\n" +
                    "- 관리자로 로그인한 상태에서 제안서 초안을 생성합니다.\n" +
                    "- 제안서를 작성할 제휴업체 ID 입력.\n" +
                    "- 성공 시 200(OK)과 `CreateDraftResponse` 객체 반환.\n" +
                    "\n**Request Body:**\n" +
                    "  - `CreateDraftRequest` 객체 (JSON)\n" +
                    "  - `partnerId` (Long): 제휴 제안서를 작성할 제휴업체 ID\n" +
                    "\n**Response:**\n" +
                    "  - `paperId` (Long): 생성된 제안서 ID\n")
    @PostMapping("/proposal/draft")
    public BaseResponse<PartnershipResponseDTO.CreateDraftResponseDTO> createDraftPartnership(
            @RequestBody PartnershipRequestDTO.CreateDraftRequestDTO request,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.createDraftPartnership(request, pd.getId()));
    }

    @Operation(
            summary = "제휴 제안서 수동 등록 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/2591197c19ed804785d9f58f95223048)\n" +
                    "- 관리자로 로그인한 상황에서 내용이 있는 제휴 제안서를 생성합니다.\n" +
                    "- 계약서 이미지 MultipartFile을 입력.\n" +
                    "- 주소 입력 시 장소 검색용 API에서 반환된 Map 객체의 내용을 selectedPlace에 입력.\n" +
                    "- options의 optionType을 SERVICE/DISCOUNT 중 하나로 설정.\n" +
                    "- options의 criterionType을 PRICE/HEADCOUNT 중 하나로 설정.\n" +
                    "- 이외의 제휴 유형일 경우 anotherType을 true로 설정.\n" +
                    "- 성공 시 200(OK)과 `ManualPartnershipResponse` 객체 반환.\n" +
                    "- DB에 해당하는 store가 없다면 생성.\n" +
                    "- 해당하는 store가 INACTIVE 상태였다면 ACTIVE 상태로 변환.\n" +
                    "\n**Request Body:**\n" +
                    "  - `ManualPartnershipRequest` 객체 (JSON): 제안서 내용\n" +
                    "  - `storeName` (String): 가게 이름\n" +
                    "  - `selectedPlace` (JSON): 선택된 장소\n" +
                    "    - `placeId` (String): kakao place ID\n" +
                    "    - `name` (String): kakao place 이름\n" +
                    "    - `address` (String): 지번 주소\n" +
                    "    - `roadAddress` (String): 도로명 주소\n" +
                    "    - `latitude` (Double): 장소 위도\n" +
                    "    - `longitude` (Double): 장소 경도\n" +
                    "  - `storeDetailAddress` (String): 가게 상세주소\n" +
                    "  - `partnershipPeriodStart` (LocalDate): 제휴 시작일\n" +
                    "  - `partnershipPeriodEnd` (LocalDate): 제휴 마감일\n" +
                    "  - `options` (JSON): 제휴 옵션\n" +
                    "    - `optionType` (OptionType):  제공 서비스 종류 (서비스 제공, 할인)\n" +
                    "    - `criterionType` (CriterionType):  서비스 제공 기준 (금액, 인원)\n" +
                    "    - `anotherType` (Boolean):  기타 제공 서비스\n" +
                    "    - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "    - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "    - `category` (String): 서비스 카테고리, 서비스 제공 항목이 여러 개 일 때 작성\n" +
                    "    - `discountRate` (Long): 서비스 제공 인원 수\n" +
                    "    - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "    - `goods` (JSON): 서비스 제공 항목\n" +
                    "      - `goodsName` (String): 서비스 제공 항목명\n" +
                    "  - `contractImage` (MultipartFile, required): 계약서 이미지 파일\n" +
                    "\n**Response:**\n" +
                    "  - `storeId` (Long): 가게 ID\n" +
                    "  - `storeCreated` (boolean): 가게가 DB에 생성되었는지 여부\n" +
                    "  - `storeActivated` (boolean): 가게가 재활성화되었는지 여부\n" +
                    "  - `status` (String): 제휴 제안서의 상태\n" +
                    "  - `contractImageUrl` (String): 계약서 파일 URL\n" +
                    "  - `partnership` (JSON): 제휴 제안서\n" +
                    "    - `partnershipId` (Long): 제안서 ID\n" +
                    "    - `partnershipPeriodStart` (LocalDate): 제휴 시작일\n" +
                    "    - `partnershipPeriodEnd` (LocalDate): 제휴 마감일\n" +
                    "    - `adminId` (Long): 관리자 ID\n" +
                    "    - `partnerId` (Long): 제휴업체 ID\n" +
                    "    - `storeId` (Long): 가게 ID\n" +
                    "    - `storeName` (String): 가게 이름\n" +
                    "    - `adminName` (String): 관리자 이름\n" +
                    "    - `isActivated` (ActivationStatus): 제안서 활성화 여부\n" +
                    "    - `options` (JSON): 제휴 옵션\n" +
                    "      - `optionType` (OptionType):  제공 서비스 종류 (서비스 제공, 할인)\n" +
                    "      - `criterionType` (CriterionType):  서비스 제공 기준 (금액, 인원)\n" +
                    "      - `anotherType` (Boolean):  기타 제공 서비스\n" +
                    "      - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "      - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "      - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "      - `category` (String): 서비스 카테고리, 서비스 제공 항목이 여러 개 일 때 작성\n" +
                    "      - `discountRate` (Long): 서비스 제공 인원 수\n" +
                    "      - `goods` (JSON): 서비스 제공 항목\n" +
                    "        - `goodsId` (Long): 서비스 제공 항목 ID\n" +
                    "        - `goodsName` (String): 서비스 제공 항목명\n")
    @PostMapping(value = "/passivity", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<PartnershipResponseDTO.ManualPartnershipResponseDTO> createManualPartnership(
            @RequestPart("request") @Parameter PartnershipRequestDTO.ManualPartnershipRequestDTO request,
            @Parameter(
                    description = "계약서 이미지 파일",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary"))
            )
            MultipartFile contractImage,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.createManualPartnership(request, pd.getId(), contractImage));
    }

    @Operation(
            summary = "제휴 제안서 내용 수정 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/2371197c19ed80aa8468d2377ef8eac2)\n" +
                    "- 제안서 초안 또는 이미 작성된 제안서의 내용을 수정합니다.\n" +
                    "- options의 optionType을 SERVICE/DISCOUNT 중 하나로 설정\n" +
                    "- options의 criterionType을 PRICE/HEADCOUNT 중 하나로 설정\n" +
                    "- 이외의 제휴 유형일 경우 anotherType을 true로 설정\n" +
                    "- 성공 시 200(OK)과 `WritePartnershipResponse` 객체 반환\n" +
                    "\n**Request Body:**\n" +
                    "  - `WritePartnershipRequest` 객체 (JSON): 수정 내용\n" +
                    "  - `paperId` (String): 이메일 주소\n" +
                    "  - `partnershipPeriodStart` (LocalDate): 제휴 시작일\n" +
                    "  - `partnershipPeriodEnd` (LocalDate): 제휴 마감일\n" +
                    "  - `options` (JSON): 제휴 옵션\n" +
                    "    - `optionType` (OptionType):  제공 서비스 종류 (서비스 제공, 할인)\n" +
                    "    - `criterionType` (CriterionType):  서비스 제공 기준 (금액, 인원)\n" +
                    "    - `anotherType` (Boolean):  기타 제공 서비스\n" +
                    "    - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "    - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "    - `category` (String): 서비스 카테고리, 서비스 제공 항목이 여러 개 일 때 작성\n" +
                    "    - `discountRate` (Long): 서비스 제공 인원 수\n" +
                    "    - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "    - `goods` (JSON): 서비스 제공 항목\n" +
                    "      - `goodsName` (String): 서비스 제공 항목명\n" +
                    "\n**Response:**\n" +
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
                    "    - `optionType` (OptionType):  제공 서비스 종류 (서비스 제공, 할인)\n" +
                    "    - `criterionType` (CriterionType):  서비스 제공 기준 (금액, 인원)\n" +
                    "    - `anotherType` (Boolean):  기타 제공 서비스\n" +
                    "    - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "    - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "    - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "    - `category` (String): 서비스 카테고리, 서비스 제공 항목이 여러 개 일 때 작성\n" +
                    "    - `discountRate` (Long): 서비스 제공 인원 수\n" +
                    "    - `goods` (JSON): 서비스 제공 항목\n" +
                    "      - `goodsId` (Long): 서비스 제공 항목 ID\n" +
                    "      - `goodsName` (String): 서비스 제공 항목명\n")
    @PatchMapping("/proposal")
    public BaseResponse<PartnershipResponseDTO.WritePartnershipResponseDTO> updatePartnership(
            @RequestBody PartnershipRequestDTO.WritePartnershipRequestDTO request,
            @AuthenticationPrincipal PrincipalDetails pd
    ){
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.updatePartnership(request, pd.getId()));
    }

    @Operation(
            summary = "제휴 상태 업데이트 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/SUSPEND-ACTIVE-INACTIVE-2371197c19ed805ab509f552817e823a)\n" +
                    "- 제휴 상태를 변경합니다.\n" +
                    "- 적용할 상태 입력 (ACTIVE/SUSPEND/INACTIVE).\n" +
                    "- 성공 시 200(OK)과 `UpdateResponse` 객체 반환.\n" +
                    "\n**Parameters:**\n" +
                    "  - `partnershipId` (Long): 상태를 적용할 제안서 ID\n" +
                    "\n**Request Body:**\n" +
                    "  - `UpdateRequest` 객체 (JSON)\n" +
                    "  - `status` (String): 제안서에 적용할 상태\n" +
                    "\n**Response:**\n" +
                    "  - `partnershipId` (Long): 생성된 제안서 ID\n"+
                    "  - `prevStatus` (String): 제안서의 이전 상태\n"+
                    "  - `newStatus` (String): 제안서의 이전 상태\n"+
                    "  - `changedAt` (LocalDateTime): 상태 변경 시간\n")
    @PatchMapping("/{partnershipId}/status")
    public BaseResponse<PartnershipResponseDTO.UpdateResponseDTO> updatePartnershipStatus(
            @PathVariable("partnershipId") Long partnershipId,
            @RequestBody PartnershipRequestDTO.UpdateRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.updatePartnershipStatus(partnershipId, request));
    }

    @Operation(
            summary = "제휴 상세조회 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/2371197c19ed80cdac8beb2ffddb2f61)\n" +
                    "- 제휴 제안서의 내용을 조회합니다.\n" +
                    "- 적용할 상태 입력 (ACTIVE/SUSPEND/INACTIVE).\n" +
                    "- 성공 시 200(OK)과 `GetPartnershipDetailResponse` 객체 반환.\n" +
                    "\n**Parameters:**\n" +
                    "  - `partnershipId` (Long): 내용을 조회할 제안서 ID\n" +
                    "\n**Response:**\n" +
                    "  - `partnershipId` (Long): 제안서 ID\n"+
                    "  - `updatedAt` (LocalDateTime): 업데이트된 시간\n"+
                    "  - `partnershipPeriodStart` (LocalDate): 제휴 시작일\n"+
                    "  - `partnershipPeriodEnd` (LocalDate): 제휴 마감일\n"+
                    "  - `adminId` (Long): 관리자 ID\n" +
                    "  - `partnerId` (Long): 제휴업체 ID\n" +
                    "  - `storeId` (Long): 가게 ID\n" +
                    "  - `options` (JSON): 제휴 옵션\n" +
                    "    - `optionType` (OptionType):  제공 서비스 종류 (서비스 제공, 할인)\n" +
                    "    - `criterionType` (CriterionType):  서비스 제공 기준 (금액, 인원)\n" +
                    "    - `anotherType` (Boolean):  기타 제공 서비스\n" +
                    "    - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "    - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "    - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "    - `category` (String): 서비스 카테고리, 서비스 제공 항목이 여러 개 일 때 작성\n" +
                    "    - `discountRate` (Long): 서비스 제공 인원 수\n" +
                    "    - `goods` (JSON): 서비스 제공 항목\n" +
                    "      - `goodsId` (Long): 서비스 제공 항목 ID\n" +
                    "      - `goodsName` (String): 서비스 제공 항목명\n")
    @GetMapping("/{partnershipId}")
    public BaseResponse<PartnershipResponseDTO.GetPartnershipDetailResponseDTO> getPartnership(
            @PathVariable Long partnershipId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.getPartnership(partnershipId));
    }

    @Operation(
            summary = "제휴 제안서 삭제 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/2fe1197c19ed80e58d30c469e4ba3146)\n" +
                    "- paperId와 관련된 모든 내용을 삭제합니다.\n" +
                    "- 성공 시 200(OK) 반환.\n" +
                    "\n**Parameters:**\n" +
                    "  - `paperId` (Long, required): 삭제할 제안서 ID\n")
    @DeleteMapping("/proposal/delete/{paperId}")
    public BaseResponse<Void> deletePartnership(
            @PathVariable Long paperId
    ) {
        partnershipService.deletePartnership(paperId);
        return BaseResponse.onSuccess(SuccessStatus._OK, null);
    }

    @Operation(
            summary = "제휴 중인 가게 조회 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/_-2241197c19ed81b1b9adf724adc4600c)\n" +
                    "- 현재 로그인한 관리자와 제휴 중인 가게를 조회합니다.\n" +
                    "- 전체를 조회하려면 all을 true로, 가장 최근 두 건을 조회하려면 all을 false로 설정.\n" +
                    "- 성공 시 200(OK)과 `WritePartnershipResponse` 객체 반환.\n" +
                    "\n**Parameters:**\n" +
                    "  - `all` (boolean, required): 조회 옵션\n" +
                    "\n**Response:**\n" +
                    "  - `partnershipId` (Long): 제안서 ID\n"+
                    "  - `partnershipPeriodStart` (LocalDate): 제휴 시작일\n"+
                    "  - `partnershipPeriodEnd` (LocalDate): 제휴 마감일\n"+
                    "  - `adminId` (Long): 관리자 ID\n" +
                    "  - `partnerId` (Long): 제휴업체 ID\n" +
                    "  - `storeId` (Long): 가게 ID\n" +
                    "  - `storeName` (String): 가게 이름\n" +
                    "  - `adminName` (String): 관리자 이름\n" +
                    "  - `isActivated` (ActivationStatus): 제안서 활성화 여부\n" +
                    "  - `options` (JSON): 제휴 옵션\n" +
                    "    - `optionType` (OptionType):  제공 서비스 종류 (서비스 제공, 할인)\n" +
                    "    - `criterionType` (CriterionType):  서비스 제공 기준 (금액, 인원)\n" +
                    "    - `anotherType` (Boolean):  기타 제공 서비스\n" +
                    "    - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "    - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "    - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "    - `category` (String): 서비스 카테고리, 서비스 제공 항목이 여러 개 일 때 작성\n" +
                    "    - `discountRate` (Long): 서비스 제공 인원 수\n" +
                    "    - `goods` (JSON): 서비스 제공 항목\n" +
                    "      - `goodsId` (Long): 서비스 제공 항목 ID\n" +
                    "      - `goodsName` (String): 서비스 제공 항목명\n")
    @GetMapping("/admin")
    public BaseResponse<List<PartnershipResponseDTO.WritePartnershipResponseDTO>> listForAdmin(
            @RequestParam(name = "all", defaultValue = "false") boolean all,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.listPartnershipsForAdmin(all, pd.getId()));
    }

    @Operation(
            summary = "제휴 중인 관리자 조회 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/_-24f1197c19ed802784fddadbbd3ea2c6)\n" +
                    "- 현재 로그인한 제휴업체와 제휴 중인 관리자를 조회합니다.\n" +
                    "- 전체를 조회하려면 all을 true로, 가장 최근 두 건을 조회하려면 all을 false로 설정.\n" +
                    "- 성공 시 200(OK)과 `WritePartnershipResponse` 객체 반환.\n" +
                    "\n**Parameters:**\n" +
                    "  - `all` (boolean, required): 조회 옵션\n" +
                    "\n**Response:**\n" +
                    "  - `partnershipId` (Long): 제안서 ID\n"+
                    "  - `partnershipPeriodStart` (LocalDate): 제휴 시작일\n"+
                    "  - `partnershipPeriodEnd` (LocalDate): 제휴 마감일\n"+
                    "  - `adminId` (Long): 관리자 ID\n" +
                    "  - `partnerId` (Long): 제휴업체 ID\n" +
                    "  - `storeId` (Long): 가게 ID\n" +
                    "  - `storeName` (String): 가게 이름\n" +
                    "  - `adminName` (String): 관리자 이름\n" +
                    "  - `isActivated` (ActivationStatus): 제안서 활성화 여부\n" +
                    "  - `options` (JSON): 제휴 옵션\n" +
                    "    - `optionType` (OptionType):  제공 서비스 종류 (서비스 제공, 할인)\n" +
                    "    - `criterionType` (CriterionType):  서비스 제공 기준 (금액, 인원)\n" +
                    "    - `anotherType` (Boolean):  기타 제공 서비스\n" +
                    "    - `people` (Integer): 서비스 제공 기준 인원 수\n" +
                    "    - `cost` (Integer): 서비스 제공 기준 금액\n" +
                    "    - `note` (String): 기타 유형 제휴 옵션 문구\n" +
                    "    - `category` (String): 서비스 카테고리, 서비스 제공 항목이 여러 개 일 때 작성\n" +
                    "    - `discountRate` (Long): 서비스 제공 인원 수\n" +
                    "    - `goods` (JSON): 서비스 제공 항목\n" +
                    "      - `goodsId` (Long): 서비스 제공 항목 ID\n" +
                    "      - `goodsName` (String): 서비스 제공 항목명\n")
    @GetMapping("/partner")
    public BaseResponse<List<PartnershipResponseDTO.WritePartnershipResponseDTO>> listForPartner(
            @RequestParam(name = "all", defaultValue = "false") boolean all,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.listPartnershipsForPartner(all, pd.getId()));
    }

    @Operation(
            summary = "대기 중인 제휴 계약서 조회 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/_-24f1197c19ed802784fddadbbd3ea2c6)\n" +
                    "- 현재 로그인한 관리자와 제휴 중인 제안서 중 SUSPEND 상태인 제안서를 모두 조회합니다.\n" +
                    "- 성공 시 200(OK)과 `SuspendedPaper` 객체 반환.\n" +
                    "\n**Response:**\n" +
                    "  - `paperId` (Long): 제안서 ID\n"+
                    "  - `partnerName` (String): 제휴업체 이름\n"+
                    "  - `createdAt` (LocalDateTime): 제휴 생성 일자\n")
    @GetMapping("/suspended")
    public BaseResponse<List<PartnershipResponseDTO.SuspendedPaperDTO>> suspendPartnership(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.getSuspendedPapers(pd.getId()));
    }

    @Operation(
            summary = "채팅방 내 제휴 확인 API(관리자용)",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/2fe1197c19ed8078af77d65bfcc09087)\n" +
                    "- 현재 로그인한 관리자와 파라미터로 받은 partnerId를 가진 제휴업체 간에 제휴를 조회합니다.\n" +
                    "- 비활성화 되지 않은 가장 최근 제휴 1건 조회.\n" +
                    "- 성공 시 200(OK)과 `AdminPartnershipWithPartnerResponse` 객체 반환.\n" +
                    "\n**Parameters:**\n" +
                    "  - `partnerId` (Long, required): 제휴업체 ID\n" +
                    "\n**Response:**\n" +
                    "  - `paperId` (Long): 제안서 ID\n"+
                    "  - `isPartnered` (boolean): 제휴 여부\n"+
                    "  - `status` (String): 제휴 상태\n"+
                    "  - `partnerId` (Long): 제휴업체 ID\n"+
                    "  - `partnerName` (String): 제휴업체 이름\n"+
                    "  - `partnerAddress` (String): 제휴업체 주소\n")
    @GetMapping("/check/admin")
    public BaseResponse<PartnershipResponseDTO.AdminPartnershipWithPartnerResponseDTO> checkAdminPartnership(
            @RequestParam("partnerId") Long partnerId,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.checkPartnershipWithPartner(pd.getId(), partnerId));
    }

    @Operation(
            summary = "채팅방 내 제휴 확인 API(제휴업체용)",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/2fe1197c19ed8078af77d65bfcc09087)\n" +
                    "- 현재 로그인한 제휴업체와 파라미터로 받은 adminId를 가진 관리자 간에 제휴를 조회합니다.\n" +
                    "- 비활성화 되지 않은 가장 최근 제휴 1건 조회.\n" +
                    "- 성공 시 200(OK)과 `PartnerPartnershipWithAdminResponse` 객체 반환.\n" +
                    "\n**Parameters:**\n" +
                    "  - `adminId` (Long, required): 관리자 ID\n" +
                    "\n**Response:**\n" +
                    "  - `paperId` (Long): 제안서 ID\n"+
                    "  - `isPartnered` (boolean): 제휴 여부\n"+
                    "  - `status` (String): 제휴 상태\n"+
                    "  - `adminId` (Long): 관리자 ID\n"+
                    "  - `adminName` (String): 관리자 이름\n"+
                    "  - `adminAddress` (String): 관리자 주소\n"
    )
    @GetMapping("/check/partner")
    public BaseResponse<PartnershipResponseDTO.PartnerPartnershipWithAdminResponseDTO> checkPartnerPartnership(
            @RequestParam("adminId") Long adminId,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.checkPartnershipWithAdmin(pd.getId(), adminId));
    }
}
