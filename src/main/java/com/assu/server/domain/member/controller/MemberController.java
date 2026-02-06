package com.assu.server.domain.member.controller;

import com.assu.server.domain.member.dto.ProfileImageResponseDTO;
import com.assu.server.domain.member.service.ProfileImageService;
import com.assu.server.global.apiPayload.BaseResponse;
import com.assu.server.global.apiPayload.code.status.SuccessStatus;
import com.assu.server.global.util.PrincipalDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@Tag(name = "Member", description = "멤버 API")
@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final ProfileImageService profileImageService;

    @Operation(
            summary = "프로필 사진 업로드/교체 API",
            description = "# [v1.0 (2025-09-15)](https://clumsy-seeder-416.notion.site/26f1197c19ed8031bc50e3571e8ea18f?source=copy_link)\n" +
                    "- `multipart/form-data`로 프로필 이미지를 업로드합니다.\n" +
                    "- 기존 이미지가 있으면 S3에서 삭제 후 새 이미지로 교체합니다.\n" +
                    "- 성공 시 업로드된 이미지 key를 반환합니다."
    )
    @PutMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<ProfileImageResponseDTO> uploadOrReplaceProfileImage(
            @AuthenticationPrincipal PrincipalDetails pd,
            @RequestPart("image")
            @Parameter(
                    description = "프로필 이미지 파일 (jpg/png/webp 등)",
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            schema = @Schema(type = "string", format = "binary")
                    )
            )
            MultipartFile image
    ) {
        String key = profileImageService.updateProfileImage(pd.getMemberId(), image);
        return BaseResponse.onSuccess(SuccessStatus._OK, new ProfileImageResponseDTO(key));
    }

    @Operation(
            summary = "프로필 이미지 조회 API",
            description = "# [v1.0 (2025-09-18)](https://clumsy-seeder-416.notion.site/2711197c19ed8039bbe2c48380c9f4c8?source=copy_link)\n" +
                    "- 로그인한 사용자의 프로필 이미지 presigned URL을 반환합니다.\n" +
                    "- URL은 일정 시간 동안만 유효합니다."
    )
    @GetMapping("/me/profile-image")
    public BaseResponse<ProfileImageResponseDTO> getProfileImage(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        String url = profileImageService.getProfileImageUrl(pd.getMemberId());
        return BaseResponse.onSuccess(SuccessStatus._OK, new ProfileImageResponseDTO(url));
    }

    @Operation(
            summary = "프로필 이미지 삭제 API",
            description = "# [v1.0 (2025-09-18)](https://clumsy-seeder-416.notion.site/2dc1197c19ed809cb813f74a1b4c5c26?source=copy_link)\n" +
                    "- 로그인한 사용자의 프로필 이미지를 삭제합니다.\n" +
                    "- 프론트의 기본 이미지를 표시합니다."
    )
    @DeleteMapping("/me/profile-image")
    public BaseResponse<String> deleteProfileImage(
            @AuthenticationPrincipal PrincipalDetails pd
    ) {
        profileImageService.deleteProfileImage(pd.getId());
        return BaseResponse.onSuccess(SuccessStatus._OK, "프로필 이미지가 삭제되었습니다.");
    }
}