package com.assu.server.global.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
public class ObservationConfig {

    private static final String[] NOISE_PATH_PREFIXES = {"/actuator", "/ws", "/swagger-ui", "/v3/api-docs"};

    @Bean
    public ObservationPredicate noiseEndpointObservationPredicate() {
        return (name, context) -> {
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
}
