package com.assu.server.domain.map.controller;

import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.map.dto.AdminMapResponseDTO;
import com.assu.server.domain.map.dto.MapRequestDTO;
import com.assu.server.domain.map.dto.PartnerMapResponseDTO;
import com.assu.server.domain.map.dto.PlaceSuggestionDTO;
import com.assu.server.domain.map.dto.StoreMapResponseDTO;
import com.assu.server.domain.map.service.MapService;
import com.assu.server.domain.map.service.PlaceSearchService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Map", description = "지도 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/map")
public class MapController {

    private final MapService mapService;
    private final PlaceSearchService placeSearchService;

    @Operation(
            summary = "주변 장소 조회 API",
            description = "# [v1.3 (2025-01-04)](https://clumsy-seeder-416.notion.site/2441197c19ed80bcb55fcad675dd9837?source=copy_link)\n" +
                    "- 로그인한 유저의 역할에 따라 Map 객체를 반환합니다.\n" +
                    "- 경도, 위도 순서로 입력한 Viewport 객체 입력.\n" +
                    "- 성공 시 200(OK)과 Map 객체 반환.\n"+
                    "\n**Request Body:**\n" +
                    "  - `viewport` 객체 (JSON, required): 공간인덱싱을 위한 경도, 위도 객체\n" +
                    "  - `lng1` (double): 좌 상단 경도\n" +
                    "  - `lat1` (double): 좌 상단 위도\n" +
                    "  - `lng2` (double): 우 상단 경도\n" +
                    "  - `lat2` (double): 우 상단 위도\n" +
                    "  - `lng3` (double): 우 하단 경도\n" +
                    "  - `lat3` (double): 우 하단 위도\n" +
                    "  - `lng4` (double): 좌 하단 경도\n" +
                    "  - `lat4` (double): 좌 하단 위도\n" +
                    "\n**Response:**\n" +
                    "  - `User`: 가게 조회\n" +
                    "    - `storeId` (Long): 가게 ID\n" +
                    "    - `adminId` (Long): 관리자 ID\n" +
                    "    - `adminName` (String): 관리자 이름\n" +
                    "    - `name` (String): 가게 이름\n" +
                    "    - `address` (String): 가게 주소\n" +
                    "    - `rate` (Integer): 가게 별점\n" +
                    "    - `criterionType` (CriterionType): 서비스 제공 기준(PRICE/HEADCOUNT)\n" +
                    "    - `optionType` (OptionType): 제공 서비스 종류(SERVICE/DISCOUNT)\n" +
                    "    - `people` (Integer): 인원 수(HEADCOUNT)\n" +
                    "    - `cost` (Long): 가격(PRICE)\n" +
                    "    - `category` (String): 카테고리(SERVICE)\n" +
                    "    - `note` (String): 제휴 설명 (형식에 맞지 않는 제휴일때)\n" +
                    "    - `discountRate` (Long): 할인률(DISCOUNT)\n" +
                    "    - `hasPartner` (boolean): 제휴업체인지 여부\n" +
                    "    - `latitude` (Double): 가게 위치 위도\n" +
                    "    - `longitude` (Double):  가게 위치 경도\n" +
                    "    - `profileUrl` (String): 가게 카카오맵 URL\n" +
                    "    - `phoneNumber` (String): 가게 전화번호\n" +
                    "  - `Admin`: 제휴업체 조회\n" +
                    "    - `partnerId` (Long): 제휴업체 ID\n" +
                    "    - `name` (String): 제휴업체 이름\n" +
                    "    - `address` (String): 제휴업체 주소\n" +
                    "    - `isPartnered` (boolean): 관리자와 제휴 여부\n" +
                    "    - `partnershipId` (Long): 제휴 ID\n" +
                    "    - `partnershipStartDate` (LocalDate): 제휴 시작일\n" +
                    "    - `partnershipEndDate` (LocalDate): 제휴 마감일\n" +
                    "    - `latitude` (Double): 제휴업체 위도\n" +
                    "    - `longitude` (Double): 제휴업체 경도\n" +
                    "    - `profileUrl` (String): 제휴업체 카카오맵 Url\n" +
                    "    - `phoneNumber` (String): 제휴업체 전화번호\n" +
                    "  - `Partner`: 관리자 조회\n" +
                    "    - `adminId` (Long): 관리자 ID\n" +
                    "    - `name` (String): 관리자 이름\n" +
                    "    - `address` (String): 관리자 주소\n" +
                    "    - `isPartnered` (boolean): 제휴업체와 제휴 여부\n" +
                    "    - `partnershipId` (Long): 제휴 ID\n" +
                    "    - `partnershipStartDate` (LocalDate): 제휴 시작일\n" +
                    "    - `partnershipEndDate` (LocalDate): 제휴 마감일\n" +
                    "    - `latitude` (Double): 관리자 위도\n" +
                    "    - `longitude` (Double): 관리자 경도\n" +
                    "    - `profileUrl` (String): 관리자 카카오맵 Url\n" +
                    "    - `phoneNumber` (String): 관리자 전화번호\n")
    @GetMapping("/nearby")
    public BaseResponse<?> getLocations(
            @ModelAttribute MapRequestDTO viewport,
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
            @ModelAttribute MapRequestDTO viewport,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        Long memberId = pd.getMember().getId();
        return BaseResponse.onSuccess(SuccessStatus._OK, mapService.getStoresV2(viewport, memberId));
    }

