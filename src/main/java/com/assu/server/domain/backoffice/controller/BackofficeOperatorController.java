package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeOperatorCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeOperatorResponseDTO;
import com.assu.server.domain.backoffice.service.BackofficeOperatorService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/operators")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeOperatorController {

    private final BackofficeOperatorService backofficeOperatorService;

    @BackofficeAudited(action = "OPERATOR_CREATE")
    @Operation(summary = "백오피스 운영자 생성 API")
    @PostMapping
    public BaseResponse<BackofficeOperatorResponseDTO> createOperator(
            @RequestBody @Valid BackofficeOperatorCreateRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeOperatorService.createOperator(request));
    }

    @Operation(summary = "백오피스 운영자 목록 조회 API")
    @GetMapping
    public BaseResponse<List<BackofficeOperatorResponseDTO>> listOperators() {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeOperatorService.listOperators());
    }
}
