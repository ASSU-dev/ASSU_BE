package com.assu.server.domain.review.controller;

import com.assu.server.domain.review.dto.ReviewRequestDTO;
import com.assu.server.domain.review.dto.ReviewResponseDTO;
import com.assu.server.domain.review.service.ReviewService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/reviews")
@Tag(name = "리뷰 관련 API", description = "리뷰 작성, 조회, 삭제 및 통계 관련 API")
public class ReviewController {
    private final ReviewService reviewService;

    @Operation(
            summary = "리뷰 작성 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2241197c19ed8176ba4fcb49c0136f93)\n" +
                    "- 리뷰 내용, 별점, 이미지를 멀티파트로 입력받아 저장합니다.\n" +
                    "- Authentication: JWT 토큰 필요 (Student 권한)"
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<ReviewResponseDTO.WriteReviewResponseDTO> writeReview(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestPart("request") ReviewRequestDTO.WriteReviewRequestDTO request,
            @RequestPart(value = "reviewImages", required = false) List<MultipartFile> reviewImages
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, reviewService.writeReview(request, pd.getId(), reviewImages));
    }

    @Operation(
            summary = "내가 쓴 리뷰 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/22b1197c19ed8057b158c88f6153d073)\n" +
                    "- 로그인한 학생 사용자가 작성한 리뷰 목록을 페이징하여 조회합니다."
    )
    @GetMapping("/student")
    public BaseResponse<Page<ReviewResponseDTO.CheckReviewResponseDTO>> checkStudent(
            @AuthenticationPrincipal PrincipalDetails pd, Pageable pageable
    ) {
        return BaseResponse.onSuccess(SuccessStatus._OK, reviewService.checkStudentReview(pd.getId(), pageable));
    }

    @Operation(
            summary = "내 가게 리뷰 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/_-2241197c19ed8130b89ad5a77f3e8b2c)\n" +
                    "- 로그인한 파트너 계정의 가게에 달린 리뷰 목록을 페이징하여 조회합니다."
    )
    @GetMapping("/partner")
    public BaseResponse<Page<ReviewResponseDTO.CheckReviewResponseDTO>> checkPartnerReview(
            @AuthenticationPrincipal PrincipalDetails pd, Pageable pageable
    ){
        return BaseResponse.onSuccess(SuccessStatus._OK, reviewService.checkPartnerReview(pd.getId(), pageable));
    }

    @Operation(
            summary = "가게 리뷰 조회 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2681197c19ed80038db3f7dd357623ff)\n" +
                    "- 특정 storeId를 기반으로 해당 가게의 모든 리뷰를 조회합니다."
    )
    @GetMapping("/store/{storeId}")
    public BaseResponse<Page<ReviewResponseDTO.CheckReviewResponseDTO>> checkStoreReview(
            Pageable pageable, @PathVariable Long storeId
    ){
        return BaseResponse.onSuccess(SuccessStatus._OK, reviewService.checkStoreReview(storeId, pageable));
    }

    @Operation(
            summary = "내가 쓴 리뷰 삭제 API",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2241197c19ed81a58e93c9ba56f6cb9a)\n" +
                    "- 본인이 작성한 리뷰를 ID를 기반으로 삭제합니다."
    )
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<BaseResponse<ReviewResponseDTO.DeleteReviewResponseDTO>> deleteReview(
            @PathVariable Long reviewId) {
        return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus._OK, reviewService.deleteReview(reviewId)));
    }

    @Operation(
            summary = "가게 리뷰 평균 조회 API (ID 기반)",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/2ef1197c19ed80a5a08fd4d2aa031e5f)\n" +
                    "- 특정 storeId 기반으로 해당 가게의 리뷰 평점을 조회합니다."
    )
    @GetMapping("/average/{storeId}")
    public ResponseEntity<BaseResponse<ReviewResponseDTO.StandardScoreResponseDTO>> getStandardScore(
            @PathVariable Long storeId
    ){
        return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus._OK, reviewService.standardScore(storeId)));
    }

    @Operation(
            summary = "내 가게 리뷰 평균 조회 API (파트너)",
            description = "# [v1.0 (2025-09-02)](https://www.notion.so/API-2681197c19ed80df9f2ac100812c7f44)\n" +
                    "- 파트너 로그인 시 본인 가게의 평균 평점을 조회합니다."
    )
    @GetMapping("/average")
    public ResponseEntity<BaseResponse<ReviewResponseDTO.StandardScoreResponseDTO>> getMyStoreAverage(
            @AuthenticationPrincipal PrincipalDetails pd
    ){
        return ResponseEntity.ok(BaseResponse.onSuccess(SuccessStatus._OK, reviewService.myStoreAverage(pd.getId())));
    }
}