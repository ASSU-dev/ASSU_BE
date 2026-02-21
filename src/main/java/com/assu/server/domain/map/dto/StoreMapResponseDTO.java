package com.assu.server.domain.map.dto;

import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.infra.s3.AmazonS3Manager;

public record StoreMapResponseDTO(
        Long storeId,
        Long adminId,
        String adminName,
        String name,
        String address,
        Integer rate,
        CriterionType criterionType,
        OptionType optionType,
        Integer people,
        Long cost,
        String category,
        String note,
        Long discountRate,
        boolean hasPartner,
        Double latitude,
        Double longitude,
        String profileUrl,
        String phoneNumber
) {
    public static StoreMapResponseDTO of(
            Store store, PaperContent content, Long adminId, String adminName, AmazonS3Manager s3Manager
    ) {
        final boolean hasPartner = store.getPartner() != null;
        final String key = (store.getPartner() != null && store.getPartner().getMember() != null)
                ? store.getPartner().getMember().getProfileUrl() : null;
        final String profileUrl = key != null ? s3Manager.generatePresignedUrl(key) : null;
        final String phoneNumber = (store.getPartner() != null
                && store.getPartner().getMember() != null
                && store.getPartner().getMember().getPhoneNum() != null)
                ? store.getPartner().getMember().getPhoneNum() : "";

        return new StoreMapResponseDTO(
                store.getId(), adminId, adminName, store.getName(),
                store.getAddress() != null ? store.getAddress() : store.getDetailAddress(),
                store.getRate(),
                content != null ? content.getCriterionType() : null,
                content != null ? content.getOptionType() : null,
                content != null ? content.getPeople() : null,
                content != null ? content.getCost() : null,
                content != null ? content.getCategory() : null,
                null,
                content != null ? content.getDiscount() : null,
                hasPartner,
                store.getLatitude(), store.getLongitude(),
                profileUrl, phoneNumber
        );
    }

    public static StoreMapResponseDTO ofSearch(
            Store store, PaperContent content, String finalCategory,
            Long adminId, String adminName, AmazonS3Manager s3Manager
    ) {
        final boolean hasPartner = store.getPartner() != null;
        final String key = (store.getPartner() != null && store.getPartner().getMember() != null)
                ? store.getPartner().getMember().getProfileUrl() : null;
        final String profileUrl = (key != null && !key.isBlank()) ? s3Manager.generatePresignedUrl(key) : null;
        final String phoneNumber = (store.getPartner() != null
                && store.getPartner().getMember() != null
                && store.getPartner().getMember().getPhoneNum() != null)
                ? store.getPartner().getMember().getPhoneNum() : "";

        return new StoreMapResponseDTO(
                store.getId(), adminId, adminName, store.getName(),
                store.getAddress() != null ? store.getAddress() : store.getDetailAddress(),
                store.getRate(),
                content != null ? content.getCriterionType() : null,
                content != null ? content.getOptionType() : null,
                content != null ? content.getPeople() : null,
                content != null ? content.getCost() : null,
                finalCategory,
                content != null ? content.getNote() : null,
                content != null ? content.getDiscount() : null,
                hasPartner,
                store.getLatitude(), store.getLongitude(),
                profileUrl, phoneNumber
        );
    }
}
