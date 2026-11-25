package com.assu.server.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.assu.server.global.config.AmazonConfig;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class AmazonS3ManagerKeyNameTest {

	private final AmazonS3Manager amazonS3Manager =
		new AmazonS3Manager(mock(S3Client.class), mock(S3Presigner.class), mock(AmazonConfig.class));

	@Test
	@DisplayName("확장자가 있으면 경로 아래 UUID.확장자 키를 생성한다")
	void generateKeyName_whenExtensionGiven_thenAppendsExtension() {
		String key = amazonS3Manager.generateKeyName("partners/1/license", "png");

		assertThat(key).matches("partners/1/license/[0-9a-f-]{36}\\.png");
	}

	@Test
	@DisplayName("확장자가 비어 있으면 점 없이 UUID 키를 생성한다")
	void generateKeyName_whenExtensionBlank_thenOmitsDot() {
		assertThat(amazonS3Manager.generateKeyName("admins/1/sign", "")).matches("admins/1/sign/[0-9a-f-]{36}");
		assertThat(amazonS3Manager.generateKeyName("admins/1/sign", null)).matches("admins/1/sign/[0-9a-f-]{36}");
	}
}
