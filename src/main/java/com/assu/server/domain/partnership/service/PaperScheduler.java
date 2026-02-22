package com.assu.server.domain.partnership.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaperScheduler {
    private final PaperQueryServiceImpl paperQueryService;

    /**
     * 매일 자정에 만료된 Paper를 INACTIVE로 변경
     * "0 0 0 * * *" → 매일 00:00:00
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void updateExpiredPapers() {
        paperQueryService.updatePaperStatus();
    }
}
