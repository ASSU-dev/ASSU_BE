package com.assu.server.backoffice;

import com.assu.server.domain.auth.entity.CommonAuth;
import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.backoffice.entity.BackofficeUser;
import com.assu.server.domain.backoffice.repository.BackofficeAuditLogRepository;
import com.assu.server.domain.backoffice.repository.BackofficeUserRepository;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.service.StudentServiceImpl;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BackofficeSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CommonAuthRepository commonAuthRepository;

    @Autowired
    private BackofficeUserRepository backofficeUserRepository;

    @Autowired
    private BackofficeAuditLogRepository backofficeAuditLogRepository;

    @MockitoBean
    private StudentServiceImpl studentService;

    @MockitoBean
    private ConnectionFactory connectionFactory;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        backofficeAuditLogRepository.deleteAll();
    }

    @Test
    @DisplayName("BACKOFFICE 토큰으로 /backoffice/students/sync 접근 가능")
    void backofficeTokenCanAccessSyncEndpoint() throws Exception {
        Member backofficeMember = createBackofficeMember("backoffice@test.com", "Operator");
        String accessToken = jwtUtil.issueBackofficeTokens(
                backofficeMember.getId(),
                "backoffice@test.com",
                UserRole.BACKOFFICE,
                AuthRealm.COMMON.name()
        ).accessToken();

        mockMvc.perform(post("/backoffice/students/sync")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        verify(studentService).syncUserPapersForAllStudents();
        assertThat(backofficeAuditLogRepository.findAll())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo("STUDENT_SYNC");
                    assertThat(log.getBackofficeMemberId()).isEqualTo(backofficeMember.getId());
                    assertThat(log.getRequestUri()).isEqualTo("/backoffice/students/sync");
                });
    }

    @Test
    @DisplayName("STUDENT 앱 토큰으로 /backoffice/students/sync 접근 거부")
    void studentTokenCannotAccessBackofficeEndpoint() throws Exception {
        Member student = createStudentMember("student@test.com");
        String accessToken = jwtUtil.issueTokens(
                student.getId(),
                "student@test.com",
                UserRole.STUDENT,
                AuthRealm.COMMON.name()
        ).accessToken();

        assertThatThrownBy(() -> mockMvc.perform(post("/backoffice/students/sync")
                        .header("Authorization", "Bearer " + accessToken)))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.JWT_AUDIENCE_MISMATCH);

        assertThat(backofficeAuditLogRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("ADMIN 앱 토큰으로 /backoffice/students/sync 접근 거부")
    void adminTokenCannotAccessBackofficeEndpoint() throws Exception {
        Member admin = createAdminMember("admin@test.com");
        String accessToken = jwtUtil.issueTokens(
                admin.getId(),
                "admin@test.com",
                UserRole.ADMIN,
                AuthRealm.COMMON.name()
        ).accessToken();

        assertThatThrownBy(() -> mockMvc.perform(post("/backoffice/students/sync")
                        .header("Authorization", "Bearer " + accessToken)))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.JWT_AUDIENCE_MISMATCH);

        assertThat(backofficeAuditLogRepository.findAll()).isEmpty();
    }

    private Member createBackofficeMember(String email, String name) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.BACKOFFICE)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());

        commonAuthRepository.save(CommonAuth.builder()
                .member(member)
                .email(email)
                .hashedPassword("hashed-password")
                .lastLoginAt(LocalDateTime.now())
                .build());

        backofficeUserRepository.save(BackofficeUser.builder()
                .member(member)
                .name(name)
                .build());

        return memberRepository.findById(member.getId()).orElseThrow();
    }

    private Member createStudentMember(String email) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.STUDENT)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());

        commonAuthRepository.save(CommonAuth.builder()
                .member(member)
                .email(email)
                .hashedPassword("hashed-password")
                .lastLoginAt(LocalDateTime.now())
                .build());

        member.setStudentProfile(Student.builder()
                .member(member)
                .name("Test Student")
                .build());

        return memberRepository.save(member);
    }

    private Member createAdminMember(String email) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.ADMIN)
                .isActivated(ActivationStatus.ACTIVE)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());

        commonAuthRepository.save(CommonAuth.builder()
                .member(member)
                .email(email)
                .hashedPassword("hashed-password")
                .lastLoginAt(LocalDateTime.now())
                .build());

        member.setAdminProfile(Admin.builder()
                .member(member)
                .name("Test Admin")
                .isPhoneVerified(false)
                .officeAddress("Test Office")
                .university(University.SSU)
                .build());

        return memberRepository.save(member);
    }

    @TestConfiguration
    static class TestJwtConfig {

        @Bean
        FirebaseMessaging firebaseMessaging() {
            return Mockito.mock(FirebaseMessaging.class);
        }

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            RedisConnectionFactory connectionFactory = Mockito.mock(RedisConnectionFactory.class);
            RedisConnection connection = Mockito.mock(RedisConnection.class);
            Mockito.when(connectionFactory.getConnection()).thenReturn(connection);
            return connectionFactory;
        }

        @Bean
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate() {
            return Mockito.mock(RedisTemplate.class);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }

        @Bean(name = "rabbitListenerContainerFactory")
        RabbitListenerContainerFactory<?> rabbitListenerContainerFactory() {
            var factory = Mockito.mock(RabbitListenerContainerFactory.class);
            var container = Mockito.mock(org.springframework.amqp.rabbit.listener.MessageListenerContainer.class);
            Mockito.when(factory.createListenerContainer(Mockito.any())).thenReturn(container);
            return factory;
        }

        @Bean
        @Primary
        JwtUtil jwtUtil(MemberRepository memberRepository, StringRedisTemplate stringRedisTemplate, RedisConnectionFactory redisConnectionFactory) {
            ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
            Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
            Mockito.when(stringRedisTemplate.hasKey(Mockito.anyString())).thenReturn(false);
            Mockito.when(stringRedisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);

            JwtUtil jwtUtil = new JwtUtil(memberRepository, stringRedisTemplate);
            ReflectionTestUtils.setField(jwtUtil, "secretKey", "S3csfifR3TrgwiKeyM2023WClokeyAppWIFNEGIBKWMGJ");
            ReflectionTestUtils.setField(jwtUtil, "accessValidSeconds", 3600);
            ReflectionTestUtils.setField(jwtUtil, "backofficeAccessValidSeconds", 1800);
            ReflectionTestUtils.setField(jwtUtil, "refreshValidSeconds", 1209600);
            return jwtUtil;
        }
    }
}