    @Operation(
            summary = "검색어 기반 장소 조회 API",
            description = "# [v1.3 (2025-01-04)](https://clumsy-seeder-416.notion.site/2591197c19ed8017adf1f3d711bab3d4)\n" +
                    "- 로그인한 유저의 역할과 검색어에 따라 Map 객체를 반환합니다.\n" +
                    "- 검색어(searchKeyword) 입력.\n" +
                    "- 성공 시 200(OK)과 Map 객체 반환.\n"+
                    "\n**Request Parts:**\n" +
                    "  - `searchKeyword` (String, required): 검색어\n" +
                    "\n**Response:**\n" +
                    "  - `User`: 가게 조회\n" +
                    "    - `storeId` (Long): 가게 ID\n" +
                    "    - `adminId` (Long): 관리자 ID\n" +
                    "    - `adminName` (String): 관리자 이름\n" +
                    "    - `name` (String): 가게 이름\n" +
                    "    - `address` (String): 가게 주소\n" +
                    "    - `rate` (Integer): 가게 별점\n" +
                    "    - `criterionType` (CriterionType): 서비스 제공 기준(PRICE/HEADCOUNT)\n" +
                    "    - `optionType` (OptionType): 제공 서비스 종류(SERVICE/DISCOUNT)\n" +
                    "    - `people` (Integer): 인원 수(HEADCOUNT)\n" +
                    "    - `cost` (Long): 가격(PRICE)\n" +
                    "    - `category` (String): 카테고리(SERVICE)\n" +
                    "    - `note` (String): 제휴 설명 (형식에 맞지 않는 제휴일때)\n" +
                    "    - `discountRate` (Long): 할인률(DISCOUNT)\n" +
                    "    - `hasPartner` (boolean): 제휴업체인지 여부\n" +
                    "    - `latitude` (Double): 가게 위치 위도\n" +
                    "    - `longitude` (Double):  가게 위치 경도\n" +
                    "    - `profileUrl` (String): 가게 카카오맵 URL\n" +
                    "    - `phoneNumber` (String): 가게 전화번호\n" +
                    "  - `Admin`: 제휴업체 조회\n" +
                    "    - `partnerId` (Long): 제휴업체 ID\n" +
                    "    - `name` (String): 제휴업체 이름\n" +
                    "    - `address` (String): 제휴업체 주소\n" +
                    "    - `isPartnered` (boolean): 관리자와 제휴 여부\n" +
                    "    - `partnershipId` (Long): 제휴 ID\n" +
                    "    - `partnershipStartDate` (LocalDate): 제휴 시작일\n" +
                    "    - `partnershipEndDate` (LocalDate): 제휴 마감일\n" +
                    "    - `latitude` (Double): 제휴업체 위도\n" +
                    "    - `longitude` (Double): 제휴업체 경도\n" +
                    "    - `profileUrl` (String): 제휴업체 카카오맵 Url\n" +
                    "    - `phoneNumber` (String): 제휴업체 전화번호\n" +
                    "  - `Partner`: 관리자 조회\n" +
                    "    - `adminId` (Long): 관리자 ID\n" +
                    "    - `name` (String): 관리자 이름\n" +
                    "    - `address` (String): 관리자 주소\n" +
                    "    - `isPartnered` (boolean): 제휴업체와 제휴 여부\n" +
                    "    - `partnershipId` (Long): 제휴 ID\n" +
                    "    - `partnershipStartDate` (LocalDate): 제휴 시작일\n" +
                    "    - `partnershipEndDate` (LocalDate): 제휴 마감일\n" +
                    "    - `latitude` (Double): 관리자 위도\n" +
                    "    - `longitude` (Double): 관리자 경도\n" +
                    "    - `profileUrl` (String): 관리자 카카오맵 Url\n" +
                    "    - `phoneNumber` (String): 관리자 전화번호\n")
    @GetMapping("/search")
    public BaseResponse<?> getLocationsByKeyword(
            @RequestParam("searchKeyword") @NotNull String keyword,
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        Long memberId = pd.getMember().getId();
        UserRole role = pd.getMember().getRole();

        return switch (role) {
            case STUDENT -> {
                List<StoreMapResponseDTO> list = mapService.searchStores(keyword);
                yield BaseResponse.onSuccess(SuccessStatus._OK, list);
            }
            case ADMIN -> {
                List<PartnerMapResponseDTO> list = mapService.searchPartner(keyword, memberId);
                yield BaseResponse.onSuccess(SuccessStatus._OK, list);
            }
            case PARTNER -> {
                List<AdminMapResponseDTO> list = mapService.searchAdmin(keyword, memberId);
                yield BaseResponse.onSuccess(SuccessStatus._OK, list);
            }
            default -> BaseResponse.onFailure(ErrorStatus._BAD_REQUEST, null);
        };
    }

