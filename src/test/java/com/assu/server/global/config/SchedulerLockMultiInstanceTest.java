package com.assu.server.global.config;

import com.assu.server.support.CommonMockConfig;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(CommonMockConfig.class)
class SchedulerLockMultiInstanceTest {

    @Autowired
    private DataSource dataSource;

    private final String lockName = "multi-pod-test-" + UUID.randomUUID();

    @AfterEach
    void tearDown() {
        new JdbcTemplate(dataSource).update("DELETE FROM shedlock WHERE name = ?", lockName);
    }

    @Test
    void scheduledJob_whenTwoPodsFireAtSameTime_thenRunsOnce() throws Exception {
        // given: 파드마다 자기 LockProvider를 갖고 같은 DB를 바라본다
        List<LockingTaskExecutor> pods = List.of(newPod(), newPod());
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(pods.size());

        // when
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (LockingTaskExecutor pod : pods) {
                futures.add(threads.submit(() -> {
                    start.await();
                    pod.executeWithLock((Runnable) () -> {
                        executions.incrementAndGet();
                        sleep(300);
                    }, lockConfiguration());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            threads.shutdownNow();
        }

        // then
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void scheduledJob_whenOtherPodFiresSlightlyLaterWithinLockAtLeastFor_thenSkipped() {
        // given
        LockingTaskExecutor podA = newPod();
        LockingTaskExecutor podB = newPod();
        AtomicInteger executions = new AtomicInteger();

        // when: 파드 A가 금방 끝낸 직후 파드 B의 cron이 늦게 발화
        podA.executeWithLock((Runnable) executions::incrementAndGet, lockConfiguration());
        podB.executeWithLock((Runnable) executions::incrementAndGet, lockConfiguration());

        // then
        assertThat(executions.get()).isEqualTo(1);
    }

    private LockingTaskExecutor newPod() {
        return new DefaultLockingTaskExecutor(new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()));
    }

    private LockConfiguration lockConfiguration() {
        return new LockConfiguration(Instant.now(), lockName, Duration.ofMinutes(1), Duration.ofSeconds(30));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
