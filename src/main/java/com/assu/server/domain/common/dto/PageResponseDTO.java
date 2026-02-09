package com.assu.server.domain.common.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record PageResponseDTO<T>(
        List<T> items,
        int page,
        int size,
        int totalPages,
        long totalElements
) {
    public static <T> PageResponseDTO<T> of(Page<T> page) {
        return new PageResponseDTO<>(
                page.getContent(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements()
        );
    }
}

