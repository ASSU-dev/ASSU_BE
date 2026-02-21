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
@Tag(name = "유저 관련 api", description = "유저와 관련된 로직을 처리하는 api")
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
			summary = "사용자의 이용 가능한 제휴 조회 API",
			description = "# [v1.0 (2025-10-30)](https://clumsy-seeder-416.notion.site/API-29c1197c19ed8030b1f5e2a744416651?source=copy_link)\n" +
					"- all = true면 전체 조회, false면 2개만 조회"
	)
	@GetMapping("/usable")
	public BaseResponse<List<StudentResponseDTO.UsablePartnershipDTO>> getUsablePartnership(
			@AuthenticationPrincipal PrincipalDetails pd,
			@RequestParam(name = "all", defaultValue = "false") boolean all
	) {
		return BaseResponse.onSuccess(SuccessStatus._OK, studentService.getUsablePartnership(pd.getId(), all));
	}

	@PostMapping("/sync/all")
	public BaseResponse<String> syncAllStudentsNow() {
		studentService.syncUserPapersForAllStudents();
		return BaseResponse.onSuccess(SuccessStatus._OK, "전체 학생 user_paper 동기화 완료");
	}

}
