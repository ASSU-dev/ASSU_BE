package com.assu.server.domain.map.dto;

public record PlaceSuggestionDTO(
        String placeId,
        String name,
        String category,
        String address,
        String roadAddress,
        String phone,
        String placeUrl,
        Double latitude,
        Double longitude,
        Integer distance
) {}
