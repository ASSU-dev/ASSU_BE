package com.assu.server.domain.map.dto;

import com.assu.server.domain.store.entity.Store;
import com.assu.server.infra.s3.AmazonS3Manager;

public record StoreMapResponseDTO(
        Long storeId,
        String name,
        String address,
        Integer rate,
        boolean hasPartner,
        Double latitude,
        Double longitude,
        String profileUrl,
        String phoneNumber,
        Long adminId1,
        Long adminId2,
        String adminName1,
        String adminName2,
        String benefit1,
        String benefit2
) {
    public static StoreMapResponseDTO of(
            Store store,
            Long adminId1, Long adminId2,
            String adminName1, String adminName2,
            String benefit1, String benefit2,
            AmazonS3Manager s3Manager
    ) {
        final boolean hasPartner = store.getPartner() != null;
        final String key = (store.getPartner() != null && store.getPartner().getMember() != null)
                ? store.getPartner().getMember().getProfileUrl() : null;
        final String profileUrl = (key != null && !key.isBlank())
                ? s3Manager.generatePresignedUrl(key) : null;
        final String phoneNumber = (store.getPartner() != null
                && store.getPartner().getMember() != null
                && store.getPartner().getMember().getPhoneNum() != null)
                ? store.getPartner().getMember().getPhoneNum() : "";

        return new StoreMapResponseDTO(
                store.getId(),
                store.getName(),
                store.getAddress() != null ? store.getAddress() : store.getDetailAddress(),
                store.getRate(),
                hasPartner,
                store.getLatitude(),
                store.getLongitude(),
                profileUrl,
                phoneNumber,
                adminId1, adminId2,
                adminName1, adminName2,
                benefit1, benefit2
        );
    }
}
