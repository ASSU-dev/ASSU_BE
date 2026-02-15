package com.assu.server.domain.partnership.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.assu.server.domain.partnership.dto.PartnershipFinalRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipRequestDTO;
import com.assu.server.domain.partnership.dto.PartnershipResponseDTO;
import com.assu.server.domain.partnership.service.PartnershipService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
@RestController
@Tag(name = "제휴 요청 api", description = "최종적으로 제휴를 요청할때 사용하는 api ")
@RequiredArgsConstructor
@RequestMapping("/partnership")
public class PartnershipController {

	private final PartnershipService partnershipService;

	@PostMapping("/usage")
    @Operation(
        summary = "유저의 인증 후 최종 제휴 데이터 기록 API",
        description = "# [v1.0 (2025-12-23)](https://clumsy-seeder-416.notion.site/2681197c19ed8052804eddd5a1f3ce96?source=copy_link)\n" +
            "- 인증 완료 화면 전에 호출되어 유저의 제휴 내역에 데이터를 기록합니다.\n" +
            "- 개인 인증 케이스도 포함됩니다.\n\n" +
            "**Request Body:**\n" +
            "  - `storeId` (Long, required): 제휴 매장 ID\n" +
            "  - `tableNumber` (String, required): 테이블 번호\n" +
            "  - `adminName` (String, required): 관리자 이름\n" +
            "  - `placeName` (String, required): 제휴 장소 이름\n" +
            "  - `partnershipContent` (String, required): 제휴 내용\n" +
            "  - `contentId` (Long, required): 제휴 컨텐츠 ID\n" +
            "  - `discount` (Long, optional): 할인 금액\n" +
            "  - `userIds` (List<Long>, optional): 인증 대상 유저 ID 목록\n\n" +
            "**Response:**\n" +
            "  - 성공: 200 OK, `isSuccess=true`, `result=null`\n" +
            "  - 실패: 적절한 에러 코드 및 메시지"
    )
	public ResponseEntity<BaseResponse<Void>> finalPartnershipRequest(
		@AuthenticationPrincipal PrincipalDetails pd,@RequestBody PartnershipFinalRequestDTO dto
	) {

		partnershipService.recordPartnershipUsage(dto,pd.getMember());

		return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus.USER_PAPER_REQUEST_SUCCESS, null));
	}

    @PatchMapping("/proposal")
    @Operation(
            summary = "제휴 제안서 내용 수정 API",
            description = "제공 서비스 종류(SERVICE, DISCOUNT), 서비스 제공 기준(PRICE, HEADCOUNT), 서비스 제공 항목, 카테고리, 할인율을 상황에 맞게 작성해주세요."
    )
    public BaseResponse<PartnershipResponseDTO.WritePartnershipResponseDTO> updatePartnership(
            @RequestBody PartnershipRequestDTO.WritePartnershipRequestDTO request,
            @AuthenticationPrincipal PrincipalDetails pd
    ){
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.updatePartnership(request, pd.getId()));
    }

    @Operation(
            summary = "제휴 제안서 수동 등록 API",
            description = "제공 서비스 종류(SERVICE, DISCOUNT), 서비스 제공 기준(PRICE, HEADCOUNT), 서비스 제공 항목, 카테고리, 할인율을 상황에 맞게 작성하고, 계약서 이미지를 업로드하세요."
    )
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
            summary = "제휴 중인 가게 조회 API",
            description = "전체를 조회하려면 all을 true로, 가장 최근 두 건을 조회하려면 all을 false로 설정해주세요."
    )
    @GetMapping("/admin")
    public BaseResponse<List<PartnershipResponseDTO.WritePartnershipResponseDTO>> listForAdmin(
            @RequestParam(name = "all", defaultValue = "false") boolean all,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.listPartnershipsForAdmin(all, pd.getId()));
    }

    @Operation(
            summary = "제휴 중인 관리자 조회 API",
            description = "전체를 조회하려면 all을 true로, 가장 최근 두 건을 조회하려면 all을 false로 설정해주세요."
    )
    @GetMapping("/partner")
    public BaseResponse<List<PartnershipResponseDTO.WritePartnershipResponseDTO>> listForPartner(
            @RequestParam(name = "all", defaultValue = "false") boolean all,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.listPartnershipsForPartner(all, pd.getId()));
    }

    @Operation(
            summary = "제휴 상세조회 API",
            description = "제휴 아이디를 입력하세요."
    )
    @GetMapping("/{partnershipId}")
    public BaseResponse<PartnershipResponseDTO.GetPartnershipDetailResponseDTO> getPartnership(
            @PathVariable Long partnershipId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.getPartnership(partnershipId));
    }

    @Operation(
            summary = "제휴 상태 업데이트 API",
            description = "제휴 ID와 바꾸고 싶은 상태를 입력하세요(SUSPEND/ACTIVE/INACTIVE/BLANK)"
    )
    @PatchMapping("/{partnershipId}/status")
    public BaseResponse<PartnershipResponseDTO.UpdateResponseDTO> updatePartnershipStatus(
            @PathVariable("partnershipId") Long partnershipId,
            @RequestBody PartnershipRequestDTO.UpdateRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.updatePartnershipStatus(partnershipId, request));
    }

    @PostMapping("/proposal/draft")
    @Operation(
            summary = "제휴 제안서 초안 생성 API",
            description = "현재 로그인한 관리자(Admin)가 내용이 비어있는 제휴 제안서를 초안 상태로 생성합니다."
    )
    public BaseResponse<PartnershipResponseDTO.CreateDraftResponseDTO> createDraftPartnership(
            @RequestBody PartnershipRequestDTO.CreateDraftRequestDTO request,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.createDraftPartnership(request, pd.getId()));
    }

    @DeleteMapping("/proposal/delete/{paperId}")
    @Operation(
            summary = "제휴 제안서 삭제 API",
            description = "특정 제휴 제안서(paperId)와 관련된 모든 데이터를 삭제합니다."
    )
    public BaseResponse<Void> deletePartnership(
            @PathVariable Long paperId
    ) {
        partnershipService.deletePartnership(paperId);
        return BaseResponse.onSuccess(SuccessStatus._OK, null);
    }

    @GetMapping("/suspended")
    @Operation(
            summary = "대기 중인 제휴 계약서 조회 API",
            description = "현재 로그인한 관리자(Admin)가 대기 중인 제휴 계약서를 모두 조회하여 리스트로 반환합니다."
    )
    public BaseResponse<List<PartnershipResponseDTO.SuspendedPaperDTO>> suspendPartnership(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.getSuspendedPapers(pd.getId()));
    }

    @GetMapping("/check/admin")
    @Operation(
            summary = "관리자 채팅방 내 제휴 확인 API",
            description = "현재 로그인한 관리자(Admin)가 파라미터로 받은 partnerId를 가진 상대 제휴업체(Partner)와 맺고 있는 제휴를 조회합니다. 비활성화되지 않은 가장 최근 제휴 1건을 조회합니다."
    )
    public BaseResponse<PartnershipResponseDTO.AdminPartnershipWithPartnerResponseDTO> checkAdminPartnership(
            @RequestParam("partnerId") Long partnerId,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.checkPartnershipWithPartner(pd.getId(), partnerId));
    }

    @GetMapping("/check/partner")
    @Operation(
            summary = "제휴업체 채팅방 내 제휴 확인 API",
            description = "현재 로그인한 제휴업체(Partner)가 파라미터로 받은 AdminId를 가진 상대 관리자(Admin)과 맺고 있는 제휴를 조회합니다. 비활성화되지 않은 가장 최근 제휴 1건을 조회합니다."
    )
    public BaseResponse<PartnershipResponseDTO.PartnerPartnershipWithAdminResponseDTO> checkPartnerPartnership(
            @RequestParam("adminId") Long adminId,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, partnershipService.checkPartnershipWithAdmin(pd.getId(), adminId));
    }
}
