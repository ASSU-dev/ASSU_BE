package com.assu.server.domain.member.service;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.s3.AmazonS3Manager;
import com.assu.server.infra.s3.ImageCdnManager;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileImageServiceImpl implements ProfileImageService{
    private final MemberRepository memberRepository;
    private final AmazonS3Manager amazonS3Manager;
    private final ImageCdnManager imageCdnManager;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif");

    // 업로드 동안 DB 커넥션을 잡지 않도록 업로드는 트랜잭션 밖에서 하고, 반영에 실패하면 올린 파일을 지운다
    @Override
    public String updateProfileImage(Long memberId, MultipartFile image) {
        validateFile(image);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));

        String keyName = amazonS3Manager.generateKeyName(
                resolveProfileKeyPath(member), getFileExtension(image.getOriginalFilename()));
        String uploadedKey = amazonS3Manager.uploadFile(keyName, image);

        String oldKey;
        try {
            oldKey = transactionTemplate.execute(status -> replaceProfileKey(memberId, uploadedKey));
        } catch (RuntimeException e) {
            amazonS3Manager.deleteFileQuietly(uploadedKey);
            throw e;
        }

        deletePreviousProfileImage(oldKey);
        return uploadedKey;
    }

    private String replaceProfileKey(Long memberId, String newKey) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
        String previousKey = member.getProfileUrl();
        member.changeProfileUrl(newKey);
        return previousKey;
    }

    private void deletePreviousProfileImage(String oldKey) {
        if (oldKey == null || oldKey.isBlank()) {
            return;
        }
        try {
            amazonS3Manager.deleteFile(oldKey);
            imageCdnManager.invalidate(oldKey);
        } catch (Exception e) {
            log.warn("이전 프로필 삭제 실패 key={}", oldKey, e);
            meterRegistry.counter("member.profile.image.s3.delete.failure").increment();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getProfileImageUrl(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));

        String key = member.getProfileUrl();

        if (key == null || key.isBlank()) {
            throw new CustomAuthException(ErrorStatus.PROFILE_IMAGE_NOT_FOUND);
        }

        return amazonS3Manager.generatePresignedUrl(key);
    }

    @Override
    @Transactional
    public void deleteProfileImage(Long memberId) {
        // 1) 멤버 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));

        // 2) 현재 프로필 이미지 확인
        String currentKey = member.getProfileUrl();
        if (currentKey == null || currentKey.isBlank()) {
            throw new CustomAuthException(ErrorStatus.PROFILE_IMAGE_NOT_FOUND);
        }

        // 3) S3에서 파일 삭제
        try {
            amazonS3Manager.deleteFile(currentKey);
        } catch (Exception e) {
            log.error("프로필 이미지 삭제 실패 memberId={}, key={}", memberId, currentKey, e);
            throw new CustomAuthException(ErrorStatus.PROFILE_IMAGE_DELETE_FAILED);
        }

        imageCdnManager.invalidate(currentKey);

        // 4) DB에서 프로필 URL 제거 (null로 설정)
        member.clearProfileUrl();
    }

    // 제휴업체 사진만 CDN 공개 경로에 저장한다
    private String resolveProfileKeyPath(Member member) {
        if (member.getRole() == UserRole.PARTNER) {
            return ImageCdnManager.PUBLIC_PREFIX + "store-profiles";
        }
        return "members/" + member.getId() + "/profile";
    }


    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomAuthException(ErrorStatus.PROFILE_IMAGE_NOT_FOUND);
        }

        // 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomAuthException(ErrorStatus.FILE_SIZE_EXCEEDED);
        }

        // 파일명 검증
        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..")) {
            throw new CustomAuthException(ErrorStatus.INVALID_FILE_NAME);
        }

        // 확장자 검증
        String extension = getFileExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new CustomAuthException(ErrorStatus.INVALID_FILE_TYPE);
        }

        // Content-Type 검증
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new CustomAuthException(ErrorStatus.INVALID_CONTENT_TYPE);
        }
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf(".");
        if (lastDot == -1) return "";
        return filename.substring(lastDot + 1).toLowerCase();
    }

}

