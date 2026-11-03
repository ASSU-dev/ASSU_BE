package com.assu.server.global.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LoginFailureResponseTest {

	private MeterRegistry meterRegistry;
	private MockMvc mockMvc;

	@RestController
	static class StubLoginController {

		@PostMapping("/auth/commons/login")
		void login() {
			throw new BadCredentialsException("Bad credentials");
		}
	}

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		mockMvc = MockMvcBuilders.standaloneSetup(new StubLoginController())
			.setControllerAdvice(new GlobalExceptionAdvice(meterRegistry))
			.build();
	}

	@Test
	@DisplayName("비밀번호가 틀리면 500이 아닌 401로 응답하고 로그인 실패 카운터가 증가한다")
	void login_whenBadCredentials_thenRespondsUnauthorizedAndCountsFailure() throws Exception {
		// when
		mockMvc.perform(post("/auth/commons/login")
				.header("X-Forwarded-For", "203.0.113.7"))
			// then
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH4010"));

		assertEquals(1.0, meterRegistry.counter("auth.login.result", "result", "failure").count());
	}
}
