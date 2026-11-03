package com.assu.server.global.config;

import com.assu.server.domain.partnership.service.PaperQueryServiceImpl;
import com.assu.server.domain.partnership.service.PaperScheduler;
import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Import(CommonMockConfig.class)
class SchedulerLockConfigTest {

    @Autowired
    private PaperScheduler paperScheduler;

    @MockitoBean
    private PaperQueryServiceImpl paperQueryService;

    @Test
    void scheduledJob_whenCalledTwiceWithinLockAtLeastFor_thenRunsOnce() {
        paperScheduler.updateExpiredPapers();
        paperScheduler.updateExpiredPapers();

        verify(paperQueryService, times(1)).updatePaperStatus();
    }
}
