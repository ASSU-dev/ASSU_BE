package com.assu.server.domain.review.service;

import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.review.dto.ReviewRequestDTO;
import com.assu.server.domain.review.dto.ReviewResponseDTO;
import com.assu.server.domain.review.entity.Review;
import com.assu.server.domain.review.entity.ReviewPhoto;
import com.assu.server.domain.review.exception.CustomReviewException;
import com.assu.server.domain.review.repository.ReviewRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.user.entity.PartnershipUsage;
import com.assu.server.domain.user.entity.Student;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.user.repository.PartnershipUsageRepository;
import com.assu.server.domain.user.repository.StudentRepository;
import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.infra.s3.AmazonS3Manager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private final ReviewRepository reviewRepository;
    private final StoreRepository storeRepository;
    private final PartnerRepository partnerRepository;
    private final StudentRepository studentRepository;
    private final AmazonS3Manager amazonS3Manager;
    private final PartnershipUsageRepository partnershipUsageRepository;

    @Override
    @Transactional
    public ReviewResponseDTO.WriteReviewResponseDTO writeReview(ReviewRequestDTO.WriteReviewRequestDTO request, Long memberId, List<MultipartFile> reviewImages) {
        String affiliation = adminNameToAffliation(request.getAdminName());

        Review review = createReview(memberId, request.getStoreId(), request, reviewImages, affiliation);

        PartnershipUsage pu = partnershipUsageRepository.findById(request.getPartnershipUsageId()).orElseThrow(
                () -> new GeneralException(ErrorStatus.NO_SUCH_USAGE)
        );
        pu.setIsReviewed(true);
        partnershipUsageRepository.save(pu);

        recalcAndUpdateStoreRate(review.getStore().getId());

        return ReviewResponseDTO.WriteReviewResponseDTO.from(review);
    }

    private String adminNameToAffliation(String adminName) {
        if(adminName.equals("총학생회")|| adminName.equals("숭실대학교 총학생회"))
            return "숭실대학교 재학생";
        else
            return adminName.replace(" 학생회"," 재학생");
    }

    private Review createReview(Long memberId, Long storeId, ReviewRequestDTO.WriteReviewRequestDTO request, List<MultipartFile> images, String affiliation) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_STORE));
        Partner partner = partnerRepository.findById(request.getPartnerId())
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_PARTNER));
        Student student = studentRepository.findById(memberId)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_STUDENT));

        Review review = request.toEntity(store, partner, student, affiliation);
        reviewRepository.save(review);

        if (images != null && !images.isEmpty()) {
            try {
                for (int i = 0; i < images.size(); i++) {
                    String keyName = generateReviewImageKeyName(memberId, review.getId(), i + 1);
                    amazonS3Manager.uploadFile(keyName, images.get(i));
                    String presignedUrl = amazonS3Manager.generatePresignedUrl(keyName);

                    ReviewPhoto reviewPhoto = ReviewPhoto.builder()
                            .review(review)
                            .photoUrl(presignedUrl)
                            .keyName(keyName)
                            .build();

                    review.getImageList().add(reviewPhoto);
                }
            } catch (Exception e) {
                throw new CustomReviewException(ErrorStatus.IMAGE_UPLOAD_FAILED);
            }
        }

        return reviewRepository.save(review);
    }

    private String generateReviewImageKeyName(Long memberId, Long reviewId, int imageIndex) {
        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());

        return String.format("reviews/images/%s/%s/user%d/review%d_img%d_%s",
                year, month, memberId, reviewId, imageIndex, UUID.randomUUID());
    }

    @Override
    @Transactional
    public Page<ReviewResponseDTO.CheckReviewResponseDTO> checkStudentReview(Long memberId, Pageable pageable) {
        pageable = PageRequest.of(pageable.getPageNumber() - 1, pageable.getPageSize(), pageable.getSort());
        Page<Review> reviews = reviewRepository.findByMemberId(memberId, pageable);

        for (Review review : reviews) {
            updateReviewImageUrls(review);
        }

        return reviews.map(ReviewResponseDTO.CheckReviewResponseDTO::from);
    }

    @Override
    @Transactional
    public Page<ReviewResponseDTO.CheckReviewResponseDTO> checkPartnerReview(Long memberId, Pageable pageable) {
        pageable = PageRequest.of(pageable.getPageNumber() - 1, pageable.getPageSize(), pageable.getSort());
        Partner partner = partnerRepository.findById(memberId)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_PARTNER));
        Store store = storeRepository.findByPartner(partner)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_STORE));

        Page<Review> reviews = reviewRepository.findByStoreIdAndStatusAndStudentStatus(
                store.getId(), ReportedStatus.NORMAL, ReportedStatus.NORMAL, pageable);

        for (Review review : reviews) {
            updateReviewImageUrls(review);
        }

        return reviews.map(ReviewResponseDTO.CheckReviewResponseDTO::from);
    }

    @Override
    @Transactional
    public ReviewResponseDTO.DeleteReviewResponseDTO deleteReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus._BAD_REQUEST));

        Long storeId = review.getStore().getId();
        reviewRepository.deleteById(reviewId);

        recalcAndUpdateStoreRate(storeId);

        return ReviewResponseDTO.DeleteReviewResponseDTO.builder()
                .reviewId(reviewId)
                .build();
    }

    private void updateReviewImageUrls(Review review) {
        for (ReviewPhoto reviewPhoto : review.getImageList()) {
            if (reviewPhoto.getKeyName() != null) {
                String freshUrl = amazonS3Manager.generatePresignedUrl(reviewPhoto.getKeyName());
                reviewPhoto.updatePhotoUrl(freshUrl);
            }
        }
    }

    @Override
    @Transactional
    public Page<ReviewResponseDTO.CheckReviewResponseDTO> checkStoreReview(Long storeId, Pageable pageable) {
        pageable = PageRequest.of(pageable.getPageNumber() - 1, pageable.getPageSize(), pageable.getSort());
        Store store = storeRepository.findById(storeId).orElseThrow(
                () -> new CustomReviewException(ErrorStatus.NO_SUCH_STORE));

        Page<Review> reviews = reviewRepository.findByStoreIdAndStatusAndStudentStatus(
                store.getId(), ReportedStatus.NORMAL, ReportedStatus.NORMAL, pageable);

        for (Review review : reviews) {
            updateReviewImageUrls(review);
        }

        return reviews.map(ReviewResponseDTO.CheckReviewResponseDTO::from);
    }

    @Override
    @Transactional
    public ReviewResponseDTO.StandardScoreResponseDTO standardScore(Long storeId) {
        Float score = reviewRepository.standardScoreWithStatus(storeId, ReportedStatus.NORMAL, ReportedStatus.NORMAL);
        return new ReviewResponseDTO.StandardScoreResponseDTO(score == null ? 0f : score);
    }

    @Override
    @Transactional
    public ReviewResponseDTO.StandardScoreResponseDTO myStoreAverage(Long memberId) {
        Partner partner = partnerRepository.findById(memberId)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_PARTNER));
        Store store = storeRepository.findByPartner(partner)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_STORE));

        Float score = reviewRepository.standardScoreWithStatus(store.getId(), ReportedStatus.NORMAL, ReportedStatus.NORMAL);
        return new ReviewResponseDTO.StandardScoreResponseDTO(score == null ? 0f : score);
    }

    private void recalcAndUpdateStoreRate(Long storeId) {
        reviewRepository.flush();
        Float avg = reviewRepository.standardScoreWithStatus(storeId, ReportedStatus.NORMAL, ReportedStatus.NORMAL);
        int rounded = (avg == null) ? 0 : (int) (Math.round(avg * 10f) / 10f);

        storeRepository.findById(storeId).ifPresent(s -> {
            s.setRate(rounded);
            storeRepository.save(s);
        });
    }
}