package com.assu.server.domain.map.service;

import com.assu.server.domain.map.dto.MapResponseDTO;
import com.assu.server.global.config.KakaoLocalClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceSearchServiceImpl implements PlaceSearchService {

    private final KakaoLocalClient kakaoLocalClient;
    private static final int NEARBY_DEFAULT_RADIUS = 500;

    @Override
    public List<MapResponseDTO.PlaceSuggestionDTO> unifiedSearch(String query, Integer size) {
        int kSize = (size == null ? 15 : size);

        // 1) 주소로도 시도 → 좌표 얻기 (성공/실패 무관)
        Double x = null, y = null; // 경도/위도
        KakaoLocalClient.KakaoAddressResp addrResp = kakaoLocalClient.searchByAddress(query, 1, 5);
        if (addrResp != null && addrResp.getDocuments() != null && !addrResp.getDocuments().isEmpty()) {
            var d = addrResp.getDocuments().get(0);
            // road_address 우선, 없으면 address
            String sx = d.getRoad_address() != null ? d.getRoad_address().getX()
                    : (d.getAddress() != null ? d.getAddress().getX() : d.getX());
            String sy = d.getRoad_address() != null ? d.getRoad_address().getY()
                    : (d.getAddress() != null ? d.getAddress().getY() : d.getY());
            if (sx != null && sy != null) {
                try {
                    x = Double.parseDouble(sx);
                    y = Double.parseDouble(sy);
                } catch (NumberFormatException ignore) {}
            }
        }

        // 2) 키워드 검색 (좌표가 있으면 바이어스)
        var kw = kakaoLocalClient.searchByKeyword(query, x, y, null, 1, kSize);
        List<MapResponseDTO.PlaceSuggestionDTO> kwList = convertKeyword(kw);

        // 3) 좌표가 있으면 카테고리 근접 검색 보강 (음식점/카페 등)
        List<MapResponseDTO.PlaceSuggestionDTO> nearby = Collections.emptyList();
        if (x != null && y != null) {
            List<String> cats = List.of("FD6", "CE7"); // 음식점/카페 (필요시 카테고리 추가)
            List<MapResponseDTO.PlaceSuggestionDTO> merged = new ArrayList<>();
            for (String c : cats) {
                var r = kakaoLocalClient.searchByCategory(c, x, y, NEARBY_DEFAULT_RADIUS, 1, kSize);
                merged.addAll(convertKeyword(r));
            }
            // 거리 오름차순
            merged.sort(Comparator.comparing(dto -> Optional.ofNullable(dto.getDistance()).orElse(Integer.MAX_VALUE)));
            nearby = merged;
        }

        // 4) 결과 합치기 (키워드 우선 → 근접 결과 추가, id로 dedupe)
        Map<String, MapResponseDTO.PlaceSuggestionDTO> dedupe = new LinkedHashMap<>();
        Stream.concat(kwList.stream(), nearby.stream())
                .forEach(dto -> dedupe.putIfAbsent(dto.getPlaceId(), dto));

        // 최종 상위 size 개 제한
        return dedupe.values().stream().limit(kSize).toList();
    }

    private List<MapResponseDTO.PlaceSuggestionDTO> convertKeyword(KakaoLocalClient.KakaoKeywordResp resp) {
        if (resp == null || resp.getDocuments() == null) return List.of();
        List<MapResponseDTO.PlaceSuggestionDTO> out = new ArrayList<>();
        for (var d : resp.getDocuments()) {
            Double x = safeParse(d.getX());
            Double y = safeParse(d.getY());
            Integer dist = safeParseInt(d.getDistance()); // null 가능
            out.add(MapResponseDTO.PlaceSuggestionDTO.builder()
                    .placeId(d.getId())
                    .name(d.getPlace_name())
                    .category(d.getCategory_group_name() != null ? d.getCategory_group_name() : d.getCategory_name())
                    .address(d.getAddress_name())
                    .roadAddress(d.getRoad_address_name())
                    .phone(d.getPhone())
                    .placeUrl(d.getPlace_url())
                    .longitude(x)
                    .latitude(y)
                    .distance(dist)
                    .build());
        }
        return out;
    }

    private Double safeParse(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }
    private Integer safeParseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
