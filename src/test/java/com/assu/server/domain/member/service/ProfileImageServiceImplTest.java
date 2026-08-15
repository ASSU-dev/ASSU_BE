package com.assu.server.domain.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.s3.AmazonS3Manager;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceImplTest {

	@InjectMocks
	private ProfileImageServiceImpl profileImageService;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private AmazonS3Manager amazonS3Manager;

	private static final Long MEMBER_ID = 1L;

	private MockMultipartFile validImage() {
		return new MockMultipartFile("image", "profile.png", "image/png", new byte[]{1, 2, 3});
	}

	// ===== updateProfileImage =====

	@Test
	@DisplayName("빈 파일을 업로드하면 PROFILE_IMAGE_NOT_FOUND 예외가 발생한다")
	void updateProfileImage_EmptyFile_ThrowsException() {
		// 1. Given
		MockMultipartFile emptyFile = new MockMultipartFile("image", "profile.png", "image/png", new byte[0]);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> profileImageService.updateProfileImage(MEMBER_ID, emptyFile));

		// 3. Then
		assertEquals(ErrorStatus.PROFILE_IMAGE_NOT_FOUND, exception.getCode());
	}

	@Test
	@DisplayName("5MB를 초과하는 파일을 업로드하면 FILE_SIZE_EXCEEDED 예외가 발생한다")
	void updateProfileImage_FileTooLarge_ThrowsException() {
		// 1. Given
		MockMultipartFile largeFile = new MockMultipartFile(
			"image", "profile.png", "image/png", new byte[5 * 1024 * 1024 + 1]);

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> profileImageService.updateProfileImage(MEMBER_ID, largeFile));

		// 3. Then
		assertEquals(ErrorStatus.FILE_SIZE_EXCEEDED, exception.getCode());
	}

	@Test
	@DisplayName("경로 조작 문자가 포함된 파일명이면 INVALID_FILE_NAME 예외가 발생한다")
	void updateProfileImage_PathTraversalFilename_ThrowsException() {
		// 1. Given
		MockMultipartFile badFile = new MockMultipartFile(
			"image", "../../etc/passwd.png", "image/png", new byte[]{1});

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> profileImageService.updateProfileImage(MEMBER_ID, badFile));

		// 3. Then
		assertEquals(ErrorStatus.INVALID_FILE_NAME, exception.getCode());
	}

	@Test
	@DisplayName("허용되지 않은 확장자면 INVALID_FILE_TYPE 예외가 발생한다")
	void updateProfileImage_InvalidExtension_ThrowsException() {
		// 1. Given
		MockMultipartFile exeFile = new MockMultipartFile(
			"image", "malware.exe", "image/png", new byte[]{1});

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> profileImageService.updateProfileImage(MEMBER_ID, exeFile));

		// 3. Then
		assertEquals(ErrorStatus.INVALID_FILE_TYPE, exception.getCode());
	}

	@Test
	@DisplayName("허용되지 않은 Content-Type이면 INVALID_CONTENT_TYPE 예외가 발생한다")
	void updateProfileImage_InvalidContentType_ThrowsException() {
		// 1. Given (확장자는 png인데 Content-Type이 pdf)
		MockMultipartFile fakeImage = new MockMultipartFile(
			"image", "profile.png", "application/pdf", new byte[]{1});

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> profileImageService.updateProfileImage(MEMBER_ID, fakeImage));

		// 3. Then
		assertEquals(ErrorStatus.INVALID_CONTENT_TYPE, exception.getCode());
	}

	@Test
	@DisplayName("존재하지 않는 회원이 프로필 이미지를 변경하면 NO_SUCH_MEMBER 예외가 발생한다")
	void updateProfileImage_MemberNotFound_ThrowsException() {
		// 1. Given
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> profileImageService.updateProfileImage(MEMBER_ID, validImage()));

		// 3. Then
		assertEquals(ErrorStatus.NO_SUCH_MEMBER, exception.getCode());
		verify(amazonS3Manager, never()).uploadFile(anyString(), any());
	}

	@Test
	@DisplayName("프로필 이미지 변경 성공 시 새 이미지를 업로드하고 기존 이미지를 삭제한다")
	void updateProfileImage_Success_UploadsNewAndDeletesOld() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getProfileUrl()).thenReturn("members/1/profile/old.png");
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		when(amazonS3Manager.generateKeyName(anyString())).thenReturn("members/1/profile/new-key.png");
		when(amazonS3Manager.uploadFile(anyString(), any())).thenReturn("members/1/profile/new-key.png");

		// 2. When
		String result = profileImageService.updateProfileImage(MEMBER_ID, validImage());

		// 3. Then
		assertEquals("members/1/profile/new-key.png", result);
		verify(amazonS3Manager, times(1)).deleteFile("members/1/profile/old.png");
		verify(member, times(1)).setProfileUrl("members/1/profile/new-key.png");
	}

	// ===== getProfileImageUrl =====

	@Test
	@DisplayName("프로필 이미지가 없는 회원의 이미지 URL을 조회하면 PROFILE_IMAGE_NOT_FOUND 예외가 발생한다")
	void getProfileImageUrl_NoImage_ThrowsException() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getProfileUrl()).thenReturn(null);
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> profileImageService.getProfileImageUrl(MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.PROFILE_IMAGE_NOT_FOUND, exception.getCode());
	}

	@Test
	@DisplayName("프로필 이미지 URL 조회 성공 시 presigned URL을 반환한다")
	void getProfileImageUrl_Success_ReturnsPresignedUrl() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getProfileUrl()).thenReturn("members/1/profile/key.png");
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
		when(amazonS3Manager.generatePresignedUrl("members/1/profile/key.png"))
			.thenReturn("https://assu-bucket.s3.ap-northeast-2.amazonaws.com/members/1/profile/key.png?presigned=1");

		// 2. When
		String url = profileImageService.getProfileImageUrl(MEMBER_ID);

		// 3. Then
		assertEquals("https://assu-bucket.s3.ap-northeast-2.amazonaws.com/members/1/profile/key.png?presigned=1", url);
	}

	// ===== deleteProfileImage =====

	@Test
	@DisplayName("S3 파일 삭제가 실패하면 PROFILE_IMAGE_DELETE_FAILED 예외가 발생하고 URL은 유지된다")
	void deleteProfileImage_S3Failure_ThrowsException() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getProfileUrl()).thenReturn("members/1/profile/key.png");
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
		doThrow(new RuntimeException("S3 오류")).when(amazonS3Manager).deleteFile("members/1/profile/key.png");

		// 2. When
		CustomAuthException exception = assertThrows(CustomAuthException.class,
			() -> profileImageService.deleteProfileImage(MEMBER_ID));

		// 3. Then
		assertEquals(ErrorStatus.PROFILE_IMAGE_DELETE_FAILED, exception.getCode());
		verify(member, never()).setProfileUrl(any());
	}

	@Test
	@DisplayName("프로필 이미지 삭제 성공 시 S3 파일을 삭제하고 프로필 URL을 초기화한다")
	void deleteProfileImage_Success_DeletesFileAndClearsUrl() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getProfileUrl()).thenReturn("members/1/profile/key.png");
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		// 2. When
		profileImageService.deleteProfileImage(MEMBER_ID);

		// 3. Then
		verify(amazonS3Manager, times(1)).deleteFile("members/1/profile/key.png");
		verify(member, times(1)).setProfileUrl(null);
	}
}
