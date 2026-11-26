package com.assu.server.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class UnhandledExceptionResponseTest {

	@RestController
	static class StubFailingController {

		@GetMapping("/stub/fail")
		void fail() {
			throw new IllegalStateException("jdbc:mariadb://internal-host/assu_db connection refused");
		}
	}

	@Test
	@DisplayName("처리되지 않은 예외는 내부 메시지 없이 고정된 500 응답만 반환한다")
	void unhandledException_whenThrown_thenRespondsWithoutInternalMessage() throws Exception {
		// given
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StubFailingController())
			.setControllerAdvice(new GlobalExceptionAdvice(new SimpleMeterRegistry()))
			.build();

		// when
		mockMvc.perform(get("/stub/fail"))
			// then
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("COMMON500"))
			.andExpect(jsonPath("$.message").value(ErrorStatus._INTERNAL_SERVER_ERROR.getMessage()))
			.andExpect(jsonPath("$.result").doesNotExist());
	}
}
