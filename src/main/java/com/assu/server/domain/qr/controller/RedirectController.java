package com.assu.server.domain.qr.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RedirectController {
	@GetMapping("/verify")
	public String handleQrRedirect(
		@RequestParam(value = "storeId", required = false) Long storeId,
		@RequestParam(value = "sessionId", required = false) Long sessionId,
		@RequestParam(value = "adminId", required = false) Long adminId,
		Model model) {

		String playStoreUrl = "https://play.google.com/store/apps/details?id=com.ssu.assu";

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
		model.addAttribute("playStoreUrl", playStoreUrl);

		return "qr_bridge";
	}
}
