package com.assu.server.domain.backoffice.controller;

import com.assu.server.domain.backoffice.annotation.BackofficeAudited;
import com.assu.server.domain.backoffice.dto.BackofficeCurationTitleUpdateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeHomeAutoRecommendResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeHomeCurationResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeHomeCurationUpdateRequestDTO;
import com.assu.server.domain.backoffice.service.BackofficeHomeCurationService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Backoffice", description = "백오피스 운영 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/home/curation")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeHomeCurationController {

    private final BackofficeHomeCurationService backofficeHomeCurationService;

    @Operation(
            summary = "홈 화면 큐레이션 설정 조회 API (백오피스용)",
            description = "- 백오피스에서 현재 설정된 홈 화면 큐레이션 제목, 상단 추천 업체 및 2개 그룹(각 2개 업체)의 추천 목록을 조회합니다.\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)와 현재 큐레이션 설정 정보(`BackofficeHomeCurationResponseDTO`) 반환"
    )
    @GetMapping
    public BaseResponse<BackofficeHomeCurationResponseDTO> getHomeCuration() {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeHomeCurationService.getHomeCuration());
    }

    @BackofficeAudited(action = "HOME_CURATION_TITLE_UPDATE")
    @Operation(
            summary = "홈 화면 큐레이션 섹션 제목 수정 API (백오피스용)",
            description = "- 홈 화면 큐레이션 섹션의 제목을 수정합니다.\n" +
                    "- `{name}` 키워드를 포함하면 학생 홈 화면 조회 시 학생의 실명으로 자동 치환됩니다.\n\n" +
                    "**Request Body:**\n" +
                    "- `title` (String, required): 수정할 제목 문구 (예: '{name}님을 위한 제휴')\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)와 수정된 큐레이션 설정 정보 반환"
    )
    @PatchMapping("/title")
    public BaseResponse<BackofficeHomeCurationResponseDTO> updateCurationTitle(
            @Valid @RequestBody BackofficeCurationTitleUpdateRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeHomeCurationService.updateCurationTitle(request));
    }

    @BackofficeAudited(action = "HOME_CURATION_UPDATE")
    @Operation(
            summary = "홈 화면 큐레이션 전체 설정 및 추천 업체 지정 API (백오피스용)",
            description = "- 백오피스에서 홈 화면 상단 추천 업체 및 2개 그룹(각 2개 업체)의 추천 목록을 직접 지정하여 저장합니다.\n\n" +
                    "**Request Body:**\n" +
                    "- `title` (String, required): 큐레이션 섹션 제목\n" +
                    "- `featuredStoreId` (Long, required): 상단 추천 업체 ID\n" +
                    "- `featuredDiscountContent` (String, optional): 상단 추천 할인 문구\n" +
                    "- `curationLists` (List, required): 2개의 추천 그룹 목록 (각 그룹당 2개의 업체 지정)\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)와 저장된 큐레이션 설정 정보 반환"
    )
    @PutMapping
    public BaseResponse<BackofficeHomeCurationResponseDTO> updateHomeCuration(
            @Valid @RequestBody BackofficeHomeCurationUpdateRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeHomeCurationService.updateHomeCuration(request));
    }

    @Operation(
            summary = "홈 화면 추천 업체 자동 추출 API (백오피스 서브 기능용)",
            description = "- 백오피스 운영자가 버튼을 눌러 추천 업체(상단 추천 1개 + 2개 그룹 x 2개 업체)를 평점/인기도 기반으로 자동 추천받을 수 있는 서브 API입니다.\n" +
                    "- 반환된 추천안을 확인 후 전체 저장 API(`PUT /backoffice/home/curation`)를 통해 확정할 수 있습니다.\n\n" +
                    "**Response:**\n" +
                    "- 성공 시 200(OK)와 자동 추출된 추천 데이터(`BackofficeHomeAutoRecommendResponseDTO`) 반환"
    )
    @GetMapping("/auto-recommend")
    public BaseResponse<BackofficeHomeAutoRecommendResponseDTO> getAutoRecommendedCuration() {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeHomeCurationService.getAutoRecommendedCuration());
    }
}
