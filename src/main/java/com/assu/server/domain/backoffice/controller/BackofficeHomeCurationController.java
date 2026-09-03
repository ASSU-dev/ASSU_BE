package com.assu.server.domain.backoffice.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

@Tag(name = "Backoffice Home Curation", description = "백오피스 학생 홈 큐레이션 관리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/backoffice/home/curation")
@PreAuthorize("hasRole('BACKOFFICE')")
public class BackofficeHomeCurationController {

    private final BackofficeHomeCurationService backofficeHomeCurationService;

    @BackofficeAudited(action = "HOME_CURATION_READ")
    @Operation(
            summary = "홈 화면 큐레이션 현재 설정 조회 API (백오피스용)",
            description = "현재 백오피스에 등록되어 학생 홈에 노출 중인 큐레이션 설정(제목, 상단 추천 업체, 2개 그룹 x 각 2개 업체)을 가져옵니다.\n\n" +
                    "**Response:**\n" +
                    "  - 성공 시 200(OK)과 `BackofficeHomeCurationResponseDTO` 반환"
    )
    @GetMapping
    public BaseResponse<BackofficeHomeCurationResponseDTO> getHomeCuration() {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeHomeCurationService.getHomeCuration());
    }

    @BackofficeAudited(action = "HOME_CURATION_UPDATE")
    @Operation(
            summary = "전체 큐레이션 및 추천 업체 직접 저장 API (백오피스용)",
            description = "상단 추천 업체 및 2개 그룹(각 그룹당 2개 매장)을 직접 지정하여 저장합니다.\n\n" +
                    "**Request Body:**\n" +
                    "  - `title`: 큐레이션 섹션 제목\n" +
                    "  - `featuredStoreId`: 상단 추천 업체 ID\n" +
                    "  - `featuredDiscountContent`: 상단 추천 할인 문구 (선택)\n" +
                    "  - `curationLists`: 2개 그룹(각 그룹당 2개 상점) 정보\n\n" +
                    "**Response:**\n" +
                    "  - 성공 시 200(OK)과 수정된 `BackofficeHomeCurationResponseDTO` 반환"
    )
    @PutMapping
    public BaseResponse<BackofficeHomeCurationResponseDTO> updateHomeCuration(
            @RequestBody @Valid BackofficeHomeCurationUpdateRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeHomeCurationService.updateHomeCuration(request));
    }

    @BackofficeAudited(action = "HOME_CURATION_TITLE_UPDATE")
    @Operation(
            summary = "큐레이션 섹션 제목 단독 수정 API (백오피스용)",
            description = "추천 업체 목록은 그대로 두고 제목 텍스트만 변경할 때 사용합니다.\n\n" +
                    "**Request Body:**\n" +
                    "  - `title`: 수정할 큐레이션 섹션 제목 (예: {name}님을 위한 특별 혜택)\n\n" +
                    "**Response:**\n" +
                    "  - 성공 시 200(OK)과 수정된 `BackofficeHomeCurationResponseDTO` 반환"
    )
    @PatchMapping("/title")
    public BaseResponse<BackofficeHomeCurationResponseDTO> updateCurationTitle(
            @RequestBody @Valid BackofficeCurationTitleUpdateRequestDTO request
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeHomeCurationService.updateCurationTitle(request));
    }

    @BackofficeAudited(action = "HOME_CURATION_AUTO_RECOMMEND_READ")
    @Operation(
            summary = "추천 업체 자동 추출 API (백오피스 서브 기능 버튼용)",
            description = "평점 및 활성 매장 기반으로 상단 추천 1곳 + 2개 그룹(각 2곳)을 자동 선별합니다. 백오피스 UI의 [자동 추천 불러오기] 버튼 클릭 시 호출하여 폼에 자동 입력해 주는 용도입니다.\n\n" +
                    "**Response:**\n" +
                    "  - 성공 시 200(OK)과 `BackofficeHomeAutoRecommendResponseDTO` 반환"
    )
    @GetMapping("/auto-recommend")
    public BaseResponse<BackofficeHomeAutoRecommendResponseDTO> getAutoRecommendedCuration() {
        return BaseResponse.onSuccess(SuccessStatus._OK, backofficeHomeCurationService.getAutoRecommendedCuration());
    }
}
