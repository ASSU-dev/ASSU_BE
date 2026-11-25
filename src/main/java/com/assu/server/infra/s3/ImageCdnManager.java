package com.assu.server.infra.s3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.CreateInvalidationRequest;
import software.amazon.awssdk.services.cloudfront.model.InvalidationBatch;
import software.amazon.awssdk.services.cloudfront.model.Paths;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ImageCdnManager {

    public static final String PUBLIC_PREFIX = "public/";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AmazonS3Manager amazonS3Manager;
    private final CloudFrontClient cloudFrontClient;
    private final String baseUrl;
    private final String signatureKey;
    private final String distributionId;

    public ImageCdnManager(
            AmazonS3Manager amazonS3Manager,
            CloudFrontClient cloudFrontClient,
            @Value("${cloud.aws.image-cdn.base-url:}") String baseUrl,
            @Value("${cloud.aws.image-cdn.signature-key:}") String signatureKey,
            @Value("${cloud.aws.image-cdn.distribution-id:}") String distributionId
    ) {
        this.amazonS3Manager = amazonS3Manager;
        this.cloudFrontClient = cloudFrontClient;
        this.baseUrl = baseUrl;
        this.signatureKey = signatureKey;
        this.distributionId = distributionId;
    }

    public String generateUrl(String key, ImageSize size) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (!isServedByCdn(key)) {
            return amazonS3Manager.generatePresignedUrl(key);
        }
        String path = size.toPath(key);
        return baseUrl + path + "?signature=" + sign(path);
    }

    public void invalidate(String key) {
        if (!isServedByCdn(key) || distributionId.isBlank()) {
            return;
        }
        List<String> paths = Arrays.stream(ImageSize.values())
                .map(size -> size.toPath(key) + "*")
                .toList();
        try {
            cloudFrontClient.createInvalidation(CreateInvalidationRequest.builder()
                    .distributionId(distributionId)
                    .invalidationBatch(InvalidationBatch.builder()
                            .callerReference(UUID.randomUUID().toString())
                            .paths(Paths.builder().items(paths).quantity(paths.size()).build())
                            .build())
                    .build());
        } catch (RuntimeException e) {
            log.warn("CloudFront 캐시 무효화 실패 key={}", key, e);
        }
    }

    private boolean isServedByCdn(String key) {
        return key != null
                && key.startsWith(PUBLIC_PREFIX)
                && !baseUrl.isBlank()
                && !signatureKey.isBlank();
    }

    private String sign(String path) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signatureKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(path.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이미지 URL 서명 생성 실패", e);
        }
    }
}
