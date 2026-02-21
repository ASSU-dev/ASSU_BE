package com.assu.server.domain.map.controller;

import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.map.dto.MapRequestDTO;
import com.assu.server.domain.map.dto.MapResponseDTO;
import com.assu.server.domain.map.service.MapService;
import com.assu.server.domain.map.service.PlaceSearchService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/map")
public class MapController {

    private final MapService mapService;
    private final PlaceSearchService placeSearchService;

    @Operation(
            summary = "주변 장소 조회 API",
            description = "공간 인덱싱에 들어갈 좌표 4개를 경도, 위도 순서로 입력해주세요 (user -> store 조회 / admin -> partner 조회 / partner -> admin 조회)"
    )
    @GetMapping("/nearby")
    public BaseResponse<?> getLocations(
            @ModelAttribute MapRequestDTO.ViewOnMapDTO viewport,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        Long memberId = pd.getMember().getId();
        UserRole role = pd.getMember().getRole();

        return switch (role) {
            case STUDENT -> BaseResponse.onSuccess(SuccessStatus._OK, mapService.getStores(viewport, memberId));
            case ADMIN -> BaseResponse.onSuccess(SuccessStatus._OK, mapService.getPartners(viewport, memberId));
            case PARTNER -> BaseResponse.onSuccess(SuccessStatus._OK, mapService.getAdmins(viewport, memberId));
            default -> BaseResponse.onFailure(ErrorStatus._BAD_REQUEST, null);
        };
    }

    @Operation(
            summary = "주변 장소 조회 API",
            description = "공간 인덱싱에 들어갈 좌표 4개를 경도, 위도 순서로 입력해주세요 (user -> store 조회 / admin -> partner 조회 / partner -> admin 조회)"
    )
    @GetMapping("/nearby/v2")
    public BaseResponse<?> getLocationsV2(
            @ModelAttribute MapRequestDTO.ViewOnMapDTO viewport,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        Long memberId = pd.getMember().getId();
        return BaseResponse.onSuccess(SuccessStatus._OK, mapService.getStoresV2(viewport, memberId));
    }

    @Operation(
            summary = "검색어 기반 장소 조회 API",
            description = "검색어를 입력해주세요. (user → store 전체조회 / admin → partner 전체조회 / partner → admin 전체조회)"
    )
    @GetMapping("/search")
    public BaseResponse<?> getLocationsByKeyword(
            @RequestParam("searchKeyword") @NotNull String keyword,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        Long memberId = pd.getMember().getId();
        UserRole role = pd.getMember().getRole();

        return switch (role) {
            case STUDENT -> {
                List<MapResponseDTO.StoreMapResponseDTO> list = mapService.searchStores(keyword);
                yield BaseResponse.onSuccess(SuccessStatus._OK, list);
            }
            case ADMIN -> {
                List<MapResponseDTO.PartnerMapResponseDTO> list = mapService.searchPartner(keyword, memberId);
                yield BaseResponse.onSuccess(SuccessStatus._OK, list);
            }
            case PARTNER -> {
                List<MapResponseDTO.AdminMapResponseDTO> list = mapService.searchAdmin(keyword, memberId);
                yield BaseResponse.onSuccess(SuccessStatus._OK, list);
            }
            default -> BaseResponse.onFailure(ErrorStatus._BAD_REQUEST, null);
        };
    }

    @Operation(
            summary = "주소 입력 시 장소 검색용 API",
            description = "검색어를 기반으로 장소를 검색하여 리스트로 반환합니다. limit로 개수를 제한할 수 있습니다."
    )
    @GetMapping("/place")
    public BaseResponse<List<MapResponseDTO.PlaceSuggestionDTO>> search(
            @RequestParam("searchKeyword") String query,
            @RequestParam(value = "limit", required = false) Integer size
    ) {
        List<MapResponseDTO.PlaceSuggestionDTO> list = placeSearchService.unifiedSearch(query, size);
        return BaseResponse.onSuccess(SuccessStatus._OK, list);
    }

}
