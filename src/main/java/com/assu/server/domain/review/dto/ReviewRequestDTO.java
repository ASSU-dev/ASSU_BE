package com.assu.server.domain.review.dto;

import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.entity.ReviewPhoto;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.user.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class ReviewRequestDTO {
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WriteReviewRequestDTO {
        @Schema(description = "리뷰 내용", example = "정말 맛있었어요!")
        private String content;

        @Schema(description = "별점 (1-10)", example = "5", minimum = "1", maximum = "10")
        private Integer rate;

        @Schema(hidden = true)
        private List<MultipartFile> reviewImage;

        @Schema(description = "가게 ID", example = "3")
        private Long storeId;

        @Schema(description = "파트너 ID", example = "2")
        private Long partnerId;

        private Long partnershipUsageId;
        private String adminName;

        public Review toEntity(Store store, Partner partner, Student student, String affiliation) {
                return null;
        }
    }
}
