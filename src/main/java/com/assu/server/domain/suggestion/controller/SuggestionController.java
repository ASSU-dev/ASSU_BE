package com.assu.server.domain.suggestion.controller;

import com.assu.server.domain.suggestion.dto.SuggestionRequestDTO;
import com.assu.server.domain.suggestion.dto.SuggestionResponseDTO;
import com.assu.server.domain.suggestion.service.SuggestionService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Suggestion", description = "제휴 건의 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/suggestion")
public class SuggestionController {

    private final SuggestionService suggestionService;

    @Operation(
            summary = "제휴 건의 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/_-2241197c19ed81e68840d565af59b534)\n" +
                    "- 현재 로그인한 학생이 관리자에게 제휴를 건의합니다.\n" +
                    "- 성공 시 200(OK)과 Suggestion 객체 반환.\n" +
                    "\n**Request Body:**\n" +
                    "  - `suggestionRequest` 객체 (JSON, required)\n" +
                    "  - `adminId` (Long): 건의 대상 관리자 ID\n" +
                    "  - `storeName` (String): 희망 가게 이름\n" +
                    "  - `benefit` (String): 희망 혜택\n" +
                    "\n**Response:**\n" +
                    "  - `suggestionId` (Long): 건의 ID\n" +
                    "  - `userId` (Long): 제안인 ID\n" +
                    "  - `adminId` (Long): 건의 대상 관리자 ID\n" +
                    "  - `storeName` (String): 희망 가게 이름\n" +
                    "  - `suggestionBenefit` (String): 희망 혜택\n")
    @PostMapping
    public BaseResponse<SuggestionResponseDTO.WriteSuggestionResponseDTO> writeSuggestion(
            @RequestBody SuggestionRequestDTO.WriteSuggestionRequestDTO suggestionRequestDTO,
            @AuthenticationPrincipal PrincipalDetails pd
    ){
        return BaseResponse.onSuccess(SuccessStatus._OK, suggestionService.writeSuggestion(suggestionRequestDTO, pd.getId()));
    }

    @Operation(
            summary = "제휴 건의대상 조회 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/_-2621197c19ed808c9627fcb7f58f4538)\n" +
                    "- 현재 로그인한 학생이 건의할 수 있는 관리자를 조회합니다.\n" +
                    "- 성공 시 200(OK)과 SuggestionAdmins 객체 반환.\n" +
                    "\n**Response:**\n" +
                    "  - `adminId` (Long): 총학생회 ID\n" +
                    "  - `adminName` (String): 총학생회 이름\n" +
                    "  - `departId` (Long): 단과대학 학생회 ID\n" +
                    "  - `departName` (String): 단과대학 학생회 이름\n" +
                    "  - `majorId` (Long): 학부/학과 학생회 ID\n" +
                    "  - `majorName` (String): 학부/학과 학생회 이름\n")
    @GetMapping("/admin")
    public BaseResponse<SuggestionResponseDTO.GetSuggestionAdminsDTO> getSuggestionAdmins(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, suggestionService.getSuggestionAdmins(pd.getId()));
    }

    @Operation(
            summary = "제휴 건의 조회 API",
            description = "# [v1.3 (2026-01-04)](https://clumsy-seeder-416.notion.site/_-24c1197c19ed8083bf8be4b6a6a43f18)\n" +
                    "- 현재 로그인한 관리자가 받은 모든 제휴 건의를 조회합니다.\n" +
                    "- 제휴 건의는 작성일 기준 최신순으로 조회.\n" +
                    "- enrollmentStatus로 재학 상태 표시(ENROLLED, LEAVE, GRADUATED)\n" +
                    "- 성공 시 200(OK)과 Suggestion 객체 반환.\n" +
                    "\n**Response:**\n" +
                    "  - `suggestionId` (Long): 건의 ID\n" +
                    "  - `createdAt` (LocalDateTime): 건의 작성일\n" +
                    "  - `storeName` (String): 희망 가게 이름\n" +
                    "  - `content` (String): 건의 내용\n" +
                    "  - `studentMajor` (Long): 건의자의 학부/학과\n" +
                    "  - `enrollmentStatus` (EnrollmentStatus): 재학 상태\n")
    @GetMapping("/list")
    public BaseResponse<List<SuggestionResponseDTO.GetSuggestionResponseDTO>> getSuggestions(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, suggestionService.getSuggestions(pd.getId()));
    }
}
