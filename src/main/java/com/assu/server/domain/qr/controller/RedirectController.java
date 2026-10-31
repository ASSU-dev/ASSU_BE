package com.assu.server.domain.qr.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RedirectController {
	private static final String PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.ssu.assu";
	private static final String APP_STORE_URL = "https://apps.apple.com/kr/app/a-ssu/id6803354251";

	@GetMapping("/verify")
	public String handleQrRedirect(
		@RequestParam(value = "storeId", required = false) Long storeId,
		@RequestParam(value = "sessionId", required = false) Long sessionId,
		@RequestParam(value = "adminId", required = false) Long adminId,
		@RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
		Model model) {

		String appScheme = "assu-app://verify";
		String finalAppUrl = "";

		if (storeId != null) {
			finalAppUrl = appScheme + "?storeId=" + storeId;
		} else if (sessionId != null && adminId != null) {
			finalAppUrl = appScheme + "?sessionId=" + sessionId + "&adminId=" + adminId;
		} else {
			finalAppUrl = appScheme;
		}

		model.addAttribute("appLink", finalAppUrl);
		model.addAttribute("storeUrl", isIos(userAgent) ? APP_STORE_URL : PLAY_STORE_URL);

		return "qr_bridge";
	}

	private boolean isIos(String userAgent) {
		if (userAgent == null) {
			return false;
		}
		return userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iPod");
	}
}
