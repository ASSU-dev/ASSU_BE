package com.assu.server.global.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.sun.net.httpserver.HttpServer;

class WebClientTimeoutTest {

	private static final long SERVER_DELAY_MS = 3000L;
	private static final int RESPONSE_TIMEOUT_MS = 500;

	private HttpServer server;
	private WebClient webClient;

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/slow", exchange -> {
			try {
				Thread.sleep(SERVER_DELAY_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			exchange.sendResponseHeaders(200, 0);
			exchange.close();
		});
		server.createContext("/fast", exchange -> {
			exchange.sendResponseHeaders(200, 0);
			exchange.close();
		});
		server.start();

		webClient = new WebClientConfig().webClient(WebClient.builder(), 1000, RESPONSE_TIMEOUT_MS);
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
	}

	@Test
	@DisplayName("응답이 늦은 외부 API 호출은 무한 대기하지 않고 응답 타임아웃으로 끊긴다")
	void webClient_whenResponseIsSlowerThanTimeout_thenFailsFast() {
		// given
		String baseUrl = "http://localhost:" + server.getAddress().getPort();
		// 커넥션 풀/Netty 초기화 비용이 측정에 섞이지 않도록 먼저 한 번 호출한다
		webClient.get().uri(baseUrl + "/fast").retrieve().toEntity(String.class).block();
		String url = baseUrl + "/slow";

		// when
		Instant start = Instant.now();
		assertThrows(WebClientRequestException.class,
			() -> webClient.get().uri(url).retrieve().toEntity(String.class).block());

		// then
		long elapsedMs = Duration.between(start, Instant.now()).toMillis();
		assertTrue(elapsedMs < SERVER_DELAY_MS, "타임아웃보다 오래 대기했습니다: " + elapsedMs + "ms");
	}
}
