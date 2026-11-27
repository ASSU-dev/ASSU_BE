package com.assu.server.domain.admin.dto;

public record StoreUsageWithPaperDTO(
        Long paperId,
        Long storeId,
        String storeName,
        Long usageCount
) {}
