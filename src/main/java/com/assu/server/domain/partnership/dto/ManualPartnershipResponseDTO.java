package com.assu.server.domain.partnership.dto;

import com.assu.server.domain.store.entity.Store;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ManualPartnershipResponseDTO(
        @Schema(description = "가게 ID", example = "201")
        @NotNull Long storeId,

        @Schema(description = "가게가 DB에 신규 생성되었는지 여부", example = "false")
        boolean storeCreated,

        @Schema(description = "가게가 재활성화되었는지 여부", example = "false")
        boolean storeActivated,

        @Schema(description = "제휴 제안서의 상태", example = "SUSPEND")
        @NotNull String status,

        @Schema(description = "계약서 파일 URL", example = "https://example.com/contract.jpg")
        @NotNull String contractImageUrl,

        @Schema(description = "제휴 제안서 상세 정보")
        @NotNull WritePartnershipResponseDTO partnership
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
