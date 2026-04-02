package com.assu.server.domain.admin.dto;

public record StoreUsageWithPaper(
        Long paperId,
        Long storeId,
        String storeName,
        Long usageCount
) {}
