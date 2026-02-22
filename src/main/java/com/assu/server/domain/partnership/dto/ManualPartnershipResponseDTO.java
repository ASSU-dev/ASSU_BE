package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.store.entity.Store;

public record ManualPartnershipResponseDTO(
        Long storeId,
        boolean storeCreated,
        boolean storeActivated,
        String status,
        String contractImageUrl,
        WritePartnershipResponseDTO partnership
) {
    public static ManualPartnershipResponseDTO of(
            Store store,
            boolean storeCreated,
            boolean storeActivated,
            String contractImageUrl,
            WritePartnershipResponseDTO partnership
    ) {
        return new ManualPartnershipResponseDTO(
                store.getId(),
                storeCreated,
                storeActivated,
                store.getIsActivate() == null ? null : store.getIsActivate().name(),
                contractImageUrl,
                partnership
        );
    }
}
