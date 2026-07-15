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
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private PartnerRepository partnerRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @Test
    @DisplayName("BACKOFFICE 토큰으로 Partner 회원 승인 및 감사 로그 기록")
    void backofficeTokenCanApprovePartnerMember() throws Exception {
        Member backofficeMember = createBackofficeMember("operator@test.com", "Operator");
        Member partnerMember = createPartnerMember("partner@test.com", ActivationStatus.SUSPEND);
        String accessToken = jwtUtil.issueBackofficeTokens(
                backofficeMember.getId(),
                "operator@test.com",
                UserRole.BACKOFFICE,
                AuthRealm.COMMON.name()
        ).accessToken();

        mockMvc.perform(patch("/backoffice/members/{memberId}/approve", partnerMember.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        Member updated = memberRepository.findById(partnerMember.getId()).orElseThrow();
        assertThat(updated.getIsActivated()).isEqualTo(ActivationStatus.ACTIVE);

        assertThat(backofficeAuditLogRepository.findAll())
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getAction()).isEqualTo("MEMBER_APPROVE");
                    assertThat(log.getTargetResourceId()).isEqualTo(String.valueOf(partnerMember.getId()));
                    assertThat(log.getBackofficeMemberId()).isEqualTo(backofficeMember.getId());
                });
    }

    @Test
    @DisplayName("STUDENT 앱 토큰으로 회원 승인 API 접근 거부")
    void studentTokenCannotApproveMember() throws Exception {
        Member student = createStudentMember("student2@test.com");
        Member partnerMember = createPartnerMember("partner2@test.com", ActivationStatus.SUSPEND);
        String accessToken = jwtUtil.issueTokens(
                student.getId(),
                "student2@test.com",
                UserRole.STUDENT,
                AuthRealm.COMMON.name()
        ).accessToken();

        assertThatThrownBy(() -> mockMvc.perform(patch("/backoffice/members/{memberId}/approve", partnerMember.getId())
                        .header("Authorization", "Bearer " + accessToken)))
                .isInstanceOf(CustomAuthException.class)
                .extracting(ex -> ((CustomAuthException) ex).getCode())
                .isEqualTo(ErrorStatus.JWT_AUDIENCE_MISMATCH);
    }

    @Test
    @DisplayName("BACKOFFICE 토큰으로 탈퇴 회원 복구 및 감사 로그 기록")
    void backofficeTokenCanRestoreDeletedMember() throws Exception {
        Member backofficeMember = createBackofficeMember("operator2@test.com", "Operator2");
        Member partnerMember = createPartnerMember("partner3@test.com", ActivationStatus.ACTIVE);
        partnerMember.setDeletedAt(LocalDateTime.now());
        memberRepository.save(partnerMember);

        String accessToken = jwtUtil.issueBackofficeTokens(
                backofficeMember.getId(),
                "operator2@test.com",
                UserRole.BACKOFFICE,
                AuthRealm.COMMON.name()
        ).accessToken();

        mockMvc.perform(patch("/backoffice/members/{memberId}/restore", partnerMember.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        Member updated = memberRepository.findById(partnerMember.getId()).orElseThrow();
        assertThat(updated.getDeletedAt()).isNull();

        assertThat(backofficeAuditLogRepository.findAll())
                .singleElement()
                .satisfies(log -> assertThat(log.getAction()).isEqualTo("MEMBER_RESTORE"));
    }

    @Test
    @DisplayName("SUSPEND Partner는 공통 로그인 시 승인 대기 에러 반환")
    void suspendPartnerCannotLoginCommon() throws Exception {
        createPartnerMemberWithPassword("pending@test.com", ActivationStatus.SUSPEND, "password123");

        mockMvc.perform(post("/auth/commons/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pending@test.com","password":"password123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_4017"))
                .andExpect(jsonPath("$.message").value(ErrorStatus.MEMBER_PENDING_APPROVAL.getMessage()));
    }

    @Test
    @DisplayName("BACKOFFICE 토큰으로 회원 목록 조회 가능")
    void backofficeTokenCanListMembers() throws Exception {
        Member backofficeMember = createBackofficeMember("operator3@test.com", "Operator3");
        createPartnerMember("partner4@test.com", ActivationStatus.SUSPEND);
        String accessToken = jwtUtil.issueBackofficeTokens(
                backofficeMember.getId(),
                "operator3@test.com",
                UserRole.BACKOFFICE,
                AuthRealm.COMMON.name()
        ).accessToken();

        mockMvc.perform(get("/backoffice/members")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private Member createPartnerMember(String email, ActivationStatus status) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.PARTNER)
                .isActivated(status)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());

        commonAuthRepository.save(CommonAuth.builder()
                .member(member)
                .email(email)
                .hashedPassword("hashed-password")
                .lastLoginAt(LocalDateTime.now())
                .build());

        Partner partner = partnerRepository.save(Partner.builder()
                .member(member)
                .name("Partner " + email)
                .phoneNum("01099998888")
                .isPhoneVerified(true)
                .address("Seoul")
                .detailAddress("Detail")
                .licenseUrl("partners/" + member.getId() + "/license.png")
                .latitude(37.0)
                .longitude(127.0)
                .build());
        member.setPartnerProfile(partner);

        storeRepository.save(Store.builder()
                .partner(partner)
                .rate(0)
                .isActivate(ActivationStatus.SUSPEND)
                .name("Store " + email)
                .address("Seoul")
                .detailAddress("Detail")
                .latitude(37.0)
                .longitude(127.0)
                .build());

        return memberRepository.save(member);
    }

    private void createPartnerMemberWithPassword(String email, ActivationStatus status, String rawPassword) {
        Member member = memberRepository.save(Member.builder()
                .role(UserRole.PARTNER)
                .isActivated(status)
                .isLocationTermAgreed(true)
                .isMarketingTermAgreed(false)
                .build());

        commonAuthRepository.save(CommonAuth.builder()
                .member(member)
                .email(email)
                .hashedPassword(passwordEncoder.encode(rawPassword))
                .lastLoginAt(LocalDateTime.now())
                .build());

        Partner partner = partnerRepository.save(Partner.builder()
                .member(member)
                .name("Partner " + email)
                .phoneNum("01088887777")
                .isPhoneVerified(true)
                .address("Seoul")
                .detailAddress("Detail")
                .licenseUrl("partners/" + member.getId() + "/license.png")
                .latitude(37.0)
                .longitude(127.0)
                .build());
        member.setPartnerProfile(partner);
        memberRepository.save(member);
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

        @Bean
        ConnectionFactory rabbitConnectionFactory() {
            return Mockito.mock(ConnectionFactory.class);
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
