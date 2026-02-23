package com.assu.server.domain.mapping.dto;

public record StoreUsageWithPaper(
        Long paperId,
        Long storeId,
        String storeName,
        Long usageCount
) {}
