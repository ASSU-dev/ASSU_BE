package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.*;
import com.assu.server.domain.backoffice.service.BackofficeAdminService;
import com.assu.server.domain.backoffice.service.BackofficeMemberService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/admins")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeAdminController {

	private final BackofficeAdminService backofficeAdminService;

	private final BackofficeMemberService backofficeMemberService;

	@Operation(
			summary = "인감 이미지 조회 API",
			description = "# [v1.0 (2026-07-03)]\n" +
					"- Admin 회원의 인감 이미지 S3 presigned URL을 반환합니다.\n" +
					"- `BACKOFFICE` 역할 및 `aud=backoffice` JWT가 필요합니다.\n\n" +
					"**Path Variable:**\n" +
					"- `memberId` (Long, required): Admin 회원 ID\n\n" +
					"**Response:**\n" +
					"- 성공 시 200(OK)과 presigned URL 반환 (약 10분 유효)\n" +
					"- 401(UNAUTHORIZED): 인증되지 않았거나 audience 불일치\n" +
					"- 403(FORBIDDEN): BACKOFFICE 권한 없음\n" +
					"- 404(NOT_FOUND): Admin이 아니거나 인감 이미지 없음"
	)
	@GetMapping("/{memberId}/sign-image")
	public BaseResponse<BackofficeDocumentUrlResponseDTO> getSignImageUrl(
			@Parameter(description = "Admin 회원 ID") @PathVariable Long memberId
	) {
		return BaseResponse.onSuccess(
				SuccessStatus._OK,
				backofficeMemberService.getAdminSignImageUrl(memberId)
		);
	}

	@Operation(
			summary = "모든 학생회 계정 조회 API",
			description = "시스템에 등록된 모든 학생회(Admin) 계정 목록을 조회합니다.\n\n" +
					"**Response:**\n" +
					"  - 성공 시 200(OK)과 `BackofficeAdminFetchResponseDTO` 객체 반환.\n" +
					"  - `adminList` (List): 학생회 계정 목록\n" +
					"    - `adminId` (Long): 학생회 ID\n" +
					"    - `email` (String): 이메일 주소\n" +
					"    - `name` (String): 이름\n" +
					"    - `phoneNumber` (String): 전화번호\n" +
					"    - `university` (University): 대학교 (CAU, etc.)\n" +
					"    - `department` (Department): 학과 (ENGINEERING, etc.)\n" +
					"    - `major` (Major): 전공 (CSE, etc.)\n" +
					"    - `officeAddress` (String): 학생회실 주소\n" +
					"    - `detailAddress` (String): 상세 주소\n" +
					"    - `signImageUrl` (String): 서명 이미지 URL\n" +
					"    - `isPhoneVerified` (Boolean): 전화번호 인증 여부\n"
	)
	@GetMapping
	public BaseResponse<BackofficeAdminFetchResponseDTO> fetchAdmin() {
		return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAdminService.fetchAdmin());
	}

	@BackofficeAudited(action = "ADMIN_CREATE", targetId = "#req.email")
	@Operation(
			summary = "학생회 계정 임의 추가 API",
			description = "인감 정보나 전화번호 없이 백오피스에서 임의로 학생회 계정을 추가합니다.\n\n" +
					"**Request Body:**\n" +
					"  - `BackofficeAdminCreateRequest` 객체 (JSON, required)\n" +
					"  - `email` (String, required): 이메일 주소\n" +
					"  - `password` (String, required): 비밀번호\n" +
					"  - `name` (String, required): 이름\n" +
					"  - `phoneNumber` (String, optional): 전화번호\n" +
					"  - `university` (University, required): 대학교 (CAU, etc.)\n" +
					"  - `department` (Department, required): 학과 (ENGINEERING, etc.)\n" +
					"  - `major` (Major, required): 전공 (CSE, etc.)\n" +
					"  - `officeAddress` (String, optional): 학생회실 주소\n" +
					"  - `detailAddress` (String, optional): 상세 주소\n" +
					"  - `latitude` (Double, optional): 위도\n" +
					"  - `longitude` (Double, optional): 경도\n\n" +
					"**Response:**\n" +
					"  - 성공 시 200(OK)과 `BackofficeAdminCreateResponseDTO` 객체 반환.\n" +
					"  - `adminId` (Long): 생성된 학생회 ID\n" +
					"  - `email` (String): 생성된 학생회 이메일\n" +
					"  - `name` (String): 생성된 학생회 이름\n" +
					"  - `createdAt` (LocalDateTime): 계정 생성 시간\n"
	)
	@PostMapping
	public BaseResponse<BackofficeAdminCreateResponseDTO> createAdmin(
		@RequestBody @Valid BackofficeAdminCreateRequestDTO req
	) {
		return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAdminService.createAdmin(req));
	}

	@BackofficeAudited(action = "ADMIN_UPDATE", targetId = "#adminId")
	@Operation(
			summary = "학생회 계정 임의 수정 API",
			description = "학생회(Admin) 계정 정보를 수정합니다. 입력된 필드만 반영됩니다.\n\n" +
					"**Parameters:**\n" +
					"  - `adminId` (Long, required): 수정할 학생회 ID\n\n" +
					"**Request Body:**\n" +
					"  - `BackofficeAdminUpdateRequest` 객체 (JSON, required)\n" +
					"  - `email` (String, optional): 이메일 주소\n" +
					"  - `password` (String, optional): 비밀번호\n" +
					"  - `name` (String, optional): 이름\n" +
					"  - `phoneNumber` (String, optional): 전화번호\n" +
					"  - `university` (University, optional): 대학교\n" +
					"  - `department` (Department, optional): 학과\n" +
					"  - `major` (Major, optional): 전공\n" +
					"  - `officeAddress` (String, optional): 학생회실 주소\n" +
					"  - `detailAddress` (String, optional): 상세 주소\n" +
					"  - `latitude` (Double, optional): 위도\n" +
					"  - `longitude` (Double, optional): 경도\n\n" +
					"**Response:**\n" +
					"  - 성공 시 200(OK)과 `BackofficeAdminUpdateResponseDTO` 객체 반환.\n" +
					"  - `adminId` (Long): 수정된 학생회 ID\n" +
					"  - `email` (String): 수정된 이메일\n" +
					"  - `name` (String): 수정된 이름\n" +
					"  - `phoneNumber` (String): 수정된 전화번호\n" +
					"  - `university` (University): 수정된 대학교\n" +
					"  - `department` (Department): 수정된 학과\n" +
					"  - `major` (Major): 수정된 전공\n" +
					"  - `officeAddress` (String): 수정된 학생회실 주소\n" +
					"  - `detailAddress` (String): 수정된 상세 주소\n" +
					"  - `updatedAt` (LocalDateTime): 수정 완료 시간\n"
	)
	@PatchMapping("/{adminId}")
	public BaseResponse<BackofficeAdminUpdateResponseDTO> updateAdmin(
		@PathVariable @Parameter(description = "수정할 학생회 ID", required = true) Long adminId,
		@RequestBody @Valid BackofficeAdminUpdateRequestDTO req
	) {
		return BaseResponse.onSuccess(SuccessStatus._OK, backofficeAdminService.updateAdmin(adminId, req));
	}

	@BackofficeAudited(action = "ADMIN_DELETE", targetId = "#adminId")
	@Operation(
			summary = "학생회 계정 임의 삭제 API",
			description = "학생회(Admin) 계정과 이에 연관된 모든 데이터(Member, CommonAuth)를 영구 삭제합니다.\n\n" +
					"**Parameters:**\n" +
					"  - `adminId` (Long, required): 삭제할 학생회 ID\n\n" +
					"**Response:**\n" +
					"  - 성공 시 200(OK) 반환 (result=null)\n"
	)
	@DeleteMapping("/{adminId}")
	public BaseResponse<Void> deleteAdmin(
		@PathVariable @Parameter(description = "삭제할 학생회 ID", required = true) Long adminId
	) {
		backofficeAdminService.deleteAdmin(adminId);
		return BaseResponse.onSuccess(SuccessStatus._OK, null);
	}
}
