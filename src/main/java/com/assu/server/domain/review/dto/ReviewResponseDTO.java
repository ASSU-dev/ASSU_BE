package com.assu.server.domain.review.dto;

import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.entity.ReviewPhoto;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class ReviewResponseDTO {

    @Builder
    public record WriteReviewResponseDTO(
            Long reviewId,
            String content,
            Integer rate,
            LocalDateTime createdAt,
            Long memberId,
            List<String> reviewImageUrls
    ) {
        public static WriteReviewResponseDTO from(Review review) {
            return WriteReviewResponseDTO.builder()
                    .reviewId(review.getId())
                    .content(review.getContent())
                    .rate(review.getRate())
                    .createdAt(review.getCreatedAt())
                    .memberId(review.getStudent().getId())
                    .reviewImageUrls(review.getImageList().stream()
                            .map(ReviewPhoto::getPhotoUrl)
                            .toList())
                    .build();
        }
    }

    @Builder
    public record CheckReviewResponseDTO(
            Long reviewId,
            Long storeId,
            String affiliation,
            String storeName,
            String content,
            Integer rate,
            LocalDateTime createdAt,
            List<String> reviewImageUrls
    ) {
        public static CheckReviewResponseDTO from(Review review) {
            return CheckReviewResponseDTO.builder()
                    .reviewId(review.getId())
                    .storeId(review.getStore().getId())
                    .affiliation(review.getAffiliation())
                    .storeName(review.getStore().getName())
                    .content(review.getContent())
                    .rate(review.getRate())
                    .createdAt(review.getCreatedAt())
                    .reviewImageUrls(review.getImageList().stream()
                            .map(ReviewPhoto::getPhotoUrl)
                            .toList())
                    .build();
        }
    }

    @Builder
    public record CheckPartnerReviewResponseDTO(
            Long reviewId,
            Long storeId,
            Long reviewerId,
            String content,
            Integer rate,
            LocalDateTime createdAt,
            List<String> reviewImageUrls
    ) {
        public static CheckPartnerReviewResponseDTO from(Review review) {
            return CheckPartnerReviewResponseDTO.builder()
                    .reviewId(review.getId())
                    .storeId(review.getStore().getId())
                    .reviewerId(review.getStudent().getId())
                    .content(review.getContent())
                    .rate(review.getRate())
                    .createdAt(review.getCreatedAt())
                    .reviewImageUrls(review.getImageList().stream()
                            .map(ReviewPhoto::getPhotoUrl)
                            .toList())
                    .build();
        }
    }

    @Builder
    public record DeleteReviewResponseDTO(Long reviewId) {}

    @Builder
    public record StandardScoreResponseDTO(Float score) {}
}