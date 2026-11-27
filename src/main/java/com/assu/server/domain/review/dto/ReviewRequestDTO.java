package com.assu.server.domain.review.dto;

import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.student.entity.Student;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class ReviewRequestDTO {
    @Builder
    public record WriteReviewRequestDTO(
        @Schema(description = "리뷰 내용", example = "정말 맛있었어요!") String content,
        @Schema(description = "별점 (1-5)", example = "5", minimum = "1", maximum = "5") Integer rate,
        @Schema(hidden = true) List<MultipartFile> reviewImage,
        @Schema(description = "가게 ID", example = "3") Long storeId,
        @Schema(description = "파트너 ID", example = "2") Long partnerId,
        Long partnershipUsageId,
        String adminName
    ) {
            public Review toEntity(Store store, Partner partner, Student student, String affiliation) {
            return Review.builder()
                    .rate(this.rate)
                    .content(this.content)
                    .store(store)
                    .partner(partner)
                    .student(student)
                    .affiliation(affiliation)
                    .build();
        }
    }
}
