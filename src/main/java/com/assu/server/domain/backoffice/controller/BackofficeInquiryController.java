package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.inquiry.dto.InquiryAnswerRequestDTO;
import com.assu.server.domain.inquiry.service.InquiryService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/inquiries")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeInquiryController {

    private final InquiryService inquiryService;

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
