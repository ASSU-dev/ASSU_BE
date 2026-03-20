package com.assu.server.domain.user.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.domain.user.dto.StudentResponseDTO;
import com.assu.server.domain.user.service.StudentService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@Tag(name = "Student", description = "학생 API")
@RequiredArgsConstructor
@RequestMapping("/students")
public class StudentController {

	private final StudentService studentService;

	@GetMapping("/partnerships/{year}/{month}")
	@Operation(
		summary = "월별 제휴 사용내역 조회 API",
		description = "# [v1.0 (2025-09-09)](https://www.notion.so/_-2241197c19ed8134bd49d8841e841634?source=copy_link)\n" +
			"- `multipart/form-data`로 호출합니다.\n" +
			"\n**Request Parts:**\n" +
			"  - `year` (Integer, required): 년도\n" +
			"  - `month` (Long, required): 월\n"+
			"\n**Response:**\n" +
			"  - 성공 시 partnership Usage 내역 반환 \n"+
			"  - 해당 storeId, storeName 반환"+
			"  - 해당 월에 사용한 제휴 수 반환"
	)
	public ResponseEntity<BaseResponse<StudentResponseDTO.MyPartnership>> getMyPartnership(
		@PathVariable int year, @PathVariable int month, @AuthenticationPrincipal PrincipalDetails pd
	){
		StudentResponseDTO.MyPartnership result = studentService.getMyPartnership(pd.getId(), year, month);

		return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus.PARTNERSHIP_HISTORY_SUCCESS, result));
	}

	@GetMapping("/usage")
	@Operation(
		summary = "리뷰되지 않은 제휴 사용내역 조회 API",
		description = "# [v1.0 (2025-09-10)](https://www.notion.so/_-24c1197c19ed809a9d81e8f928e8355f?source=copy_link)\n" +
			"- `multipart/form-data`로 호출합니다.\n" +
			"\n**Request:**\n" +
			"  - page : (Int, required) 이상의 정수 \n" +
			"  - size : (Int, required) 기본 값 10 \n" +
			"  - sort : (String, required) createdAt/desc 문자열로 입력\n" +
			"\n**Response:**\n" +
			"  - 성공 시 리뷰 되지 않은 partnership Usage 내역 반환 \n"+
			"  - StudentResponseTO.UsageDetailDTO 객체 반환 \n"

	)
	public ResponseEntity<BaseResponse<Page<StudentResponseDTO.UsageDetail>>> getUnreviewedUsage(
		@AuthenticationPrincipal PrincipalDetails pd,
		Pageable pageable
	){
		return ResponseEntity.ok(BaseResponse
			.onSuccess(SuccessStatus.UNREVIEWED_HISTORY_SUCCESS,
				studentService.getUnreviewedUsage(pd.getId(), pageable)));
	}

	@Operation(
		summary = "사용자 스탬프 개수 조회 API",
		description = "# [v1.0 (2025-09-09)](https://www.notion.so/2691197c19ed805c980dd546adee9301?source=copy_link)\n" +
			"- `multipart/form-data`로 호출합니다.\n" +
			"- login 필요 "+
			"\n**Response:**\n" +
			"  - stamp 개수 반환 \n"
	)
    @GetMapping("/stamp")
    public BaseResponse<StudentResponseDTO.CheckStampResponseDTO> getStamp(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, studentService.getStamp(pd.getId()));
    }
	@Operation(
			summary = "스탬프 적립 및 이벤트 응모 API",
			description = "# [v1.0 (2026-02-23)](https://clumsy-seeder-416.notion.site/3101197c19ed80b5b47eceb202535469)\n" +
					"- 스탬프가 10개가 되는 시점에 자동으로 응모및 알림"
	)
	@PostMapping("/stamp")
	public BaseResponse<String> earnStamp(
			@AuthenticationPrincipal PrincipalDetails pd
	) {
		studentService.addStamp(pd.getId());
		return BaseResponse.onSuccess(SuccessStatus._OK, "스탬프 적립 성공");
	}

	@Operation(
			summary = "사용자의 이용 가능한 제휴 조회 API",
			description = "# [v1.0 (2025-10-30)](https://clumsy-seeder-416.notion.site/API-29c1197c19ed8030b1f5e2a744416651?source=copy_link)\n" +
					"- 현재 로그인한 사용자가 이용 가능한 제휴 목록을 조회합니다.\n" +
					"- 활성 상태인 제휴만 반환합니다.\n\n" +
					"**Request Parameters:**\n" +
					"- `all` (Boolean, optional): 전체 조회 여부 - 기본값: false\n" +
					"  - true: 모든 이용 가능한 제휴 조회\n" +
					"  - false: 최대 2개만 조회\n\n" +
					"**Response:**\n" +
					"- 성공 시 200(OK)와 이용 가능한 제휴 목록 반환\n" +
					"- 401(UNAUTHORIZED): 인증되지 않은 사용자\n" +
					"- 404(NOT_FOUND): 사용자 정보를 찾을 수 없음"
	)
	@GetMapping("/usable")
	public BaseResponse<List<StudentResponseDTO.UsablePartnershipDTO>> getUsablePartnership(
			@AuthenticationPrincipal PrincipalDetails pd,
			@RequestParam(name = "all", defaultValue = "false") boolean all
	) {
		return BaseResponse.onSuccess(SuccessStatus._OK, studentService.getUsablePartnership(pd.getId(), all));
	}

	@Operation(
			summary = "전체 학생의 사용 가능 제휴 동기화 API",
			description = "# [v1.0 (2026-03-16)](https://clumsy-seeder-416.notion.site/3251197c19ed8066885cece9ffc455f6?source=copy_link)\n" +
					"- 모든 학생의 user_paper 데이터를 동기화합니다.\n" +
					"- 관리자 전용 API입니다.\n" +
					"- 시스템 전체에 영향을 주는 작업이므로 주의해서 사용해야 합니다.\n\n" +
					"**주의사항:**\n" +
					"- 대량의 데이터 처리로 인해 시간이 오래 걸릴 수 있음\n" +
					"- 실행 중에는 다른 제휴 관련 작업에 영향을 줄 수 있음\n\n" +
					"**Response:**\n" +
					"- 성공 시 200(OK)와 동기화 완료 메시지 반환\n" +
					"- 403(FORBIDDEN): 관리자 권한 없음\n" +
					"- 500(INTERNAL_SERVER_ERROR): 동기화 작업 실패"
	)
	@PostMapping("/sync/all")
	public BaseResponse<String> syncAllStudentsNow() {
		studentService.syncUserPapersForAllStudents();
		return BaseResponse.onSuccess(SuccessStatus._OK, "전체 학생 user_paper 동기화 완료");
	}

}
