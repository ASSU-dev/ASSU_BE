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
import com.assu.server.domain.student.entity.PartnershipUsage;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.domain.common.entity.enums.ReportedStatus;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.infra.s3.AmazonS3Manager;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private final ReviewRepository reviewRepository;
    private final StoreRepository storeRepository;
    private final PartnerRepository partnerRepository;
    private final StudentRepository studentRepository;
    private final AmazonS3Manager amazonS3Manager;
    private final PartnershipUsageRepository partnershipUsageRepository;
    private final TransactionTemplate transactionTemplate;

    // 이미지 업로드 동안 DB 커넥션을 잡지 않도록 업로드를 먼저 하고, 저장이 실패하면 올린 파일을 지운다
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReviewResponseDTO.WriteReviewResponseDTO writeReview(ReviewRequestDTO.WriteReviewRequestDTO request, Long memberId, List<MultipartFile> reviewImages) {
        List<String> uploadedKeys = uploadReviewImages(memberId, reviewImages);
        try {
            return transactionTemplate.execute(status -> saveReview(request, memberId, uploadedKeys));
        } catch (RuntimeException e) {
            uploadedKeys.forEach(amazonS3Manager::deleteFileQuietly);
            throw e;
        }
    }

    private ReviewResponseDTO.WriteReviewResponseDTO saveReview(ReviewRequestDTO.WriteReviewRequestDTO request, Long memberId, List<String> imageKeys) {
        Store store = findStoreOfPartner(request.getStoreId(), request.getPartnerId());
        claimUsageForReview(request.getPartnershipUsageId(), memberId, store.getId());

        String affiliation = adminNameToAffliation(request.getAdminName());

        Review review = createReview(memberId, store, request, imageKeys, affiliation);

        recalcAndUpdateStoreRate(review.getStore().getId());

        return ReviewResponseDTO.WriteReviewResponseDTO.from(review);
    }

    private List<String> uploadReviewImages(Long memberId, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (int i = 0; i < images.size(); i++) {
                String keyName = generateReviewImageKeyName(memberId, i + 1);
                amazonS3Manager.uploadFile(keyName, images.get(i));
                uploadedKeys.add(keyName);
            }
        } catch (Exception e) {
            uploadedKeys.forEach(amazonS3Manager::deleteFileQuietly);
            throw new CustomReviewException(ErrorStatus.IMAGE_UPLOAD_FAILED);
        }
        return uploadedKeys;
    }

    // 다른 가게의 평점을 바꾸지 못하도록 요청한 가게와 업체가 실제로 연결돼 있는지 먼저 확인한다
    private Store findStoreOfPartner(Long storeId, Long partnerId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_STORE));
        if (store.getPartner() == null || !store.getPartner().getId().equals(partnerId)) {
            throw new CustomReviewException(ErrorStatus.NO_SUCH_STORE_WITH_THAT_PARTNER);
        }
        return store;
    }

    private void claimUsageForReview(Long partnershipUsageId, Long memberId, Long storeId) {
        if (partnershipUsageId == null) {
            throw new GeneralException(ErrorStatus.NO_SUCH_USAGE);
        }
        if (partnershipUsageRepository.markReviewedIfUnreviewed(partnershipUsageId, memberId, storeId) == 1) {
            return;
        }

        PartnershipUsage usage = partnershipUsageRepository.findById(partnershipUsageId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_USAGE));
        if (usage.getStudent() == null || !usage.getStudent().getId().equals(memberId)) {
            throw new GeneralException(ErrorStatus.FORBIDDEN_USAGE);
        }
        if (!partnershipUsageRepository.existsByIdAndStoreId(partnershipUsageId, storeId)) {
            throw new GeneralException(ErrorStatus.USAGE_STORE_MISMATCH);
        }
        throw new GeneralException(ErrorStatus.ALREADY_REVIEWED_USAGE);
    }

    private String adminNameToAffliation(String adminName) {
        if(adminName.equals("총학생회")|| adminName.equals("숭실대학교 총학생회"))
            return "숭실대학교 재학생";
        else
            return adminName.replace(" 학생회"," 재학생");
    }

    private Review createReview(Long memberId, Store store, ReviewRequestDTO.WriteReviewRequestDTO request, List<String> imageKeys, String affiliation) {
        Partner partner = partnerRepository.findByIdAndMember_DeletedAtIsNull(request.getPartnerId())
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_PARTNER));
        Student student = studentRepository.findById(memberId)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_STUDENT));

        Review review = request.toEntity(store, partner, student, affiliation);
        for (String keyName : imageKeys) {
            review.getImageList().add(ReviewPhoto.builder()
                    .review(review)
                    .photoUrl(amazonS3Manager.generatePresignedUrl(keyName))
                    .keyName(keyName)
                    .build());
        }

        return reviewRepository.save(review);
    }

    private String generateReviewImageKeyName(Long memberId, int imageIndex) {
        LocalDateTime now = LocalDateTime.now();
        String year = String.valueOf(now.getYear());
        String month = String.format("%02d", now.getMonthValue());

        return String.format("reviews/images/%s/%s/user%d/img%d_%s",
                year, month, memberId, imageIndex, UUID.randomUUID());
    }

    @Override
    public Page<ReviewResponseDTO.CheckReviewResponseDTO> checkStudentReview(Long memberId, Pageable pageable) {
        pageable = toZeroIndexed(pageable);
        Page<Review> reviews = reviewRepository.findByMemberId(memberId, pageable);

        return reviews.map(review -> ReviewResponseDTO.CheckReviewResponseDTO.from(review, resolveImageUrls(review)));
    }

    @Override
    public Page<ReviewResponseDTO.CheckReviewResponseDTO> checkPartnerReview(Long memberId, Pageable pageable) {
        pageable = toZeroIndexed(pageable);
        Partner partner = partnerRepository.findById(memberId)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_PARTNER));
        Store store = storeRepository.findByPartner(partner)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_STORE));

        Page<Review> reviews = reviewRepository.findByStoreIdAndStatusAndStudentStatus(
                store.getId(), ReportedStatus.NORMAL, ReportedStatus.NORMAL, pageable);

        return reviews.map(review -> ReviewResponseDTO.CheckReviewResponseDTO.from(review, resolveImageUrls(review)));
    }

    @Override
    @Transactional
    public ReviewResponseDTO.DeleteReviewResponseDTO deleteReview(Long reviewId, Long memberId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new CustomReviewException(ErrorStatus.NO_SUCH_REVIEW));
        if (review.getStudent() == null || !review.getStudent().getId().equals(memberId)) {
            throw new CustomReviewException(ErrorStatus.FORBIDDEN_REVIEW);
        }

        Long storeId = review.getStore().getId();
        reviewRepository.deleteById(reviewId);

        recalcAndUpdateStoreRate(storeId);

        return ReviewResponseDTO.DeleteReviewResponseDTO.builder()
                .reviewId(reviewId)
                .build();
    }

    private List<String> resolveImageUrls(Review review) {
        return review.getImageList().stream()
                .map(photo -> photo.getKeyName() != null
                        ? amazonS3Manager.generatePresignedUrl(photo.getKeyName())
                        : photo.getPhotoUrl())
                .toList();
    }

    @Override
    public Page<ReviewResponseDTO.CheckReviewResponseDTO> checkStoreReview(Long storeId, Pageable pageable) {
        pageable = toZeroIndexed(pageable);
        Store store = storeRepository.findById(storeId).orElseThrow(
                () -> new CustomReviewException(ErrorStatus.NO_SUCH_STORE));

        Page<Review> reviews = reviewRepository.findByStoreIdAndStatusAndStudentStatus(
                store.getId(), ReportedStatus.NORMAL, ReportedStatus.NORMAL, pageable);

        return reviews.map(review -> ReviewResponseDTO.CheckReviewResponseDTO.from(review, resolveImageUrls(review)));
    }

    @Override
    public ReviewResponseDTO.StandardScoreResponseDTO standardScore(Long storeId) {
        Float score = reviewRepository.standardScoreWithStatus(storeId, ReportedStatus.NORMAL, ReportedStatus.NORMAL);
        return new ReviewResponseDTO.StandardScoreResponseDTO(score == null ? 0f : score);
    }

    @Override
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

    /**
     * 이 API들은 프론트와 1-based 페이지 번호로 계약되어 있다.
     * page 를 생략하면 Pageable 기본값 0 이 들어오므로 음수가 되지 않도록 보정한다.
     */
    private Pageable toZeroIndexed(Pageable pageable) {
        return PageRequest.of(
                Math.max(pageable.getPageNumber() - 1, 0),
                pageable.getPageSize(),
                pageable.getSort()
        );
    }
}
