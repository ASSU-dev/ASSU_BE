package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.inquiry.dto.InquiryAnswerRequestDTO;
import com.assu.server.domain.inquiry.dto.InquiryResponseDTO;
import com.assu.server.domain.inquiry.service.InquiryService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.assu.server.domain.common.dto.PageResponseDTO;
import com.assu.server.domain.inquiry.entity.Inquiry;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/inquiries")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeInquiryController {

    private final InquiryService inquiryService;

    @Operation(
            summary = "운영자 전체 문의 목록 조회 API",
            description = "# [v1.1 (2026-07-04)]\n" +
                    "- 모든 사용자의 문의를 페이징으로 조회합니다.\n" +
                    "- `status` 파라미터로 상태 필터링 가능합니다 (ALL / WAITING / ANSWERED).\n" +
                    "- `keyword`가 있으면 제목 또는 본문에서 검색합니다.\n\n" +
                    "**Query Parameters:**\n" +
                    "- `status` (default: ALL): 문의 상태 필터\n" +
                    "- `keyword` (optional): 제목 또는 본문 검색어\n" +
                    "- `page` (default: 1): 페이지 번호\n" +
                    "- `size` (default: 20): 페이지 크기\n\n" +
                    "**Response:**\n" +
                    "- 200(OK): 문의 목록 페이지 반환\n" +
                    "- 401(UNAUTHORIZED): 인증 실패 또는 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음"
    )
    @GetMapping
    public BaseResponse<PageResponseDTO<InquiryResponseDTO>> listAllInquiries(
            @Parameter(description = "상태 필터 (ALL / WAITING / ANSWERED)", example = "ALL")
            @RequestParam(defaultValue = "ALL") Inquiry.StatusFilter status,
            @Parameter(description = "제목 또는 본문 검색어")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (1 이상)", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~200)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, inquiryService.getAllInquiries(status, keyword, page, size));
    }

    @Operation(
            summary = "운영자 문의 상세 조회 API",
            description = "# [v1.0 (2026-06-25)]()\n" +
                    "- 특정 문의의 상세 내용을 조회합니다.\n" +
                    "- 소유권 검증 없이 모든 문의에 접근 가능합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `inquiryId` (Long, required): 문의 ID\n\n" +
                    "**Response:**\n" +
                    "- 200(OK): 문의 상세 반환\n" +
                    "- 401(UNAUTHORIZED): 인증 실패 또는 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
                    "- 404(NOT_FOUND): 존재하지 않는 문의 ID"
    )
    @GetMapping("/{inquiryId}")
    public BaseResponse<InquiryResponseDTO> getInquiry(
            @PathVariable("inquiryId") Long inquiryId
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, inquiryService.getById(inquiryId));
    }

    @BackofficeAudited(action = "INQUIRY_ANSWER", targetId = "#inquiryId")
    @Operation(
            summary = "운영자 문의 답변 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/24e1197c19ed8064808fcca568b8912a?source=copy_link)\n" +
                    "- 문의에 답변을 등록하고 상태를 ANSWERED로 변경합니다.\n" +
                    "- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
                    "**Path Variable:**\n" +
                    "- `inquiryId` (Long, required): 문의 ID\n\n" +
                    "**Request Body:**\n" +
                    "- `answer` (String, required): 답변 내용\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)과 성공 메시지 반환\n" +
                    "- 400(BAD_REQUEST): 빈 답변 내용\n" +
                    "- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
                    "- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
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
