package com.assu.server.domain.notification.controller;

import com.assu.server.domain.notification.event.NotificationFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/test/notification")
@RequiredArgsConstructor
public class NotificationTestController {
    
    private final ApplicationEventPublisher eventPublisher;
    
    @PostMapping("/retry-test/{outboxId}")
    public String testRetry(@PathVariable Long outboxId) {
        log.info("[Test] Publishing retry event for outboxId: {}", outboxId);
        eventPublisher.publishEvent(new NotificationFailedEvent(outboxId, 0));
        return "Retry event published for outboxId: " + outboxId;
    }
}