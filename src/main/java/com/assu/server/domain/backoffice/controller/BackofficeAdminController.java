package com.assu.server.domain.backoffice.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.backoffice.dto.BackofficeAdminCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminCreateResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminFetchResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminUpdateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminUpdateResponseDTO;
import com.assu.server.domain.backoffice.service.BackofficeAdminService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/admin")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeAdminController {

	private final BackofficeAdminService backofficeAdminService;

	@Operation(summary = "모든 학생회 계정 조회 API", description = "시스템에 등록된 모든 학생회(Admin) 계정 목록을 조회합니다.")
	@GetMapping
	public BaseResponse<BackofficeAdminFetchResponseDTO> fetchAdmin() {
		return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAdminService.fetchAdmin());
	}

	@Operation(summary = "학생회 계정 임의 추가 API", description = "인감 정보나 전화번호 없이 백오피스에서 임의로 학생회 계정을 추가합니다.")
	@PostMapping
	public BaseResponse<BackofficeAdminCreateResponseDTO> createAdmin(
		@RequestBody @Valid BackofficeAdminCreateRequestDTO req
	) {
		return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAdminService.createAdmin(req));
	}

	@Operation(summary = "학생회 계정 임의 수정 API", description = "학생회(Admin) 계정 정보를 수정합니다. 입력된 필드만 반영됩니다.")
	@PatchMapping("/{adminId}")
	public BaseResponse<BackofficeAdminUpdateResponseDTO> updateAdmin(
		@PathVariable Long adminId,
		@RequestBody @Valid BackofficeAdminUpdateRequestDTO req
	) {
		return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAdminService.updateAdmin(adminId, req));
	}

	@Operation(summary = "학생회 계정 임의 삭제 API", description = "학생회(Admin) 계정과 이에 연관된 모든 데이터(Member, CommonAuth)를 영구 삭제합니다.")
	@DeleteMapping("/{adminId}")
	public BaseResponse<Void> deleteAdmin(
		@PathVariable Long adminId
	) {
		backofficeAdminService.deleteAdmin(adminId);
		return BaseResponse.onSuccess(SuccessStatus._OK, null);
	}
}

