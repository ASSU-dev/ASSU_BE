package com.assu.server.domain.mapping.controller;

import com.assu.server.domain.mapping.dto.StudentAdminResponseDTO;
import com.assu.server.domain.mapping.service.StudentAdminService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/dashBoard")
@Tag(name = "관리자 대시보드 API", description = "어드민 전용 통계 및 대시보드 데이터 조회 API")
public class StudentAdminController {

    private final StudentAdminService studentAdminService;

    @Operation(
            summary = "누적 가입자 수 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/_-24c1197c19ed8062be94fc08619b760f)\n" +
                    "- 관리자(Admin) 권한으로 접근하여 현재까지의 총 누적 가입자 수를 조회합니다."
    )
    @GetMapping
    public BaseResponse<StudentAdminResponseDTO.CountAdminAuthResponseDTO> getCountAdmin(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, studentAdminService.getCountAdminAuth(pd.getId()));
    }

    @Operation(
            summary = "신규 한 달 가입자 수 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/_-24c1197c19ed805db80fca98c38849d1)\n" +
                    "- 이번 달(매달 1일 초기화) 기준 신규 가입한 사용자 수를 조회합니다."
    )
    @GetMapping("/new")
    public BaseResponse<StudentAdminResponseDTO.NewCountAdminResponseDTO> getNewStudentCountAdmin(
            @AuthenticationPrincipal PrincipalDetails pd
    ){
        return BaseResponse.onSuccess(SuccessStatus._OK, studentAdminService.getNewStudentCountAdmin(pd.getId()));
    }

    @Operation(
            summary = "오늘 제휴 사용자 수 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/_-24e1197c19ed80a283b1c336a1c3df72)\n" +
                    "- 금일 제휴 서비스를 이용한 총 사용자 수를 조회합니다."
    )
    @GetMapping("/countUser")
    public BaseResponse<StudentAdminResponseDTO.CountUsagePersonResponseDTO> getCountUser(
            @AuthenticationPrincipal PrincipalDetails pd
    ){
        return BaseResponse.onSuccess(SuccessStatus._OK, studentAdminService.getCountUsagePerson(pd.getId()));
    }

    @Operation(
            summary = "제휴업체 누적별 1위 업체 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/_1-2ef1197c19ed8010a49ce6313d137b4f)\n" +
                    "- 제휴 이용 횟수가 가장 많은 1위 업체의 정보를 조회합니다."
    )
    @GetMapping("/top")
    public BaseResponse<StudentAdminResponseDTO.CountUsageResponseDTO> getTopUsage(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, studentAdminService.getCountUsage(pd.getId()));
    }

    @Operation(
            summary = "제휴업체 누적 사용 수 내림차순 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/_-24e1197c19ed802b92eff5d4dc4dbe82)\n" +
                    "- 모든 제휴 업체의 누적 사용 현황을 사용량 내림차순 리스트로 반환합니다."
    )
    @GetMapping("/usage")
    public BaseResponse<StudentAdminResponseDTO.CountUsageListResponseDTO> getUsageList(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, studentAdminService.getCountUsageList(pd.getId()));
    }
}