package com.assu.server.domain.review.service;

import com.assu.server.domain.review.dto.ReviewRequestDTO;
import com.assu.server.domain.review.dto.ReviewResponseDTO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReviewService {
    ReviewResponseDTO.WriteReviewResponseDTO writeReview(@RequestBody ReviewRequestDTO.WriteReviewRequestDTO request, Long memberId, List<MultipartFile> reviewImages);
    Page<ReviewResponseDTO.CheckReviewResponseDTO> checkStudentReview(Long memberId, Pageable pageable);
    Page<ReviewResponseDTO.CheckReviewResponseDTO> checkPartnerReview(Long memberId, Pageable pageable);
    Page<ReviewResponseDTO.CheckReviewResponseDTO> checkStoreReview(Long storeId, Pageable pageable);

    ReviewResponseDTO.DeleteReviewResponseDTO deleteReview(Long reviewId, Long memberId);

    ReviewResponseDTO.StandardScoreResponseDTO standardScore(@PathVariable Long storeId);
    ReviewResponseDTO.StandardScoreResponseDTO myStoreAverage(@PathVariable Long memberId);
}
