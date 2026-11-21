package com.assu.server.global.logging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StructuredLoggingConfigTest {

    private static final String CONFIG_LOCATION = "classpath:logback-spring.xml";
    private static final String MARKER = "structured-logging-test-marker";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    @AfterEach
    void restoreDefaultLogging() {
        MDC.clear();
        initializeLogging();
    }

    @Test
    void prodProfile_writesJsonLineWithServiceAndMdcFields(CapturedOutput output) throws Exception {
        // given
        initializeLogging("prod");
        Logger logger = LoggerFactory.getLogger("com.assu.server.LoggingTest");
        MDC.put("requestId", "req-123");
        MDC.put("traceId", "trace-abc");
        MDC.put("spanId", "span-def");

        // when
        logger.warn("[LOGIN_FAIL] ip=1.2.3.4 uri=/auth/login reason=bad {}", MARKER);

        // then
        JsonNode json = findJsonLine(output);
        assertThat(json.get("level").asText()).isEqualTo("WARN");
        assertThat(json.get("logger_name").asText()).isEqualTo("com.assu.server.LoggingTest");
        assertThat(json.get("message").asText()).startsWith("[LOGIN_FAIL] ip=1.2.3.4 uri=/auth/login");
        assertThat(json.get("service").asText()).isEqualTo("assu");
        assertThat(json.get("requestId").asText()).isEqualTo("req-123");
        assertThat(json.get("traceId").asText()).isEqualTo("trace-abc");
        assertThat(json.get("spanId").asText()).isEqualTo("span-def");
        assertThat(json.has("@timestamp")).isTrue();
        assertThat(json.has("SERVICE_NAME")).isFalse();
    }

    @Test
    void devProfile_includesStackTraceFieldWithRootCauseFirst(CapturedOutput output) throws Exception {
        // given
        initializeLogging("dev");
        Logger logger = LoggerFactory.getLogger("com.assu.server.LoggingTest");
        IllegalStateException exception = new IllegalStateException("wrapper", new IllegalArgumentException("root-cause"));

        // when
        logger.error("unexpected {}", MARKER, exception);

        // then
        JsonNode json = findJsonLine(output);
        assertThat(json.get("level").asText()).isEqualTo("ERROR");
        String stackTrace = json.get("stack_trace").asText();
        assertThat(stackTrace.indexOf("root-cause")).isLessThan(stackTrace.indexOf("wrapper"));
        assertThat(stackTrace).doesNotContain("jdk.internal.reflect.");
    }

    @Test
    void localProfile_keepsPlainTextPattern(CapturedOutput output) {
        // given
        initializeLogging("local");
        Logger logger = LoggerFactory.getLogger("com.assu.server.LoggingTest");

        // when
        logger.info("plain {}", MARKER);

        // then
        String line = Arrays.stream(output.getOut().split("\n"))
                .filter(candidate -> candidate.contains(MARKER))
                .findFirst()
                .orElseThrow();
        assertThat(line).doesNotStartWith("{");
        assertThat(line).contains("INFO");
    }

    private void initializeLogging(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.application.name", "assu");
        environment.setActiveProfiles(profiles);
        loggingSystem.cleanUp();
        loggingSystem.beforeInitialize();
        loggingSystem.initialize(new LoggingInitializationContext(environment), CONFIG_LOCATION, null);
    }

    private JsonNode findJsonLine(CapturedOutput output) throws Exception {
        String line = Arrays.stream(output.getOut().split("\n"))
                .filter(candidate -> candidate.contains(MARKER))
                .findFirst()
                .orElseThrow();
        assertThat(line).startsWith("{");
        return objectMapper.readTree(line);
    }
}
