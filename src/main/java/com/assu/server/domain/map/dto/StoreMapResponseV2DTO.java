package com.assu.server.domain.map.dto;

public record StoreMapResponseV2DTO(
        Long storeId,
        Long adminId,
        String adminName,
        String name,
        String address,
        Integer rate,
        boolean hasPartner,
        Double latitude,
        Double longitude,
        String profileUrl,
        String phoneNumber,
        String partner1,
        String partner2,
        String benefit1,
        String benefit2
) {}
