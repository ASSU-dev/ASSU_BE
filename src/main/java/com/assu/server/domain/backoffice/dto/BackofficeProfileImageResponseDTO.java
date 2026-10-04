package com.assu.server.domain.backoffice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "백오피스 프로필 이미지 업로드 응답")
public record BackofficeProfileImageResponseDTO(
        @Schema(description = "프로필 이미지 S3 presigned URL (약 10분 유효)", example = "https://assu-bucket.s3.ap-northeast-2.amazonaws.com/members/1/profile/image.png?X-Amz-Signature=...") String url
) {
    public static BackofficeProfileImageResponseDTO of(String url) {
        return new BackofficeProfileImageResponseDTO(url);
    }
}
