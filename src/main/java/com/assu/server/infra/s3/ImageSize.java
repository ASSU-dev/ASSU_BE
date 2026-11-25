package com.assu.server.infra.s3;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImageSize {
    THUMBNAIL(256),
    MEDIUM(640),
    LARGE(1200);

    private final int maxPixel;

    public String toPath(String key) {
        return "/fit-in/" + maxPixel + "x" + maxPixel + "/filters:format(webp):quality(80)/" + key;
    }
}
