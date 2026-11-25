package com.assu.server.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;

class ImageCdnManagerTest {

	private static final String BASE_URL = "https://img.assu.site";
	private static final String SIGNATURE_KEY = "test-signature-key";
	private static final String DISTRIBUTION_ID = "E2YBXHPKZ2QEIS";
	private static final String PUBLIC_KEY = "public/store-profiles/abc.png";

	private final AmazonS3Manager amazonS3Manager = mock(AmazonS3Manager.class);
	private final CloudFrontClient cloudFrontClient = mock(CloudFrontClient.class);

	private ImageCdnManager enabledManager() {
		return new ImageCdnManager(amazonS3Manager, cloudFrontClient, BASE_URL, SIGNATURE_KEY, DISTRIBUTION_ID);
	}

	private ImageCdnManager disabledManager() {
		return new ImageCdnManager(amazonS3Manager, cloudFrontClient, "", "", "");
	}

	@Test
	@DisplayName("공개 경로 이미지는 크기별 변환 경로와 HMAC-SHA256 서명이 붙은 CDN URL을 반환한다")
	void generateUrl_whenPublicKey_thenReturnsSignedCdnUrl() {
		// when
		String thumbnail = enabledManager().generateUrl(PUBLIC_KEY, ImageSize.THUMBNAIL);
		String large = enabledManager().generateUrl(PUBLIC_KEY, ImageSize.LARGE);

		// then
		assertThat(thumbnail).isEqualTo(BASE_URL
			+ "/fit-in/256x256/filters:format(webp):quality(80)/public/store-profiles/abc.png"
			+ "?signature=fdb1014b285a0cd71fec62b5a0a3e38bf89dafa12936e4a75807187b04634e3a");
		assertThat(large).isEqualTo(BASE_URL
			+ "/fit-in/1200x1200/filters:format(webp):quality(80)/public/store-profiles/abc.png"
			+ "?signature=61775dc58800fc80a5c6a7ee11e18dce88cc16eac4a7d35e51aa76f4113f3d60");
		verify(amazonS3Manager, never()).generatePresignedUrl(any());
	}

	@Test
	@DisplayName("공개 경로가 아닌 이미지는 presigned URL을 반환한다")
	void generateUrl_whenPrivateKey_thenReturnsPresignedUrl() {
		// given
		String key = "members/1/profile/abc.png";
		when(amazonS3Manager.generatePresignedUrl(key)).thenReturn("https://presigned.url");

		// when
		String url = enabledManager().generateUrl(key, ImageSize.THUMBNAIL);

		// then
		assertThat(url).isEqualTo("https://presigned.url");
	}

	@Test
	@DisplayName("CDN 설정이 없으면 공개 경로 이미지도 presigned URL을 반환한다")
	void generateUrl_whenCdnNotConfigured_thenReturnsPresignedUrl() {
		// given
		when(amazonS3Manager.generatePresignedUrl(PUBLIC_KEY)).thenReturn("https://presigned.url");

		// when
		String url = disabledManager().generateUrl(PUBLIC_KEY, ImageSize.THUMBNAIL);

		// then
		assertThat(url).isEqualTo("https://presigned.url");
	}

	@Test
	@DisplayName("키가 없으면 null을 반환한다")
	void generateUrl_whenKeyBlank_thenReturnsNull() {
		assertThat(enabledManager().generateUrl(null, ImageSize.THUMBNAIL)).isNull();
		assertThat(enabledManager().generateUrl(" ", ImageSize.THUMBNAIL)).isNull();
	}

	@Test
	@DisplayName("공개 경로 이미지를 무효화하면 모든 크기의 변환 경로를 한 번에 무효화한다")
	void invalidate_whenPublicKey_thenInvalidatesAllSizes() {
		// given
		ArgumentCaptor<CreateInvalidationRequest> captor = ArgumentCaptor.forClass(CreateInvalidationRequest.class);

		// when
		enabledManager().invalidate(PUBLIC_KEY);

		// then
		verify(cloudFrontClient).createInvalidation(captor.capture());
		CreateInvalidationRequest request = captor.getValue();
		assertThat(request.distributionId()).isEqualTo(DISTRIBUTION_ID);
		assertThat(request.invalidationBatch().paths().items()).containsExactly(
			"/fit-in/256x256/filters:format(webp):quality(80)/public/store-profiles/abc.png*",
			"/fit-in/640x640/filters:format(webp):quality(80)/public/store-profiles/abc.png*",
			"/fit-in/1200x1200/filters:format(webp):quality(80)/public/store-profiles/abc.png*");
	}

	@Test
	@DisplayName("공개 경로가 아니거나 CDN 설정이 없으면 무효화를 요청하지 않는다")
	void invalidate_whenPrivateKeyOrDisabled_thenSkips() {
		// when
		enabledManager().invalidate("members/1/profile/abc.png");
		disabledManager().invalidate(PUBLIC_KEY);

		// then
		verify(cloudFrontClient, never()).createInvalidation(any(CreateInvalidationRequest.class));
	}

	@Test
	@DisplayName("무효화 요청이 실패해도 예외를 전파하지 않는다")
	void invalidate_whenCloudFrontFails_thenDoesNotThrow() {
		// given
		when(cloudFrontClient.createInvalidation(any(CreateInvalidationRequest.class)))
			.thenThrow(new RuntimeException("CloudFront 오류"));

		// when & then
		enabledManager().invalidate(PUBLIC_KEY);
	}
}