    @Operation(
            summary = "주소 입력 시 장소 검색용 API",
            description = "# [v1.3 (2025-01-04)](https://clumsy-seeder-416.notion.site/25d1197c19ed807eb7a7c5ede2e75040)\n" +
                    "- 검색어에 따라 Map 객체를 반환합니다.\n" +
                    "- 검색어(searchKeyword) 입력.\n" +
                    "- limit을 통해 Map 객체 수 제한.\n" +
                    "- 성공 시 200(OK)과 Map 객체 반환.\n"+
                    "\n**Request Parts:**\n" +
                    "  - `searchKeyword` (String, required): 검색어\n" +
                    " - `limit` (Integer): 결과 객체 수\n" +
                    "\n**Response:**\n" +
                    "  - `placeId` (String): kakao place ID\n" +
                    "  - `name` (String): kakao place 이름\n" +
                    "  - `category` (String): kakao 카테고리 또는 그룹 이름\n" +
                    "  - `address` (String): 지번 주소\n" +
                    "  - `roadAddress` (String): 도로명 주소\n" +
                    "  - `phone` (String): 장소 전화번호\n" +
                    "  - `placeUrl` (String): kakao place 상세 URL\n" +
                    "  - `latitude` (Double): 장소 위도\n" +
                    "  - `longitude` (Double): 장소 경도\n" +
                    "  - `distance` (Integer): 장소의 m 좌표 (좌표바이어스/카테고리 검색 시 제공)\n")
    @GetMapping("/place")
    public BaseResponse<List<PlaceSuggestionDTO>> search(
            @RequestParam("searchKeyword") String query,
            @RequestParam(value = "limit", required = false) Integer size
    ) {
        List<PlaceSuggestionDTO> list = placeSearchService.unifiedSearch(query, size);
        return BaseResponse.onSuccess(SuccessStatus._OK, list);
    }

}
