package com.assu.server.domain.partnership.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.map.dto.SelectedPlacePayload;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.partnership.dto.ManualPartnershipRequestDTO;
import com.assu.server.domain.partnership.dto.ManualPartnershipResponseDTO;
import com.assu.server.domain.store.service.StoreRegistrationLock;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import com.assu.server.support.CommonMockConfig;

import jakarta.persistence.EntityManager;

// CI(GitHub Actions가 CI 환경 변수를 설정)에서는 Docker가 없으면 건너뛰지 않고 실패시켜, 테스트가 조용히 빠지지 않게 한다
@SpringBootTest
@ActiveProfiles("test")
@Import(CommonMockConfig.class)
@Testcontainers
@EnabledIf("dockerAvailableOrCi")
class ManualPartnershipConcurrencyTest {

    static boolean dockerAvailableOrCi() {
        return System.getenv("CI") != null || DockerClientFactory.instance().isDockerAvailable();
    }

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final int CONCURRENT_REQUESTS = 6;
    private static final String ADDRESS = "서울특별시 동작구 상도로 369";
    private static final String DETAIL_ADDRESS = "2층";

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate realRedisTemplate;

    @Autowired
    private PartnershipService partnershipService;

    @Autowired
    private StoreRegistrationLock storeRegistrationLock;

    @Autowired
    private EntityManager em;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Object mockRedisTemplate;
    private Long adminId;
    private String storeName;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        realRedisTemplate = new StringRedisTemplate(connectionFactory);
        realRedisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        mockRedisTemplate = ReflectionTestUtils.getField(storeRegistrationLock, "redisTemplate");
        ReflectionTestUtils.setField(storeRegistrationLock, "redisTemplate", realRedisTemplate);
        storeName = "동시성가게-" + UUID.randomUUID();
        adminId = transactionTemplate.execute(status -> persistAdmin().getId());
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(storeRegistrationLock, "redisTemplate", mockRedisTemplate);
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from Paper p where p.admin.id = :adminId")
                    .setParameter("adminId", adminId).executeUpdate();
            em.createQuery("delete from Store s where s.name = :name")
                    .setParameter("name", storeName).executeUpdate();
            em.createQuery("delete from Admin a where a.id = :adminId")
                    .setParameter("adminId", adminId).executeUpdate();
            em.createQuery("delete from Member m where m.id = :adminId")
                    .setParameter("adminId", adminId).executeUpdate();
        });
    }

    @Test
    void createManualPartnership_whenIdenticalRequestsRunConcurrently_thenCreatesExactlyOneStore() throws Exception {
        // given
        ManualPartnershipRequestDTO request = request();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<ManualPartnershipResponseDTO> responses = new ArrayList<>();

        // when
        try {
            List<Future<ManualPartnershipResponseDTO>> futures = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                futures.add(threads.submit(() -> {
                    start.await();
                    return partnershipService.createManualPartnership(request, adminId, null);
                }));
            }
            start.countDown();
            for (Future<ManualPartnershipResponseDTO> future : futures) {
                responses.add(future.get());
            }
        } finally {
            threads.shutdownNow();
        }

        // then
        assertThat(countStores()).isEqualTo(1L);
        assertThat(responses).extracting(ManualPartnershipResponseDTO::storeId).containsOnly(responses.get(0).storeId());
        assertThat(responses).filteredOn(ManualPartnershipResponseDTO::storeCreated).hasSize(1);
        assertThat(realRedisTemplate.hasKey(StoreRegistrationLock.lockKey(storeName, ADDRESS, DETAIL_ADDRESS))).isFalse();
    }

    @Test
    void createManualPartnership_whenLockHeldByAnotherOwner_thenFailsWithoutCreatingStoreAndKeepsOthersLock() {
        // given
        String key = StoreRegistrationLock.lockKey(storeName, ADDRESS, DETAIL_ADDRESS);
        realRedisTemplate.opsForValue().set(key, "other-owner", Duration.ofSeconds(30));

        // when / then
        try {
            assertThatThrownBy(() -> partnershipService.createManualPartnership(request(), adminId, null))
                    .isInstanceOf(GeneralException.class)
                    .extracting(e -> ((GeneralException) e).getCode())
                    .isEqualTo(ErrorStatus.STORE_REGISTRATION_IN_PROGRESS);
            assertThat(countStores()).isZero();
            assertThat(realRedisTemplate.opsForValue().get(key)).isEqualTo("other-owner");
        } finally {
            realRedisTemplate.delete(key);
        }
    }

    private ManualPartnershipRequestDTO request() {
        SelectedPlacePayload place = SelectedPlacePayload.builder()
                .roadAddress(ADDRESS)
                .address("서울특별시 동작구 상도동 1")
                .build();
        return new ManualPartnershipRequestDTO(storeName, place, DETAIL_ADDRESS,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), List.of());
    }

    private long countStores() {
        return transactionTemplate.execute(status -> em.createQuery(
                        "select count(s) from Store s where s.name = :name", Long.class)
                .setParameter("name", storeName)
                .getSingleResult());
    }

    private Admin persistAdmin() {
        Member member = Member.builder()
                .role(UserRole.ADMIN)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build();
        em.persist(member);
        Admin admin = Admin.builder()
                .member(member)
                .name("총학생회")
                .university(University.SSU)
                .isPhoneVerified(true)
                .officeAddress("서울특별시 동작구 상도로 369")
                .build();
        em.persist(admin);
        return admin;
    }
}
