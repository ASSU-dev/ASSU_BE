package com.assu.server.domain.inquiry.controller;

import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.inquiry.dto.InquiryAnswerRequestDTO;
import com.assu.server.domain.inquiry.dto.InquiryCreateRequestDTO;
import com.assu.server.domain.inquiry.dto.InquiryResponseDTO;
import com.assu.server.domain.inquiry.entity.Inquiry;
import com.assu.server.domain.inquiry.service.InquiryService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;

import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Inquiry", description = "문의 API")
@RestController
@Validated
@RequestMapping("/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryService inquiryService;

    @Operation(
            summary = "문의하기 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2441197c19ed800688f0cfb304dead63?source=copy_link)\n" +
                    "- 문의를 생성하고 해당 문의의 ID를 반환합니다.\n\n" +
                    "**Request Body:**\n" +
                    "- `title` (String, required): 문의 제목\n" +
                    "- `content` (String, required): 문의 내용\n" +
                    "- `email` (String, required): 답변 받을 이메일\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 생성된 문의 ID 반환\n" +
                    "- 400(BAD_REQUEST): 필수 필드 누락 또는 잘못된 이메일 형식\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자"
    )
    @PostMapping
    public BaseResponse<Long> create(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestBody @Valid InquiryCreateRequestDTO inquiryCreateRequestDTO
    ) {
        Long id = inquiryService.create(inquiryCreateRequestDTO, pd.getId());
        return BaseResponse.onSuccess(SuccessStatus._OK, id);
    }

    @Operation(
            summary = "문의 내역 목록 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2441197c19ed803eba4af9598484e5c5?source=copy_link)\n" +
                    "- 본인의 문의 내역 목록을 상태별로 조회합니다.\n\n" +
                    "**Request Parameters:**\n" +
                    "- `status` (String, optional): 문의 상태 (WAITING, ANSWERED, ALL) - 기본값: ALL\n" +
                    "- `page` (Integer, optional): 페이지 번호 (1 이상) - 기본값: 1\n" +
                    "- `size` (Integer, optional): 페이지 크기 - 기본값: 20\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 문의 목록 반환\n" +
                    "- 400(BAD_REQUEST): 잘못된 페이지 번호 또는 상태 값\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자"
    )
    @GetMapping
    public BaseResponse<PageResponseDTO<InquiryResponseDTO>> list(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestParam(defaultValue = "ALL") Inquiry.StatusFilter status,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        PageResponseDTO<InquiryResponseDTO> inquiryResponseDTO = inquiryService.getInquiries(status, page, size, pd.getId());
        return BaseResponse.onSuccess(SuccessStatus._OK, inquiryResponseDTO);
    }

    /** 단건 상세 조회 */
    @Operation(
            summary = "문의 내역 단건 상세 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/24e1197c19ed800f8a1fffc5a101f3c0?source=copy_link)\n" +
                    "- 본인의 문의 내역 중 1건의 문의를 상세 조회합니다.\n\n" +
                    "**Path Variables:**\n" +
                    "- `inquiryId` (Long, required): 문의 ID\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 문의 상세 정보 반환\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않은 사용자\n" +
                    "- 403(FORBIDDEN): 다른 사용자의 문의 접근 시도\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 문의 ID"
    )
    @GetMapping("/{inquiryId}")
    public BaseResponse<InquiryResponseDTO> get(
            @AuthenticationPrincipal PrincipalDetails pd,
            @PathVariable("inquiryId") Long inquiryId)
    {
        InquiryResponseDTO inquiryResponseDTO = inquiryService.get(inquiryId, pd.getId());
        return BaseResponse.onSuccess(SuccessStatus._OK, inquiryResponseDTO);
    }

    /** 문의 답변 (운영자) */
    @Operation(
            summary = "운영자 답변 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/24e1197c19ed8064808fcca568b8912a?source=copy_link)\n" +
                    "- 문의에 답변을 저장하고 상태를 ANSWERED로 변경합니다.\n\n" +
                    "**Path Variables:**\n" +
                    "- `inquiryId` (Long, required): 문의 ID\n\n" +
                    "**Request Body:**\n" +
                    "- `answer` (String, required): 답변 내용\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 성공 메시지 반환\n" +
                    "- 400(BAD_REQUEST): 빈 답변 내용\n" +
                    "- 403(FORBIDDEN): 운영자 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 문의 ID\n" +
                    "- 409(CONFLICT): 이미 답변된 문의"
    )
    @PatchMapping("/{inquiryId}/answer")
    public BaseResponse<String> answer(
            @PathVariable("inquiryId") Long inquiryId,
            @RequestBody @Valid InquiryAnswerRequestDTO inquiryAnswerRequestDTO
    ) {
        inquiryService.answer(inquiryId, inquiryAnswerRequestDTO.answer());
        return BaseResponse.onSuccess(SuccessStatus._OK, "The inquiry answered successfully. id=" + inquiryId);
    }
}