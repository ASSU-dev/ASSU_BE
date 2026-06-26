package com.assu.server.domain.backoffice.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficePaperContentCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficePaperCreateRequestDTO;
import com.assu.server.domain.backoffice.service.BackofficePaperService;
import com.assu.server.domain.partnership.dto.WritePartnershipResponseDTO;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/paper")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficePaperController {

    private final BackofficePaperService backofficePaperService;

    @BackofficeAudited(action = "PAPER_ALL_READ")
    @Operation(summary = "모든 제휴 계약서 목록 조회 API (백오피스용)", description = "시스템에 등록된 모든 제휴 계약서 목록을 페이징 조회합니다.")
    @GetMapping
    public BaseResponse<Page<WritePartnershipResponseDTO>> getPapers(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficePaperService.getPapers(pageable));
    }

    @BackofficeAudited(action = "PAPER_CREATE", targetId = "#req.adminId")
    @Operation(summary = "임의의 제휴 계약서 생성 API (백오피스용)", description = "운영자가 임의의 제휴 계약서를 생성(빈 제휴 계약서)합니다.")
    @PostMapping
    public BaseResponse<WritePartnershipResponseDTO> createPaper(
            @RequestBody @Valid BackofficePaperCreateRequestDTO req
    ) {
        WritePartnershipResponseDTO response = backofficePaperService.createPaper(req);
        return BaseResponse.onSuccess(SuccessStatus._OK, response);
    }

    @BackofficeAudited(action = "PAPER_CONTENT_ADD", targetId = "#paperId")
    @Operation(summary = "제휴 계약서 내용(옵션) 추가 API (백오피스용)", description = "생성되어 있는 빈 제휴 계약서에 구체적인 제휴 혜택/옵션 및 Goods를 추가합니다.")
    @PostMapping("/{paperId}/content")
    public BaseResponse<WritePartnershipResponseDTO> addPaperContents(
            @PathVariable @Parameter(description = "계약서 ID", required = true) Long paperId,
            @RequestBody @Valid BackofficePaperContentCreateRequestDTO req
    ) {
        WritePartnershipResponseDTO response = backofficePaperService.addPaperContents(paperId, req);
        return BaseResponse.onSuccess(SuccessStatus._OK, response);
    }

    @BackofficeAudited(action = "PAPER_APPROVE", targetId = "#paperId")
    @Operation(summary = "제휴 계약서 승인 API (백오피스용)", description = "제휴 계약서를 승인하여 제휴 상태를 ACTIVE로 변경합니다.")
    @PatchMapping("/{paperId}/approve")
    public BaseResponse<Void> approvePaper(
            @PathVariable @Parameter(description = "계약서 ID", required = true) Long paperId
    ) {
        backofficePaperService.approvePaper(paperId);
        return BaseResponse.onSuccess(SuccessStatus._OK, null);
    }

    @BackofficeAudited(action = "PAPER_REJECT", targetId = "#paperId")
    @Operation(summary = "제휴 계약서 거부 API (백오피스용)", description = "제휴 계약서를 거부하여 제휴 상태를 INACTIVE로 변경합니다.")
    @PatchMapping("/{paperId}/reject")
    public BaseResponse<Void> rejectPaper(
            @PathVariable @Parameter(description = "계약서 ID", required = true) Long paperId
    ) {
        backofficePaperService.rejectPaper(paperId);
        return BaseResponse.onSuccess(SuccessStatus._OK, null);
    }

    @BackofficeAudited(action = "PAPER_EXPIRE", targetId = "#paperId")
    @Operation(summary = "제휴 계약서 만료 API (백오피스용)", description = "제휴 계약서를 강제 만료하여 제휴 상태를 INACTIVE로 변경합니다.")
    @PatchMapping("/{paperId}/expire")
    public BaseResponse<Void> expirePaper(
            @PathVariable @Parameter(description = "계약서 ID", required = true) Long paperId
    ) {
        backofficePaperService.expirePaper(paperId);
        return BaseResponse.onSuccess(SuccessStatus._OK, null);
    }
}

