package com.assu.server.domain.member.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.assu.server.global.exception.GeneralException;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.infra.s3.AmazonS3Manager;
import com.assu.server.infra.s3.ImageCdnManager;

import io.micrometer.core.instrument.MeterRegistry;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceImplTest {

	@InjectMocks
	private ProfileImageServiceImpl profileImageService;

	@Mock
	private MemberRepository memberRepository;

	@Mock
	private AmazonS3Manager amazonS3Manager;

	@Mock
	private ImageCdnManager imageCdnManager;

	@Mock
	private MeterRegistry meterRegistry;

	@Mock
	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		lenient().when(transactionTemplate.execute(any()))
			.thenAnswer(inv -> inv.<TransactionCallback<Object>>getArgument(0).doInTransaction(null));
	}

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
		GeneralException exception = assertThrows(GeneralException.class,
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
		GeneralException exception = assertThrows(GeneralException.class,
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
		GeneralException exception = assertThrows(GeneralException.class,
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
		GeneralException exception = assertThrows(GeneralException.class,
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
		GeneralException exception = assertThrows(GeneralException.class,
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
		GeneralException exception = assertThrows(GeneralException.class,
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
		when(member.getRole()).thenReturn(UserRole.STUDENT);
		when(member.getProfileUrl()).thenReturn("members/1/profile/old.png");
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		when(amazonS3Manager.generateKeyName("members/1/profile", "png")).thenReturn("members/1/profile/new-key.png");
		when(amazonS3Manager.uploadFile(anyString(), any())).thenReturn("members/1/profile/new-key.png");

		// 2. When
		String result = profileImageService.updateProfileImage(MEMBER_ID, validImage());

		// 3. Then
		assertEquals("members/1/profile/new-key.png", result);
		verify(amazonS3Manager, times(1)).deleteFile("members/1/profile/old.png");
		verify(imageCdnManager, times(1)).invalidate("members/1/profile/old.png");
		verify(member, times(1)).setProfileUrl("members/1/profile/new-key.png");
	}

	@Test
	@DisplayName("제휴업체가 프로필 이미지를 올리면 원본 파일명 없이 CDN 공개 경로에 저장한다")
	void updateProfileImage_Partner_UploadsToPublicStoreProfilePath() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getRole()).thenReturn(UserRole.PARTNER);
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));

		when(amazonS3Manager.generateKeyName("public/store-profiles", "png")).thenReturn("public/store-profiles/new-key.png");
		when(amazonS3Manager.uploadFile(anyString(), any())).thenReturn("public/store-profiles/new-key.png");

		// 2. When
		String result = profileImageService.updateProfileImage(MEMBER_ID, validImage());

		// 3. Then
		assertEquals("public/store-profiles/new-key.png", result);
		verify(amazonS3Manager, times(1)).uploadFile(eq("public/store-profiles/new-key.png"), any());
		verify(member, times(1)).setProfileUrl("public/store-profiles/new-key.png");
	}

	@Test
	@DisplayName("업로드 후 DB 반영이 실패하면 새로 올린 파일을 지우고 기존 파일은 남긴다")
	void updateProfileImage_WhenDbUpdateFails_DeletesUploadedFileAndKeepsOld() {
		// 1. Given
		Member member = mock(Member.class);
		when(member.getId()).thenReturn(MEMBER_ID);
		when(member.getRole()).thenReturn(UserRole.STUDENT);
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
		when(amazonS3Manager.generateKeyName("members/1/profile", "png")).thenReturn("members/1/profile/new-key.png");
		when(amazonS3Manager.uploadFile(anyString(), any())).thenReturn("members/1/profile/new-key.png");
		doThrow(new IllegalStateException("db down")).when(transactionTemplate).execute(any());

		// 2. When
		assertThrows(IllegalStateException.class,
			() -> profileImageService.updateProfileImage(MEMBER_ID, validImage()));

		// 3. Then
		verify(amazonS3Manager).deleteFileQuietly("members/1/profile/new-key.png");
		verify(amazonS3Manager, never()).deleteFile(anyString());
		verify(imageCdnManager, never()).invalidate(anyString());
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
		GeneralException exception = assertThrows(GeneralException.class,
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
		GeneralException exception = assertThrows(GeneralException.class,
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
		verify(imageCdnManager, times(1)).invalidate("members/1/profile/key.png");
		verify(member, times(1)).setProfileUrl(null);
	}
}
