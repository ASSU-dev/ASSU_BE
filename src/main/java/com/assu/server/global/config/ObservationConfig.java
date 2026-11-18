package com.assu.server.global.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.web.reactive.function.client.ClientHttpObservationDocumentation;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;

@Configuration
public class ObservationConfig {

    private static final String[] NOISE_PATH_PREFIXES = {"/actuator", "/ws", "/swagger-ui", "/v3/api-docs"};
    private static final String USAINT_HOST = "saint.ssu.ac.kr";
    private static final String HTTP_URL_KEY =
            ClientHttpObservationDocumentation.HighCardinalityKeyNames.HTTP_URL.asString();

    @Bean
    public ObservationPredicate noiseEndpointObservationPredicate() {
        return (name, context) -> {
            if (name.startsWith("spring.security.")) {
                return false;
            }
            if (context instanceof ServerRequestObservationContext serverContext) {
                String path = serverContext.getCarrier().getRequestURI();
                for (String prefix : NOISE_PATH_PREFIXES) {
                    if (path.startsWith(prefix)) {
                        return false;
                    }
                }
            }
            return true;
        };
    }

    // 유세인트 SSO 요청은 쿼리에 sToken이 실려 있어, 트레이스 span 속성으로 나가기 전에 쿼리를 잘라낸다
    @Bean
    public ObservationFilter usaintUrlQueryStripFilter() {
        return context -> {
            if (context instanceof ClientRequestObservationContext) {
                KeyValue url = context.getHighCardinalityKeyValue(HTTP_URL_KEY);
                if (url != null && url.getValue().contains(USAINT_HOST)) {
                    int queryIndex = url.getValue().indexOf('?');
                    if (queryIndex >= 0) {
                        context.addHighCardinalityKeyValue(
                                KeyValue.of(HTTP_URL_KEY, url.getValue().substring(0, queryIndex)));
                    }
                }
            }
            return context;
        };
    }
}
