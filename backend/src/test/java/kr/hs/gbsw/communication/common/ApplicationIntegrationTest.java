package kr.hs.gbsw.communication.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;
import kr.hs.gbsw.communication.auth.delivery.CredentialDeliveryCommand;
import kr.hs.gbsw.communication.auth.delivery.CredentialDeliveryPort;
import kr.hs.gbsw.communication.auth.delivery.CredentialDeliveryReceipt;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseStatus;
import kr.hs.gbsw.communication.moderation.domain.ModerationCaseType;
import kr.hs.gbsw.communication.moderation.domain.ModerationVoteDecision;
import kr.hs.gbsw.communication.moderation.service.ContentReportService;
import kr.hs.gbsw.communication.moderation.service.ModerationCaseService;
import kr.hs.gbsw.communication.moderation.service.ModerationVoteService;
import kr.hs.gbsw.communication.proposal.domain.AuthorVisibility;
import kr.hs.gbsw.communication.proposal.domain.SupportResult;
import kr.hs.gbsw.communication.proposal.service.ProposalAssignmentService;
import kr.hs.gbsw.communication.proposal.service.ProposalService;
import kr.hs.gbsw.communication.user.exception.BootstrapAlreadyCompletedException;
import kr.hs.gbsw.communication.user.service.SuperAdminBootstrapService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ApplicationIntegrationTest {

    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.10")
            .withDatabaseName("school_communication")
            .withUsername("application_test")
            .withPassword("application_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.security.secrets.throttle-fingerprint",
                () -> "integration-test-throttle-fingerprint-secret");
        registry.add("app.identity-vault.key-base64",
                () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        registry.add("app.identity-vault.key-version", () -> "1");
        registry.add("app.proposal-ownership.key-base64",
                () -> "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=");
        registry.add("app.proposal-ownership.key-version", () -> "1");
        registry.add("app.security.rate-limit.login-failures-before-delay", () -> "3");
        registry.add("app.security.rate-limit.general-requests-per-minute", () -> "1000");
        registry.add("app.deployment.authentication-requests-per-minute", () -> "1000");
        registry.add("springdoc.api-docs.enabled", () -> "true");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CredentialDeliveryPort credentialDeliveryPort;

    private final AtomicReference<CredentialDeliveryCommand> deliveredCredential = new AtomicReference<>();

    @Autowired
    private SuperAdminBootstrapService superAdminBootstrapService;

    @Autowired
    private ProposalService proposalService;

    @Autowired
    private ProposalAssignmentService proposalAssignmentService;

    @Autowired
    private ContentReportService contentReportService;

    @Autowired
    private ModerationCaseService moderationCaseService;

    @Autowired
    private ModerationVoteService moderationVoteService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void cleanApplicationData() {
        deliveredCredential.set(null);
        doAnswer(invocation -> {
            CredentialDeliveryCommand command = invocation.getArgument(0);
            deliveredCredential.set(command);
            return new CredentialDeliveryReceipt("integration-test-delivery");
        }).when(credentialDeliveryPort).deliver(any());
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM identity_reveal_records");
        jdbcTemplate.update("DELETE FROM proposal_visibility_history");
        jdbcTemplate.update("DELETE FROM proposal_comments");
        jdbcTemplate.update("DELETE FROM moderation_votes");
        jdbcTemplate.update("DELETE FROM moderation_reviewer_snapshots");
        jdbcTemplate.update("DELETE FROM moderation_cases");
        jdbcTemplate.update("DELETE FROM content_reports");
        jdbcTemplate.update("DELETE FROM proposal_notifications");
        jdbcTemplate.update("DELETE FROM proposal_official_responses");
        jdbcTemplate.update("DELETE FROM proposal_teacher_assignments");
        jdbcTemplate.update("DELETE FROM proposal_status_history");
        jdbcTemplate.update("DELETE FROM proposal_supports");
        jdbcTemplate.update("DELETE FROM proposal_author_ownership_tags");
        jdbcTemplate.update("DELETE FROM proposal_identities");
        jdbcTemplate.update("DELETE FROM proposals");
        jdbcTemplate.update("DELETE FROM bootstrap_markers");
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM activation_codes");
        jdbcTemplate.update("DELETE FROM credential_delivery_records");
        jdbcTemplate.update("DELETE FROM privileged_change_requests");
        jdbcTemplate.update("DELETE FROM office_assignments");
        jdbcTemplate.update("DELETE FROM role_assignments");
        jdbcTemplate.update("DELETE FROM credentials");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
        jdbcTemplate.update("DELETE FROM security_throttle_states");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void appliesFlywayAndCreatesJdbcSessionTablesOnEmptyMySql() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("15");

        Integer sessionTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'SPRING_SESSION'",
                Integer.class);

        assertThat(sessionTableCount).isEqualTo(1);
    }

    @Test
    void publicStatusReturnsTraceIdAndSecurityHeadersAndCsrfEndpointIssuesToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/system/status"))
                .header("X-Request-Id", "integration_trace_123")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Request-Id")).contains("integration_trace_123");
        String contentSecurityPolicy = response.headers()
                .firstValue("Content-Security-Policy")
                .orElseThrow();
        assertThat(contentSecurityPolicy)
                .contains("default-src 'self'", "object-src 'none'", "frame-ancestors 'none'");
        assertThat(response.body()).contains("\"status\":\"ok\"", "\"apiVersion\":\"v1\"");

        HttpResponse<String> csrfResponse = httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(csrfResponse.statusCode()).isEqualTo(200);
        JsonNode csrfBody = objectMapper.readTree(csrfResponse.body());
        assertThat(csrfBody.path("headerName").asText()).isNotBlank();
        assertThat(csrfBody.path("token").asText()).isNotBlank();
    }

    @Test
    void protectedPathReturnsSafeCommonAuthenticationError() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/not-implemented")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body())
                .contains("\"code\":\"AUTHENTICATION_REQUIRED\"")
                .contains("\"traceId\":")
                .doesNotContain("Exception", "java.", "SELECT", "/Users/");
    }

    @Test
    void openApiPublishesAuthenticationContractAndCommonErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/activate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/reauthenticate'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/users'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/offices/{office}/appointments'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}/comments'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}/comments'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}/comments/{commentPublicId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}/support'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}/support'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/proposals/{publicId}/assignments'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}/review-start'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}/decisions/accept'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}/execution-complete'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/proposals/{publicId}/reports'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/moderation/reports'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/moderation/reports/{reportPublicId}/cases'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/moderation/cases/{publicId}/votes/approve'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/identity-reveal-cases/{publicId}/reveal'].post").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie").exists())
                .andExpect(jsonPath("$.components.securitySchemes.csrfHeader").exists());
    }

    @Test
    void activationIsOneTimeAndLoginCreatesServerSession() throws Exception {
        String loginId = "student.2026";
        String activationCode = "7Kp9zT4Wh3NmQ6Rx";
        String password = "correct horse battery staple";
        createPendingAccount(loginId, "홍길동", "STUDENT", activationCode, Instant.now().plusSeconds(3600));

        mockMvc.perform(post("/api/v1/auth/activate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"student.2026","activationCode":"7Kp9zT4Wh3NmQ6Rx","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT account_status FROM users WHERE login_id = ?", String.class, loginId))
                .isEqualTo("ACTIVE");
        String storedPassword = jdbcTemplate.queryForObject("""
                        SELECT c.password_hash FROM credentials c
                        JOIN users u ON u.id = c.user_id
                        WHERE u.login_id = ?
                        """, String.class, loginId);
        assertThat(storedPassword).startsWith("$argon2id$").doesNotContain(password);

        mockMvc.perform(post("/api/v1/auth/activate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"student.2026","activationCode":"7Kp9zT4Wh3NmQ6Rx","password":"another secure passphrase"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_FAILED"));

        MvcResult login = login(loginId, password);
        Cookie sessionCookie = requireSessionCookie(login);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, loginId))
                .isEqualTo(1);

        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value(loginId))
                .andExpect(jsonPath("$.roles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.offices").isArray());

        mockMvc.perform(post("/api/v1/auth/logout").cookie(sessionCookie).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredActivationCodeAndMissingCsrfAreRejected() throws Exception {
        createPendingAccount(
                "expired.student",
                "만료 학생",
                "STUDENT",
                "7Kp9zT4Wh3NmQ6Rx",
                Instant.now().minusSeconds(1));

        mockMvc.perform(post("/api/v1/auth/activate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"expired.student","activationCode":"7Kp9zT4Wh3NmQ6Rx","password":"correct horse battery staple"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTIVATION_FAILED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"expired.student\",\"password\":\"correct horse battery staple\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void loginFailuresPersistAndReturnGeneralizedErrors() throws Exception {
        createActiveAccount("known.student", "알려진 학생", "STUDENT", "known secure passphrase");

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(request -> {
                                request.setRemoteAddr("203.0.113.10");
                                return request;
                            })
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\":\"known.student\",\"password\":\"wrong password value\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                    .andExpect(jsonPath("$.message").value("로그인 정보 또는 계정 상태를 확인해 주세요."));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"known.student\",\"password\":\"known secure passphrase\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_THROTTLED"));

        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM security_throttle_states
                        WHERE failure_count = 3 AND blocked_until IS NOT NULL
                        """, Integer.class))
                .isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.11");
                            return request;
                        })
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"unknown.student\",\"password\":\"wrong password value\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
                .andExpect(jsonPath("$.message").value("로그인 정보 또는 계정 상태를 확인해 주세요."));
    }

    @Test
    void passwordResetConsumesCodeAndInvalidatesExistingSessions() throws Exception {
        SeedAccount account = createActiveAccount(
                "reset.student", "재설정 학생", "STUDENT", "old secure passphrase");
        Cookie sessionCookie = requireSessionCookie(login("reset.student", "old secure passphrase"));
        String resetCode = "G8vY4mN7qR2xP9Ts";
        insertPasswordResetCode(account, resetCode, Instant.now().plusSeconds(1800));

        mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"reset.student","resetCode":"G8vY4mN7qR2xP9Ts","newPassword":"new secure passphrase value"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me").cookie(sessionCookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"reset.student\",\"password\":\"old secure passphrase\"}"))
                .andExpect(status().isUnauthorized());
        login("reset.student", "new secure passphrase value");

        mockMvc.perform(post("/api/v1/auth/password-reset/complete")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"reset.student","resetCode":"G8vY4mN7qR2xP9Ts","newPassword":"another secure passphrase"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_FAILED"));
    }

    @Test
    void onlySuperAdminCanProvisionAccountAndRawCodeIsNeverStored() throws Exception {
        createActiveAccount("ordinary.student", "일반 학생", "STUDENT", "student secure passphrase");
        Cookie studentSession = requireSessionCookie(login("ordinary.student", "student secure passphrase"));

        mockMvc.perform(post("/api/v1/admin/users")
                        .cookie(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"new.student","displayName":"새 학생","role":"STUDENT","reason":"2026학년도 등록"}
                                """))
                .andExpect(status().isForbidden());

        createActiveAccount("system.requester", "요청 관리자", "SUPER_ADMIN", "requester secure passphrase");
        Cookie requesterSession = requireSessionCookie(login("system.requester", "requester secure passphrase"));

        UUID quorumRequestId = requestGovernedChange(requesterSession, Map.of(
                "changeType", "CREATE_ACCOUNT",
                "loginId", "system.approver",
                "displayName", "승인 관리자",
                "role", "SUPER_ADMIN",
                "reason", "초기 2인 승인 정족수 구성"));
        approveGovernedChange(requesterSession, quorumRequestId);
        CredentialDeliveryCommand quorumDelivery = deliveredCredential.get();
        assertThat(quorumDelivery).isNotNull();
        UUID reissueRequestId = requestGovernedChange(requesterSession, Map.of(
                "changeType", "REISSUE_ACTIVATION_CODE",
                "targetUserPublicId", quorumDelivery.targetUserPublicId(),
                "reason", "초기 정족수 활성화 코드 복구"));
        approveGovernedChange(requesterSession, reissueRequestId);
        CredentialDeliveryCommand reissuedQuorumDelivery = deliveredCredential.get();
        assertThat(reissuedQuorumDelivery.oneTimeCode()).isNotEqualTo(quorumDelivery.oneTimeCode());
        mockMvc.perform(post("/api/v1/auth/activate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "loginId", "system.approver",
                                "activationCode", reissuedQuorumDelivery.oneTimeCode(),
                                "password", "approver secure passphrase"))))
                .andExpect(status().isNoContent());
        Cookie approverSession = requireSessionCookie(login("system.approver", "approver secure passphrase"));
        deliveredCredential.set(null);

        mockMvc.perform(post("/api/v1/admin/users")
                        .cookie(requesterSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"new.student","displayName":"새 학생","role":"STUDENT","reason":"2026학년도 등록"}
                                """))
                .andExpect(status().isForbidden());

        UUID governanceRequestId = requestGovernedChange(requesterSession, Map.of(
                "changeType", "CREATE_ACCOUNT",
                "loginId", "new.student",
                "displayName", "새 학생",
                "role", "STUDENT",
                "reason", "2026학년도 등록"));
        mockMvc.perform(post("/api/v1/admin/governance/requests/{publicId}/approve", governanceRequestId)
                        .cookie(requesterSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"자기 승인 시도\"}"))
                .andExpect(status().isForbidden());
        MvcResult approved = approveGovernedChange(approverSession, governanceRequestId);

        CredentialDeliveryCommand delivery = deliveredCredential.get();
        assertThat(delivery).isNotNull();
        String rawCode = delivery.oneTimeCode();
        String storedCode = jdbcTemplate.queryForObject("""
                        SELECT ac.code_hash
                        FROM activation_codes ac
                        JOIN users u ON u.id = ac.user_id
                        WHERE u.login_id = 'new.student'
                        """, String.class);
        assertThat(rawCode).hasSize(16);
        assertThat(delivery.recipientReference()).isEqualTo("new.student");
        assertThat(approved.getResponse().getContentAsString()).doesNotContain(rawCode, "\"code\"");
        assertThat(storedCode).startsWith("$argon2id$").doesNotContain(rawCode);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM credential_delivery_records WHERE delivery_status = 'DELIVERED'",
                Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM privileged_change_requests WHERE bootstrap_quorum_exception = TRUE",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM audit_logs
                        WHERE event_type = 'ADMIN_ACCOUNT_CREATED' AND outcome = 'SUCCESS'
                        """, Integer.class))
                .isEqualTo(2);
    }

    @Test
    void superAdminCanSearchAccountsWithStatusPaginationAndCurrentAssignments() throws Exception {
        SeedAccount admin = createActiveAccount(
                "search.admin", "검색 관리자", "SUPER_ADMIN", "admin secure passphrase");
        SeedAccount student = createActiveAccount(
                "search.student", "검색 대상 학생", "STUDENT", "student secure passphrase");
        createActiveAccount("other.teacher", "다른 교사", "TEACHER", "teacher secure passphrase");
        Cookie adminSession = requireSessionCookie(login("search.admin", "admin secure passphrase"));

        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO office_assignments (
                            id, user_id, office_type, starts_at, ends_at,
                            assigned_by_user_id, assigned_at, reason
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'STUDENT_COUNCIL_PRESIDENT', ?, NULL, UUID_TO_BIN(?), ?, ?)
                        """,
                UUID.randomUUID().toString(), student.id().toString(),
                Timestamp.from(now.minusSeconds(60)), admin.id().toString(),
                Timestamp.from(now), "통합 테스트 현임");
        jdbcTemplate.update("""
                        INSERT INTO role_assignments (
                            id, user_id, role_type, starts_at, ends_at,
                            assigned_by_user_id, assigned_at, reason
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'TEACHER', ?, ?, NULL, ?, ?)
                        """,
                UUID.randomUUID().toString(), student.id().toString(),
                Timestamp.from(now.minusSeconds(120)), Timestamp.from(now.minusSeconds(90)),
                Timestamp.from(now.minusSeconds(120)), "종료된 역할");
        Cookie studentSession = requireSessionCookie(login("search.student", "student secure passphrase"));

        mockMvc.perform(get("/api/v1/admin/users")
                        .cookie(studentSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/users")
                        .cookie(adminSession)
                        .param("query", "대상")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].publicId").value(student.publicId().toString()))
                .andExpect(jsonPath("$.items[0].loginId").value("search.student"))
                .andExpect(jsonPath("$.items[0].currentRoles.length()").value(1))
                .andExpect(jsonPath("$.items[0].currentRoles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.items[0].currentOffices[0]").value("STUDENT_COUNCIL_PRESIDENT"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/api/v1/admin/users")
                        .cookie(adminSession)
                        .param("query", "search%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anonymousAndNamedProposalsProtectIdentityWhileTeachersSeeGatheringProposals() throws Exception {
        SeedAccount anonymousStudent = createActiveAccount(
                "anonymous.student", "익명 학생", "STUDENT", "student secure passphrase");
        createActiveAccount("proposal.teacher", "제안 교사", "TEACHER", "teacher secure passphrase");
        Cookie studentSession = requireSessionCookie(login("anonymous.student", "student secure passphrase"));
        Cookie teacherSession = requireSessionCookie(login("proposal.teacher", "teacher secure passphrase"));

        MvcResult anonymousCreated = mockMvc.perform(post("/api/v1/proposals")
                        .cookie(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"기숙사 생활 개선",
                                  "content":"소등 시간을 논의해 주세요. <script>alert(1)</script>",
                                  "authorVisibility":"ANONYMOUS"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorVisibility").value("ANONYMOUS"))
                .andExpect(jsonPath("$.authorDisplayName").doesNotExist())
                .andExpect(jsonPath("$.supportCount").value(0))
                .andExpect(jsonPath("$.viewerSupported").value(false))
                .andExpect(jsonPath("$.workflowStatus").value("GATHERING_SUPPORT"))
                .andReturn();
        JsonNode anonymousBody = objectMapper.readTree(anonymousCreated.getResponse().getContentAsString());
        UUID proposalPublicId = UUID.fromString(anonymousBody.get("publicId").asText());

        mockMvc.perform(get("/api/v1/proposals/{publicId}", proposalPublicId).cookie(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("소등 시간을 논의해 주세요. <script>alert(1)</script>"))
                .andExpect(jsonPath("$.authorDisplayName").doesNotExist());
        // 교사도 동의 모집 중인 제안을 본다. 다만 익명 작성자의 신원은 드러나지 않는다.
        mockMvc.perform(get("/api/v1/proposals/{publicId}", proposalPublicId).cookie(teacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("GATHERING_SUPPORT"))
                .andExpect(jsonPath("$.authorVisibility").value("ANONYMOUS"))
                .andExpect(jsonPath("$.authorDisplayName").doesNotExist())
                .andExpect(jsonPath("$.viewerCanEdit").value(false))
                .andExpect(jsonPath("$.viewerCanManage").value(false));
        mockMvc.perform(get("/api/v1/proposals").cookie(teacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/proposals?scope=GATHERING_SUPPORT&sort=MOST_SUPPORTED")
                        .cookie(teacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].authorVisibility").value("ANONYMOUS"))
                .andExpect(jsonPath("$.items[0].authorDisplayName").doesNotExist());

        byte[] encryptedIdentity = jdbcTemplate.queryForObject("""
                        SELECT identity.encrypted_user_id
                        FROM proposal_identities identity
                        JOIN proposals proposal ON proposal.id = identity.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                        """, byte[].class, proposalPublicId.toString());
        assertThat(encryptedIdentity).isNotNull().hasSize(32);
        assertThat(encryptedIdentity).isNotEqualTo(uuidBytes(anonymousStudent.id()));
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'proposals'
                          AND column_name LIKE '%user_id%'
                        """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM audit_logs
                        WHERE event_type = 'PROPOSAL_CREATED' AND actor_user_id IS NULL
                        """, Integer.class)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/proposals")
                        .cookie(studentSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"실명 공개 제안",
                                  "content":"이름을 공개하고 작성합니다.",
                                  "authorVisibility":"NAMED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorDisplayName").value("익명 학생"));
        mockMvc.perform(post("/api/v1/proposals")
                        .cookie(teacherSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"교사 작성 시도","content":"허용되지 않음","authorVisibility":"NAMED"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void proposalFeedFiltersRejectedAndSortsByDateAndSupportInBothDirections() throws Exception {
        SeedAccount author = createActiveAccount(
                "feed.author", "피드 작성 학생", "STUDENT", "author secure passphrase");
        createActiveAccount("feed.supporter.one", "동의 학생 1", "STUDENT", "supporter one passphrase");
        createActiveAccount("feed.supporter.two", "동의 학생 2", "STUDENT", "supporter two passphrase");
        SeedAccount teacher = createActiveAccount(
                "feed.teacher", "피드 교사", "TEACHER", "teacher secure passphrase");
        Cookie authorSession = requireSessionCookie(login("feed.author", "author secure passphrase"));
        Cookie firstSupporterSession = requireSessionCookie(
                login("feed.supporter.one", "supporter one passphrase"));
        Cookie secondSupporterSession = requireSessionCookie(
                login("feed.supporter.two", "supporter two passphrase"));
        Cookie teacherSession = requireSessionCookie(login("feed.teacher", "teacher secure passphrase"));

        UUID oldest = createGatheringProposal(authorSession, "가장 오래된 제안");
        UUID middle = createGatheringProposal(authorSession, "중간에 올라온 제안");
        UUID newest = createGatheringProposal(authorSession, "가장 최근 제안");

        // 등록 시각을 명시적으로 벌려 날짜 정렬 결과가 실행마다 흔들리지 않게 한다.
        Instant base = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(3600);
        setProposalCreatedAt(oldest, base);
        setProposalCreatedAt(middle, base.plusSeconds(60));
        setProposalCreatedAt(newest, base.plusSeconds(120));

        // 작성자 자동 1표 위에 동의를 더해 동의 수를 오래된 1 · 최근 2 · 중간 3으로 만든다.
        mockMvc.perform(put("/api/v1/proposals/{publicId}/support", middle)
                .cookie(firstSupporterSession).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/proposals/{publicId}/support", middle)
                .cookie(secondSupporterSession).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/proposals/{publicId}/support", newest)
                .cookie(firstSupporterSession).with(csrf())).andExpect(status().isOk());

        assertThat(feedTitles(authorSession, "sort=LATEST"))
                .containsExactly("가장 최근 제안", "중간에 올라온 제안", "가장 오래된 제안");
        assertThat(feedTitles(authorSession, "sort=OLDEST"))
                .containsExactly("가장 오래된 제안", "중간에 올라온 제안", "가장 최근 제안");
        assertThat(feedTitles(authorSession, "sort=MOST_SUPPORTED"))
                .containsExactly("중간에 올라온 제안", "가장 최근 제안", "가장 오래된 제안");
        assertThat(feedTitles(authorSession, "sort=LEAST_SUPPORTED"))
                .containsExactly("가장 오래된 제안", "가장 최근 제안", "중간에 올라온 제안");

        // 같은 조건을 두 번 조회해도 순서가 같아야 페이지를 넘길 때 항목이 중복되거나 빠지지 않는다.
        assertThat(feedTitles(authorSession, "sort=LATEST"))
                .isEqualTo(feedTitles(authorSession, "sort=LATEST"));

        // 검색은 제목과 본문을 함께 찾으며 검색어가 어느 위치에 있어도 걸린다.
        assertThat(feedTitles(authorSession, "sort=LATEST&query=중간에"))
                .containsExactly("중간에 올라온 제안");
        assertThat(feedTitles(authorSession, "sort=LATEST&query=%"))
                .isEmpty();

        UUID rejected = createFormalProposal(author, "반려될 정식 안건");
        assignTeacherForTest(rejected, teacher);
        mockMvc.perform(post("/api/v1/proposals/{publicId}/review-start", rejected)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"반려 검증을 위한 검토 시작\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/proposals/{publicId}/decisions/reject", rejected)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"이번에는 채택하지 않습니다.","decisionReason":"예산 편성 주기가 맞지 않습니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("REJECTED"));

        assertThat(feedTitles(authorSession, "scope=REJECTED"))
                .containsExactly("반려될 정식 안건");
        assertThat(feedTitles(authorSession, "scope=FORMAL_AGENDA"))
                .containsExactly("반려될 정식 안건");
        assertThat(feedTitles(authorSession, "scope=ALL")).hasSize(4);

        // UI에 없는 값으로 요청해도 서버 오류가 아니라 검증 실패로 응답해야 한다.
        mockMvc.perform(get("/api/v1/proposals?sort=NOT_A_SORT").cookie(authorSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/proposals?scope=NOT_A_SCOPE").cookie(authorSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/proposals?size=999").cookie(authorSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/proposals?page=-1").cookie(authorSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void supportAndWithdrawalAreStudentOnlyAndIdempotentBeforeFormalization() throws Exception {
        createActiveAccount("support.creator", "제안 작성자", "STUDENT", "creator secure passphrase");
        createActiveAccount("support.student", "동의 학생", "STUDENT", "supporter secure passphrase");
        createActiveAccount("support.teacher", "동의 교사", "TEACHER", "teacher secure passphrase");
        Cookie creatorSession = requireSessionCookie(login("support.creator", "creator secure passphrase"));
        Cookie supporterSession = requireSessionCookie(login("support.student", "supporter secure passphrase"));
        Cookie teacherSession = requireSessionCookie(login("support.teacher", "teacher secure passphrase"));

        MvcResult created = mockMvc.perform(post("/api/v1/proposals")
                        .cookie(creatorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"동의 멱등성","content":"중복 동의를 검증합니다.","authorVisibility":"ANONYMOUS"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID publicId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("publicId").asText());

        for (int request = 0; request < 2; request++) {
            mockMvc.perform(put("/api/v1/proposals/{publicId}/support", publicId)
                            .cookie(supporterSession).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.supported").value(true))
                    .andExpect(jsonPath("$.supportCount").value(1));
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_supports", Integer.class)).isEqualTo(1);

        SeedAccount concurrentStudent = insertUser(
                "support.concurrent", "동시 동의 학생", "ACTIVE", "STUDENT");
        ExecutorService duplicateExecutor = Executors.newFixedThreadPool(2);
        CountDownLatch duplicateReady = new CountDownLatch(2);
        CountDownLatch duplicateStart = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<SupportResult> duplicateRequest = () -> {
                duplicateReady.countDown();
                duplicateStart.await();
                return proposalService.support(
                        studentPrincipal(concurrentStudent), publicId, "duplicate-support-trace");
            };
            Future<SupportResult> first = duplicateExecutor.submit(duplicateRequest);
            Future<SupportResult> second = duplicateExecutor.submit(duplicateRequest);
            duplicateReady.await();
            duplicateStart.countDown();
            assertThat(List.of(first.get(), second.get()))
                    .allSatisfy(result -> assertThat(result.supportCount()).isEqualTo(2));
        } finally {
            duplicateExecutor.shutdownNow();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_supports", Integer.class)).isEqualTo(2);

        for (int request = 0; request < 2; request++) {
            mockMvc.perform(delete("/api/v1/proposals/{publicId}/support", publicId)
                            .cookie(supporterSession).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.supported").value(false))
                    .andExpect(jsonPath("$.supportCount").value(1));
        }
        mockMvc.perform(put("/api/v1/proposals/{publicId}/support", publicId)
                        .cookie(teacherSession).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void proposalAuthorCanUpdateAndSoftWithdrawBeforeFormalization() throws Exception {
        createActiveAccount("edit.creator", "수정 작성자", "STUDENT", "creator secure passphrase");
        createActiveAccount("edit.other", "다른 학생", "STUDENT", "other secure passphrase");
        Cookie creatorSession = requireSessionCookie(login("edit.creator", "creator secure passphrase"));
        Cookie otherSession = requireSessionCookie(login("edit.other", "other secure passphrase"));

        MvcResult created = mockMvc.perform(post("/api/v1/proposals")
                        .cookie(creatorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정 전 제목","content":"수정 전 내용","authorVisibility":"ANONYMOUS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.viewerCanEdit").value(true))
                .andReturn();
        UUID publicId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("publicId").asText());

        mockMvc.perform(get("/api/v1/proposals/{publicId}", publicId).cookie(otherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerCanEdit").value(false));
        mockMvc.perform(put("/api/v1/proposals/{publicId}", publicId)
                        .cookie(otherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"권한 없는 수정","content":"수정되면 안 됩니다."}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/proposals/{publicId}", publicId)
                        .cookie(creatorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"수정된 제목","content":"수정된 내용입니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andExpect(jsonPath("$.content").value("수정된 내용입니다."))
                .andExpect(jsonPath("$.viewerCanEdit").value(true));

        mockMvc.perform(delete("/api/v1/proposals/{publicId}", publicId)
                        .cookie(otherSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/proposals/{publicId}", publicId)
                        .cookie(creatorSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/proposals/{publicId}", publicId).cookie(creatorSession))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposals WHERE public_id = UUID_TO_BIN(?) AND withdrawn_at IS NOT NULL",
                Integer.class, publicId.toString())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM audit_logs
                        WHERE event_type IN ('PROPOSAL_UPDATED', 'PROPOSAL_WITHDRAWN')
                          AND actor_user_id IS NULL
                        """, Integer.class)).isEqualTo(2);
    }

    @Test
    void studentsCanCommentAndOnlyDeleteTheirOwnComments() throws Exception {
        createActiveAccount("comment.creator", "댓글 제안자", "STUDENT", "creator secure passphrase");
        createActiveAccount("comment.writer", "댓글 학생", "STUDENT", "writer secure passphrase");
        createActiveAccount("comment.teacher", "댓글 교사", "TEACHER", "teacher secure passphrase");
        Cookie creatorSession = requireSessionCookie(login("comment.creator", "creator secure passphrase"));
        Cookie writerSession = requireSessionCookie(login("comment.writer", "writer secure passphrase"));
        Cookie teacherSession = requireSessionCookie(login("comment.teacher", "teacher secure passphrase"));

        MvcResult created = mockMvc.perform(post("/api/v1/proposals")
                        .cookie(creatorSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"댓글 검증 제안","content":"학생 댓글을 검증합니다.","authorVisibility":"NAMED"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        UUID proposalPublicId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("publicId").asText());

        MvcResult commentCreated = mockMvc.perform(post(
                                "/api/v1/proposals/{publicId}/comments", proposalPublicId)
                        .cookie(writerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"이 제안에 동의합니다.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorDisplayName").value("댓글 학생"))
                .andExpect(jsonPath("$.viewerCanDelete").value(true))
                .andReturn();
        UUID commentPublicId = UUID.fromString(
                objectMapper.readTree(commentCreated.getResponse().getContentAsString()).get("publicId").asText());

        mockMvc.perform(get("/api/v1/proposals/{publicId}/comments", proposalPublicId)
                        .cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("이 제안에 동의합니다."))
                .andExpect(jsonPath("$[0].viewerCanDelete").value(false));
        // 교사는 동의 모집 중인 제안과 그 댓글을 읽을 수 있지만 댓글을 달 수는 없다.
        mockMvc.perform(get("/api/v1/proposals/{publicId}/comments", proposalPublicId)
                        .cookie(teacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("이 제안에 동의합니다."))
                .andExpect(jsonPath("$[0].viewerCanDelete").value(false));
        mockMvc.perform(post("/api/v1/proposals/{publicId}/comments", proposalPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"교사 댓글 시도\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(
                                "/api/v1/proposals/{publicId}/comments/{commentPublicId}",
                                proposalPublicId, commentPublicId)
                        .cookie(creatorSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROPOSAL_COMMENT_NOT_FOUND"));
        mockMvc.perform(delete(
                                "/api/v1/proposals/{publicId}/comments/{commentPublicId}",
                                proposalPublicId, commentPublicId)
                        .cookie(writerSession).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/proposals/{publicId}/comments", proposalPublicId)
                        .cookie(creatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_comments WHERE deleted_at IS NOT NULL", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void reportsHideProposalOnlyAtThresholdAndExactlyOnceUnderConcurrency() throws Exception {
        SeedAccount author = insertUser("hide.author", "가림 작성자", "ACTIVE", "STUDENT");
        UUID publicId = proposalService.create(
                studentPrincipal(author),
                "신고 임계값 검증 제안",
                "신고가 쌓이면 일반 사용자에게 임시로 가려져야 합니다.",
                AuthorVisibility.ANONYMOUS,
                "report-threshold-create").publicId();

        // 임계값 직전까지는 공개 상태가 그대로여야 한다.
        for (int index = 0; index < 3; index++) {
            SeedAccount reporter = insertUser(
                    "hide.reporter." + index, "가림 신고자 " + index, "ACTIVE", "STUDENT");
            contentReportService.report(
                    studentPrincipal(reporter), publicId, "신고 사유 " + index, "report-threshold-trace");
        }
        assertThat(visibilityStatusOf(publicId)).isEqualTo("VISIBLE");

        // 같은 사용자가 다시 신고해도 1건으로만 계산되어 임계값을 앞당기지 않는다.
        SeedAccount repeatReporter = insertUser("hide.reporter.repeat", "중복 신고자", "ACTIVE", "STUDENT");
        contentReportService.report(
                studentPrincipal(repeatReporter), publicId, "첫 신고", "report-threshold-trace");
        contentReportService.report(
                studentPrincipal(repeatReporter), publicId, "같은 사람의 두 번째 신고", "report-threshold-trace");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM content_reports report
                        JOIN proposals proposal ON proposal.id = report.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                        """, Integer.class, publicId.toString())).isEqualTo(4);
        assertThat(visibilityStatusOf(publicId)).isEqualTo("VISIBLE");

        // 다섯 번째와 여섯 번째 신고를 동시에 보내 가림이 정확히 한 번만 적용되는지 본다.
        SeedAccount fifth = insertUser("hide.reporter.fifth", "다섯째 신고자", "ACTIVE", "STUDENT");
        SeedAccount sixth = insertUser("hide.reporter.sixth", "여섯째 신고자", "ACTIVE", "STUDENT");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return contentReportService.report(
                        studentPrincipal(fifth), publicId, "동시 신고 A", "report-threshold-concurrent-a");
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return contentReportService.report(
                        studentPrincipal(sixth), publicId, "동시 신고 B", "report-threshold-concurrent-b");
            });
            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(visibilityStatusOf(publicId)).isEqualTo("RESTRICTED");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM audit_logs
                        WHERE event_type = 'PROPOSAL_RESTRICTED_BY_REPORTS'
                          AND target_public_id = UUID_TO_BIN(?)
                        """, Integer.class, publicId.toString())).isEqualTo(1);

        // 가려진 제안은 일반 사용자의 목록과 상세에서 사라진다.
        SeedAccount reader = createActiveAccount(
                "hide.reader", "가림 확인 학생", "STUDENT", "reader secure passphrase");
        Cookie readerSession = requireSessionCookie(login("hide.reader", "reader secure passphrase"));
        mockMvc.perform(get("/api/v1/proposals/{publicId}", publicId).cookie(readerSession))
                .andExpect(status().isNotFound());
        MvcResult feed = mockMvc.perform(get("/api/v1/proposals").cookie(readerSession))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(feed.getResponse().getContentAsString()).doesNotContain("신고 임계값 검증 제안");
        // 신고 수는 일반 사용자에게 어떤 형태로도 노출되지 않는다.
        assertThat(feed.getResponse().getContentAsString()).doesNotContain("reportCount");
        assertThat(reader.publicId()).isNotNull();
    }

    private String visibilityStatusOf(UUID proposalPublicId) {
        return jdbcTemplate.queryForObject(
                "SELECT visibility_status FROM proposals WHERE public_id = UUID_TO_BIN(?)",
                String.class, proposalPublicId.toString());
    }

    @Test
    void concurrentThresholdSupportsFormalizeExactlyOnceAndOpenTeacherAccess() throws Exception {
        SeedAccount creator = insertUser("threshold.creator", "임계값 작성자", "ACTIVE", "STUDENT");
        AuthPrincipal creatorPrincipal = studentPrincipal(creator);
        UUID publicId = proposalService.create(
                creatorPrincipal,
                "50명 동시성 검증",
                "50번째 근처의 동의를 동시에 처리합니다.",
                AuthorVisibility.ANONYMOUS,
                "threshold-create-trace").publicId();

        for (int index = 0; index < 48; index++) {
            SeedAccount supporter = insertUser(
                    "threshold.student." + index, "임계값 학생 " + index, "ACTIVE", "STUDENT");
            proposalService.support(studentPrincipal(supporter), publicId, "threshold-sequential-trace");
        }
        SeedAccount fortyNinth = insertUser(
                "threshold.student.48", "임계값 학생 48", "ACTIVE", "STUDENT");
        SeedAccount fiftieth = insertUser(
                "threshold.student.49", "임계값 학생 49", "ACTIVE", "STUDENT");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<SupportResult> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return proposalService.support(studentPrincipal(fortyNinth), publicId, "threshold-concurrent-a");
            });
            Future<SupportResult> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return proposalService.support(studentPrincipal(fiftieth), publicId, "threshold-concurrent-b");
            });
            ready.await();
            start.countDown();
            List<SupportResult> results = List.of(first.get(), second.get());
            assertThat(results).filteredOn(SupportResult::justFormalized).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("""
                        SELECT workflow_status FROM proposals WHERE public_id = UUID_TO_BIN(?)
                        """, String.class, publicId.toString())).isEqualTo("FORMAL_AGENDA");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT formalized_support_count FROM proposals WHERE public_id = UUID_TO_BIN(?)
                        """, Integer.class, publicId.toString())).isEqualTo(50);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM proposal_status_history history
                        JOIN proposals proposal ON proposal.id = history.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?) AND history.to_status = 'FORMAL_AGENDA'
                        """, Integer.class, publicId.toString())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM proposal_notifications notification
                        JOIN proposals proposal ON proposal.id = notification.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                        """, Integer.class, publicId.toString())).isEqualTo(1);

        createActiveAccount("formal.teacher", "정식 안건 교사", "TEACHER", "teacher secure passphrase");
        Cookie teacherSession = requireSessionCookie(login("formal.teacher", "teacher secure passphrase"));
        mockMvc.perform(get("/api/v1/proposals/{publicId}", publicId).cookie(teacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("FORMAL_AGENDA"))
                .andExpect(jsonPath("$.formalizedSupportCount").value(50));

        assertThatThrownBy(() -> proposalService.withdrawSupport(creatorPrincipal, publicId))
                .isInstanceOf(kr.hs.gbsw.communication.proposal.exception.SupportWithdrawalClosedException.class);
    }

    @Test
    void superAdminInternallyAssignsOnlyActiveTeachersWithoutLeakingThemToStudents() throws Exception {
        SeedAccount admin = createActiveAccount(
                "proposal.admin", "제안 관리자", "SUPER_ADMIN", "admin secure passphrase");
        SeedAccount firstTeacher = createActiveAccount(
                "proposal.teacher.first", "첫 담당 교사", "TEACHER", "teacher secure passphrase");
        SeedAccount secondTeacher = createActiveAccount(
                "proposal.teacher.second", "두 번째 담당 교사", "TEACHER", "teacher secure passphrase");
        SeedAccount student = createActiveAccount(
                "proposal.assignment.student", "제안 학생", "STUDENT", "student secure passphrase");
        UUID publicId = createFormalProposal(student, "내부 담당 지정 검증");

        Cookie adminSession = requireSessionCookie(login("proposal.admin", "admin secure passphrase"));
        Cookie firstTeacherSession = requireSessionCookie(login(
                "proposal.teacher.first", "teacher secure passphrase"));
        Cookie secondTeacherSession = requireSessionCookie(login(
                "proposal.teacher.second", "teacher secure passphrase"));
        Cookie studentSession = requireSessionCookie(login(
                "proposal.assignment.student", "student secure passphrase"));

        mockMvc.perform(post("/api/v1/admin/proposals/{publicId}/assignments", publicId)
                        .cookie(firstTeacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "teacherPublicId", firstTeacher.publicId(),
                                "reason", "권한 없는 지정 시도"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/proposals/{publicId}/assignments", publicId)
                        .cookie(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "teacherPublicId", firstTeacher.publicId(),
                                "reason", "정식 안건 검토 지정"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherPublicId").value(firstTeacher.publicId().toString()))
                .andExpect(jsonPath("$.teacherDisplayName").value("첫 담당 교사"));

        mockMvc.perform(post("/api/v1/admin/proposals/{publicId}/assignments", publicId)
                        .cookie(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "teacherPublicId", student.publicId(),
                                "reason", "학생 지정 시도"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_TEACHER_NOT_ELIGIBLE"));
        MvcResult studentDetail = mockMvc.perform(get("/api/v1/proposals/{publicId}", publicId)
                        .cookie(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerCanManage").value(false))
                .andExpect(jsonPath("$.teacherPublicId").doesNotExist())
                .andReturn();
        assertThat(studentDetail.getResponse().getContentAsString())
                .doesNotContain(firstTeacher.publicId().toString(), "첫 담당 교사", "proposal.teacher.first");
        mockMvc.perform(get("/api/v1/proposals/{publicId}", publicId).cookie(firstTeacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerCanManage").value(true));
        mockMvc.perform(get("/api/v1/proposals/{publicId}", publicId).cookie(secondTeacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerCanManage").value(false));

        mockMvc.perform(post("/api/v1/admin/proposals/{publicId}/assignments", publicId)
                        .cookie(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "teacherPublicId", secondTeacher.publicId(),
                                "reason", "업무 조정으로 담당 변경"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherPublicId").value(secondTeacher.publicId().toString()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_teacher_assignments", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_teacher_assignments WHERE unassigned_at IS NULL", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM proposal_teacher_assignments
                        WHERE unassigned_at IS NOT NULL
                          AND unassigned_by_user_id IS NOT NULL
                          AND unassignment_reason = '업무 조정으로 담당 변경'
                        """, Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/admin/proposals").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].publicId").value(publicId.toString()))
                .andExpect(jsonPath("$[0].assignment.teacherDisplayName").value("두 번째 담당 교사"));
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM audit_logs
                        WHERE event_type = 'PROPOSAL_TEACHER_ASSIGNED'
                          AND actor_user_id = UUID_TO_BIN(?)
                        """, Integer.class, admin.id().toString())).isEqualTo(2);
    }

    @Test
    void assignedTeacherFollowsExplicitDecisionAndExecutionWorkflowWithPublicResponses() throws Exception {
        SeedAccount teacher = createActiveAccount(
                "workflow.teacher", "응답 교사 비공개", "TEACHER", "teacher secure passphrase");
        createActiveAccount(
                "workflow.other.teacher", "다른 교사", "TEACHER", "other teacher passphrase");
        SeedAccount student = createActiveAccount(
                "workflow.student", "진행 확인 학생", "STUDENT", "student secure passphrase");
        UUID acceptedPublicId = createFormalProposal(student, "채택부터 완료까지 검증");
        assignTeacherForTest(acceptedPublicId, teacher);

        Cookie teacherSession = requireSessionCookie(login(
                "workflow.teacher", "teacher secure passphrase"));
        Cookie otherTeacherSession = requireSessionCookie(login(
                "workflow.other.teacher", "other teacher passphrase"));
        Cookie studentSession = requireSessionCookie(login(
                "workflow.student", "student secure passphrase"));

        mockMvc.perform(post("/api/v1/proposals/{publicId}/review-start", acceptedPublicId)
                        .cookie(otherTeacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"담당자가 아닌 교사의 시도\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROPOSAL_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/proposals/{publicId}/review-start", acceptedPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"정식 검토를 시작합니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("UNDER_REVIEW"));
        mockMvc.perform(post("/api/v1/proposals/{publicId}/decisions/accept", acceptedPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":"학교는 제안의 필요성에 동의하여 채택합니다.",
                                  "decisionReason":"학생 이용 수요와 안전 기준을 확인했습니다.",
                                  "followUpPlan":"다음 달까지 세부 실행 계획을 공개합니다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("ACCEPTED"));
        mockMvc.perform(post("/api/v1/proposals/{publicId}/execution-start", acceptedPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":"실행 준비를 마치고 작업을 시작했습니다.",
                                  "decisionReason":"필요한 자원과 일정을 확보했습니다.",
                                  "followUpPlan":"주간 단위로 진행 상황을 확인합니다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/v1/proposals/{publicId}/execution-complete", acceptedPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content":"제안된 개선 작업을 완료했습니다.",
                                  "decisionReason":"현장 확인과 최종 점검을 마쳤습니다."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("COMPLETED"));
        mockMvc.perform(post("/api/v1/proposals/{publicId}/execution-complete", acceptedPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"중복 완료","decisionReason":"중복 요청"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSAL_STATE_CONFLICT"));

        MvcResult detail = mockMvc.perform(get("/api/v1/proposals/{publicId}", acceptedPublicId)
                        .cookie(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.officialResponses.length()").value(3))
                .andExpect(jsonPath("$.officialResponses[0].resultingStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.officialResponses[1].resultingStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.officialResponses[2].resultingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.viewerCanManage").value(false))
                .andReturn();
        assertThat(detail.getResponse().getContentAsString())
                .doesNotContain(teacher.publicId().toString(), "응답 교사 비공개", "workflow.teacher");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_official_responses", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM proposal_status_history history
                        JOIN proposals proposal ON proposal.id = history.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                          AND history.changed_by_user_id = UUID_TO_BIN(?)
                        """, Integer.class, acceptedPublicId.toString(), teacher.id().toString())).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM proposal_status_history history
                        JOIN proposals proposal ON proposal.id = history.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                          AND history.changed_by_user_id = UUID_TO_BIN(?)
                          AND history.support_count_snapshot IS NULL
                        """, Integer.class, acceptedPublicId.toString(), teacher.id().toString())).isZero();
        // 작성자 자동 동의를 제거했으므로 이 제안의 유효 동의는 0표다. 전이 이력은
        // 그 시점의 실제 값을 담아야 하며 승격 시 기록된 50을 되풀이해서는 안 된다.
        assertThat(jdbcTemplate.queryForList("""
                        SELECT history.support_count_snapshot FROM proposal_status_history history
                        JOIN proposals proposal ON proposal.id = history.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                          AND history.changed_by_user_id = UUID_TO_BIN(?)
                        """, Integer.class, acceptedPublicId.toString(), teacher.id().toString()))
                .containsExactly(0, 0, 0, 0);

        UUID rejectedPublicId = createFormalProposal(student, "보류 후 반려 검증");
        assignTeacherForTest(rejectedPublicId, teacher);
        mockMvc.perform(post("/api/v1/proposals/{publicId}/review-start", rejectedPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"검토 시작\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/proposals/{publicId}/decisions/hold", rejectedPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"추가 확인이 필요해 보류합니다.","decisionReason":"관련 부서 확인이 남았습니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("ON_HOLD"));
        mockMvc.perform(post("/api/v1/proposals/{publicId}/review-resume", rejectedPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"추가 확인이 끝나 검토를 재개합니다.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("UNDER_REVIEW"));
        mockMvc.perform(post("/api/v1/proposals/{publicId}/decisions/reject", rejectedPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"현재 조건에서는 실행하기 어려워 반려합니다.","decisionReason":"필수 안전 기준을 충족하지 못했습니다."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowStatus").value("REJECTED"));
        mockMvc.perform(get("/api/v1/proposals/{publicId}", rejectedPublicId).cookie(studentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officialResponses.length()").value(2))
                .andExpect(jsonPath("$.officialResponses[0].resultingStatus").value("ON_HOLD"))
                .andExpect(jsonPath("$.officialResponses[1].resultingStatus").value("REJECTED"));
    }

    @Test
    void concurrentTeacherAssignmentsKeepExactlyOneCurrentAssignee() throws Exception {
        SeedAccount admin = insertUser(
                "concurrent.assignment.admin", "동시 지정 관리자", "ACTIVE", "SUPER_ADMIN");
        SeedAccount firstTeacher = insertUser(
                "concurrent.assignment.first", "동시 지정 교사 1", "ACTIVE", "TEACHER");
        SeedAccount secondTeacher = insertUser(
                "concurrent.assignment.second", "동시 지정 교사 2", "ACTIVE", "TEACHER");
        SeedAccount student = insertUser(
                "concurrent.assignment.student", "동시 지정 학생", "ACTIVE", "STUDENT");
        UUID publicId = createFormalProposal(student, "담당 교사 동시 지정 검증");
        AuthPrincipal adminPrincipal = new AuthPrincipal(
                admin.id(), admin.publicId(), admin.loginId(), admin.loginId(),
                1, Instant.now(), List.of("ROLE_SUPER_ADMIN"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return proposalAssignmentService.assign(
                        adminPrincipal, publicId, firstTeacher.publicId(),
                        "첫 동시 지정", "concurrent-assignment-first");
            });
            Future<?> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return proposalAssignmentService.assign(
                        adminPrincipal, publicId, secondTeacher.publicId(),
                        "두 번째 동시 지정", "concurrent-assignment-second");
            });
            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_teacher_assignments", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_teacher_assignments WHERE unassigned_at IS NULL", Integer.class))
                .isEqualTo(1);
        String currentTeacher = jdbcTemplate.queryForObject("""
                        SELECT BIN_TO_UUID(assignment.teacher_user_id)
                        FROM proposal_teacher_assignments assignment
                        WHERE assignment.unassigned_at IS NULL
                        """, String.class);
        assertThat(currentTeacher).isIn(firstTeacher.id().toString(), secondTeacher.id().toString());
    }

    @Test
    void reportsUseFixedThreeReviewerCasesAndAllowRevealByThreeReviewersWithinWindow() throws Exception {
        SeedAccount author = createActiveAccount(
                "moderation.author", "익명 제안 작성자", "STUDENT", "author secure passphrase");
        SeedAccount moderationReporter = createActiveAccount(
                "moderation.reporter", "신고 학생", "STUDENT", "reporter secure passphrase");
        SeedAccount studentAffairs = createActiveAccount(
                "moderation.teacher", "학생부장", "TEACHER", "teacher secure passphrase");
        SeedAccount president = createActiveAccount(
                "moderation.president", "학생회장", "STUDENT", "president secure passphrase");
        SeedAccount vicePresident = createActiveAccount(
                "moderation.vice", "학생부회장", "STUDENT", "vice secure passphrase");
        SeedAccount moderationAdmin = createActiveAccount(
                "moderation.admin", "심의 불가 관리자", "SUPER_ADMIN", "admin secure passphrase");
        assignOfficeForTest(studentAffairs, "STUDENT_AFFAIRS_TEACHER");
        assignOfficeForTest(president, "STUDENT_COUNCIL_PRESIDENT");
        assignOfficeForTest(vicePresident, "STUDENT_COUNCIL_VICE_PRESIDENT");

        UUID proposalPublicId = proposalService.create(
                studentPrincipal(author),
                "심의 분리 검증 제안",
                "신고만으로 숨김이나 신원 공개가 일어나지 않아야 합니다.",
                AuthorVisibility.ANONYMOUS,
                "moderation-proposal-trace").publicId();
        Cookie reporterSession = requireSessionCookie(login(
                "moderation.reporter", "reporter secure passphrase"));
        Cookie teacherSession = requireSessionCookie(login(
                "moderation.teacher", "teacher secure passphrase"));
        Cookie presidentSession = requireSessionCookie(login(
                "moderation.president", "president secure passphrase"));
        Cookie viceSession = requireSessionCookie(login(
                "moderation.vice", "vice secure passphrase"));
        Cookie adminSession = requireSessionCookie(login(
                "moderation.admin", "admin secure passphrase"));

        MvcResult firstReport = mockMvc.perform(post(
                                "/api/v1/proposals/{publicId}/reports", proposalPublicId)
                        .cookie(reporterSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"개인정보 포함 여부 확인 요청\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").isString())
                .andReturn();
        UUID reportPublicId = UUID.fromString(objectMapper.readTree(
                firstReport.getResponse().getContentAsString()).get("publicId").asText());
        mockMvc.perform(post("/api/v1/proposals/{publicId}/reports", proposalPublicId)
                        .cookie(reporterSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"중복 신고 요청\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(reportPublicId.toString()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM content_reports", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT visibility_status FROM proposals WHERE public_id = UUID_TO_BIN(?)",
                String.class, proposalPublicId.toString())).isEqualTo("VISIBLE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_reveal_records", Integer.class)).isZero();

        mockMvc.perform(get("/api/v1/moderation/reports").cookie(teacherSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].proposalPublicId").value(proposalPublicId.toString()))
                .andExpect(jsonPath("$[0].proposalTitle").value("심의 분리 검증 제안"))
                .andExpect(jsonPath("$[0].existingCaseTypes").isEmpty());
        mockMvc.perform(post("/api/v1/moderation/reports/{reportPublicId}/cases", reportPublicId)
                        .cookie(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseType\":\"CONTENT_VISIBILITY\",\"reason\":\"권한 없는 생성\"}"))
                .andExpect(status().isForbidden());

        MvcResult visibilityCaseResult = mockMvc.perform(post(
                                "/api/v1/moderation/reports/{reportPublicId}/cases", reportPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseType":"CONTENT_VISIBILITY","reason":"공개 제한 필요성 분리 심의"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseType").value("CONTENT_VISIBILITY"))
                .andExpect(jsonPath("$.caseStatus").value("PENDING"))
                .andReturn();
        UUID visibilityCaseId = UUID.fromString(objectMapper.readTree(
                visibilityCaseResult.getResponse().getContentAsString()).get("publicId").asText());

        MvcResult identityCaseResult = mockMvc.perform(post(
                                "/api/v1/moderation/reports/{reportPublicId}/cases", reportPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"caseType":"IDENTITY_REVEAL","reason":"작성자 확인 필요성 분리 심의"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseType").value("IDENTITY_REVEAL"))
                .andReturn();
        UUID identityCaseId = UUID.fromString(objectMapper.readTree(
                identityCaseResult.getResponse().getContentAsString()).get("publicId").asText());
        assertThat(identityCaseResult.getResponse().getContentAsString())
                .doesNotContain(author.loginId(), author.publicId().toString(), "익명 제안 작성자");

        mockMvc.perform(post("/api/v1/moderation/reports/{reportPublicId}/cases", reportPublicId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"caseType\":\"CONTENT_VISIBILITY\",\"reason\":\"중복 사건\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODERATION_CASE_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM moderation_reviewer_snapshots", Integer.class)).isEqualTo(6);

        vote(teacherSession, visibilityCaseId, "approve", "공개 제한에 동의합니다.", "PENDING");
        vote(presidentSession, visibilityCaseId, "approve", "학생회장 승인입니다.", "PENDING");
        vote(viceSession, visibilityCaseId, "approve", "학생부회장 승인입니다.", "APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT visibility_status FROM proposals WHERE public_id = UUID_TO_BIN(?)",
                String.class, proposalPublicId.toString())).isEqualTo("HIDDEN_BY_DECISION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM proposal_visibility_history", Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/proposals/{publicId}", proposalPublicId).cookie(reporterSession))
                .andExpect(status().isNotFound());

        vote(teacherSession, identityCaseId, "approve", "신원 확인에 동의합니다.", "PENDING");
        vote(presidentSession, identityCaseId, "approve", "학생회장 승인입니다.", "PENDING");
        vote(viceSession, identityCaseId, "approve", "학생부회장 승인입니다.", "APPROVED");
        // 슈퍼 어드민은 학생·교사 역할이 없어 인가 단계에서 막힌다.
        mockMvc.perform(post("/api/v1/identity-reveal-cases/{publicId}/reveal", identityCaseId)
                        .cookie(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"관리자 확인 시도\"}"))
                .andExpect(status().isForbidden());
        // 신고자는 학생이지만 이 사건의 고정 심의자가 아니므로 사건 자체를 찾을 수 없다.
        mockMvc.perform(post("/api/v1/identity-reveal-cases/{publicId}/reveal", identityCaseId)
                        .cookie(reporterSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"심의자가 아닌 학생의 시도\"}"))
                .andExpect(status().isNotFound());

        // 고정 심의자 세 명 모두 열람할 수 있다. 학생회장과 부회장은 학생 역할이다.
        for (Cookie reviewerSession : List.of(teacherSession, presidentSession, viceSession)) {
            mockMvc.perform(post("/api/v1/identity-reveal-cases/{publicId}/reveal", identityCaseId)
                            .cookie(reviewerSession).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"승인 결과에 따른 신원 확인\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.loginId").value(author.loginId()))
                    .andExpect(jsonPath("$.displayName").value("익명 제안 작성자"));
        }
        // 같은 사람이 기간 안에 다시 확인할 수 있고 그때마다 기록이 남는다.
        mockMvc.perform(post("/api/v1/identity-reveal-cases/{publicId}/reveal", identityCaseId)
                        .cookie(teacherSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"기간 내 재확인\"}"))
                .andExpect(status().isOk());

        MvcResult caseAfterReveal = mockMvc.perform(
                        get("/api/v1/moderation/cases/{publicId}", identityCaseId)
                                .cookie(presidentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityRevealed").value(true))
                .andReturn();
        assertThat(caseAfterReveal.getResponse().getContentAsString())
                .doesNotContain(author.loginId(), author.publicId().toString(), "익명 제안 작성자");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_reveal_records", Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM security_throttle_states
                        WHERE throttle_scope = 'IDENTITY_REVEAL_ACCOUNT'
                          AND failure_count = 1
                        """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(DISTINCT revealed_by_user_id) FROM identity_reveal_records
                        """, Integer.class)).isEqualTo(3);
        // 열람 기록에 평문 신원을 남기지 않는다.
        assertThat(jdbcTemplate.queryForList(
                "SELECT reason FROM identity_reveal_records", String.class))
                .noneMatch(reason -> reason.contains(author.loginId()));
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM audit_logs
                        WHERE event_type = 'PROPOSAL_IDENTITY_REVEALED'
                          AND details_json IS NULL
                        """, Integer.class)).isEqualTo(4);
        // 심의자가 아닌 계정의 시도는 열람 기록을 남기지 않는다.
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM identity_reveal_records
                        WHERE revealed_by_user_id IN (UUID_TO_BIN(?), UUID_TO_BIN(?))
                        """, Integer.class, moderationAdmin.id().toString(), moderationReporter.id().toString()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM audit_logs
                        WHERE event_type = 'PROPOSAL_IDENTITY_REVEAL_FAILED'
                          AND actor_user_id = UUID_TO_BIN(?)
                        """, Integer.class, moderationReporter.id().toString())).isEqualTo(1);

        // 승인 시점이 열람 기간보다 오래되면 세 사람 모두 더는 확인할 수 없다.
        jdbcTemplate.update("""
                        UPDATE moderation_cases SET decided_at = ?
                        WHERE public_id = UUID_TO_BIN(?)
                        """,
                Timestamp.from(Instant.now().minus(Duration.ofHours(25))), identityCaseId.toString());
        for (Cookie reviewerSession : List.of(teacherSession, presidentSession, viceSession)) {
            mockMvc.perform(post("/api/v1/identity-reveal-cases/{publicId}/reveal", identityCaseId)
                            .cookie(reviewerSession).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"만료 후 확인 시도\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDENTITY_REVEAL_UNAVAILABLE"));
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_reveal_records", Integer.class)).isEqualTo(4);
    }

    @Test
    void moderationReviewerSnapshotSurvivesLaterOfficeChanges() {
        SeedAccount author = insertUser(
                "snapshot.author", "스냅샷 작성자", "ACTIVE", "STUDENT");
        SeedAccount reporter = insertUser(
                "snapshot.reporter", "스냅샷 신고자", "ACTIVE", "STUDENT");
        SeedAccount studentAffairs = insertUser(
                "snapshot.teacher", "스냅샷 학생부장", "ACTIVE", "TEACHER");
        SeedAccount president = insertUser(
                "snapshot.president", "스냅샷 학생회장", "ACTIVE", "STUDENT");
        SeedAccount vicePresident = insertUser(
                "snapshot.vice", "스냅샷 학생부회장", "ACTIVE", "STUDENT");
        assignOfficeForTest(studentAffairs, "STUDENT_AFFAIRS_TEACHER");
        assignOfficeForTest(president, "STUDENT_COUNCIL_PRESIDENT");
        assignOfficeForTest(vicePresident, "STUDENT_COUNCIL_VICE_PRESIDENT");

        UUID proposalPublicId = proposalService.create(
                studentPrincipal(author), "보직 변경 후 심의", "사건 생성 시점 심의자 고정 검증",
                AuthorVisibility.ANONYMOUS, "snapshot-proposal").publicId();
        UUID reportPublicId = contentReportService.report(
                studentPrincipal(reporter), proposalPublicId, "스냅샷 심의 요청", "snapshot-report")
                .receipt().publicId();
        AuthPrincipal creator = principal(
                studentAffairs, "ROLE_TEACHER", "OFFICE_STUDENT_AFFAIRS_TEACHER");
        UUID casePublicId = moderationCaseService.create(
                creator, reportPublicId, ModerationCaseType.CONTENT_VISIBILITY,
                "보직 변경 전 사건 생성", "snapshot-case").publicId();

        jdbcTemplate.update("""
                        UPDATE office_assignments
                        SET ends_at = ?
                        WHERE office_type IN (
                            'STUDENT_AFFAIRS_TEACHER',
                            'STUDENT_COUNCIL_PRESIDENT',
                            'STUDENT_COUNCIL_VICE_PRESIDENT'
                        ) AND ends_at IS NULL
                        """, Timestamp.from(Instant.now()));

        AuthPrincipal formerStudentAffairs = principal(studentAffairs, "ROLE_TEACHER");
        AuthPrincipal formerPresident = principal(president, "ROLE_STUDENT");
        AuthPrincipal formerVicePresident = principal(vicePresident, "ROLE_STUDENT");
        assertThat(moderationCaseService.caseDetail(formerPresident, casePublicId).viewerOffice().name())
                .isEqualTo("STUDENT_COUNCIL_PRESIDENT");
        assertThat(moderationVoteService.vote(
                formerStudentAffairs, casePublicId, ModerationVoteDecision.APPROVE,
                "기존 학생부장 승인", "snapshot-vote-teacher")).isEqualTo(ModerationCaseStatus.PENDING);
        assertThat(moderationVoteService.vote(
                formerPresident, casePublicId, ModerationVoteDecision.APPROVE,
                "기존 학생회장 승인", "snapshot-vote-president")).isEqualTo(ModerationCaseStatus.PENDING);
        assertThat(moderationVoteService.vote(
                formerVicePresident, casePublicId, ModerationVoteDecision.APPROVE,
                "기존 학생부회장 승인", "snapshot-vote-vice")).isEqualTo(ModerationCaseStatus.APPROVED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT visibility_status FROM proposals WHERE public_id = UUID_TO_BIN(?)",
                String.class, proposalPublicId.toString())).isEqualTo("HIDDEN_BY_DECISION");
    }

    @Test
    void bootstrapCreatesExactlyOnePendingSuperAdminWithoutStoringRawCode() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String rawCode = "7Kp9zT4Wh3NmQ6Rx";

        UUID publicId = superAdminBootstrapService.bootstrap(
                "initial.admin",
                "최초 관리자",
                rawCode,
                now.plusSeconds(3600),
                now,
                "bootstrap-test-trace");

        assertThat(jdbcTemplate.queryForObject("""
                        SELECT account_status FROM users WHERE public_id = UUID_TO_BIN(?)
                        """, String.class, publicId.toString()))
                .isEqualTo("PENDING_ACTIVATION");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM role_assignments r
                        JOIN users u ON u.id = r.user_id
                        WHERE u.public_id = UUID_TO_BIN(?) AND r.role_type = 'SUPER_ADMIN'
                        """, Integer.class, publicId.toString()))
                .isEqualTo(1);
        String storedCode = jdbcTemplate.queryForObject("""
                        SELECT ac.code_hash FROM activation_codes ac
                        JOIN users u ON u.id = ac.user_id
                        WHERE u.public_id = UUID_TO_BIN(?)
                        """, String.class, publicId.toString());
        assertThat(storedCode).startsWith("$argon2id$").doesNotContain(rawCode);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bootstrap_markers", Integer.class)).isEqualTo(1);

        assertThatThrownBy(() -> superAdminBootstrapService.bootstrap(
                "second.admin",
                "두 번째 관리자",
                "G8vY4mN7qR2xP9Ts",
                now.plusSeconds(3600),
                now,
                "bootstrap-second-trace"))
                .isInstanceOf(BootstrapAlreadyCompletedException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
    }

    @Test
    void reauthenticationAndAccountSuspensionEnforceRecentAdminAndInvalidateSessions() throws Exception {
        createActiveAccount("lifecycle.admin", "계정 관리자", "SUPER_ADMIN", "admin secure passphrase");
        SeedAccount student = createActiveAccount(
                "suspended.student", "정지 학생", "STUDENT", "student secure passphrase");
        Cookie adminSession = requireSessionCookie(login("lifecycle.admin", "admin secure passphrase"));
        Cookie studentSession = requireSessionCookie(login("suspended.student", "student secure passphrase"));

        mockMvc.perform(post("/api/v1/auth/reauthenticate")
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong admin password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
        mockMvc.perform(post("/api/v1/auth/reauthenticate")
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"admin secure passphrase\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reauthenticatedAt").isString())
                .andExpect(jsonPath("$.reauthenticationExpiresAt").isString());

        mockMvc.perform(post("/api/v1/admin/users/{publicId}/suspensions", student.publicId())
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"계정 도용 신고 확인\"}"))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT account_status FROM users WHERE id = UUID_TO_BIN(?)",
                String.class,
                student.id().toString())).isEqualTo("SUSPENDED");
        mockMvc.perform(get("/api/v1/auth/me").cookie(studentSession))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.20");
                            return request;
                        })
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"suspended.student\",\"password\":\"student secure passphrase\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT JSON_UNQUOTE(JSON_EXTRACT(details_json, '$.reason'))
                        FROM audit_logs WHERE event_type = 'ADMIN_ACCOUNT_SUSPENDED'
                        """, String.class)).isEqualTo("계정 도용 신고 확인");

        mockMvc.perform(post("/api/v1/admin/users/{publicId}/reactivations", student.publicId())
                        .cookie(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"본인 확인 완료\"}"))
                .andExpect(status().isNoContent());
        login("suspended.student", "student secure passphrase");
    }

    @Test
    void roleAndOfficeLifecyclesRejectOverlapAndSupportFutureReplacement() throws Exception {
        createActiveAccount("office.requester", "보직 요청 관리자", "SUPER_ADMIN", "requester secure passphrase");
        createActiveAccount("office.approver", "보직 승인 관리자", "SUPER_ADMIN", "approver secure passphrase");
        SeedAccount firstTeacher = createActiveAccount(
                "first.teacher", "현 학생부장", "TEACHER", "first teacher passphrase");
        SeedAccount nextTeacher = createActiveAccount(
                "next.teacher", "후임 학생부장", "TEACHER", "next teacher passphrase");
        Cookie requesterSession = requireSessionCookie(login("office.requester", "requester secure passphrase"));
        Cookie approverSession = requireSessionCookie(login("office.approver", "approver secure passphrase"));
        Cookie firstTeacherSession = requireSessionCookie(login("first.teacher", "first teacher passphrase"));

        mockMvc.perform(post("/api/v1/admin/users/{publicId}/roles", firstTeacher.publicId())
                        .cookie(requesterSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TEACHER\",\"reason\":\"중복 역할 시도\"}"))
                .andExpect(status().isForbidden());

        UUID duplicateRoleRequest = requestGovernedChange(requesterSession, Map.of(
                "changeType", "ASSIGN_ROLE",
                "targetUserPublicId", firstTeacher.publicId(),
                "role", "TEACHER",
                "reason", "중복 역할 시도"));
        mockMvc.perform(post("/api/v1/admin/governance/requests/{publicId}/approve", duplicateRoleRequest)
                        .cookie(approverSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"중복 역할 거절 확인\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_CONFLICT"));

        UUID firstAppointmentRequest = requestGovernedChange(requesterSession, Map.of(
                "changeType", "APPOINT_OFFICE",
                "targetUserPublicId", firstTeacher.publicId(),
                "office", "STUDENT_AFFAIRS_TEACHER",
                "replaceExistingAtStart", false,
                "reason", "현 학생부장 임명"));
        approveGovernedChange(approverSession, firstAppointmentRequest);
        mockMvc.perform(get("/api/v1/auth/me").cookie(firstTeacherSession))
                .andExpect(status().isUnauthorized());

        Instant replacementStartsAt = Instant.now().plusSeconds(86_400).truncatedTo(ChronoUnit.MICROS);
        UUID replacementRequest = requestGovernedChange(requesterSession, Map.of(
                "changeType", "APPOINT_OFFICE",
                "targetUserPublicId", nextTeacher.publicId(),
                "office", "STUDENT_AFFAIRS_TEACHER",
                "startsAt", replacementStartsAt,
                "replaceExistingAtStart", true,
                "reason", "다음 학기 후임 예약"));
        approveGovernedChange(approverSession, replacementRequest);

        Timestamp firstEndsAt = jdbcTemplate.queryForObject("""
                        SELECT ends_at FROM office_assignments
                        WHERE user_id = UUID_TO_BIN(?) AND office_type = 'STUDENT_AFFAIRS_TEACHER'
                        """, Timestamp.class, firstTeacher.id().toString());
        assertThat(firstEndsAt).isNotNull();
        assertThat(firstEndsAt.toInstant()).isEqualTo(replacementStartsAt);
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM office_assignments
                        WHERE office_type = 'STUDENT_AFFAIRS_TEACHER'
                        """, Integer.class)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/admin/users/{publicId}", firstTeacher.publicId())
                        .cookie(requesterSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0].role").value("TEACHER"))
                .andExpect(jsonPath("$.offices[0].office").value("STUDENT_AFFAIRS_TEACHER"))
                .andExpect(jsonPath("$.offices[0].endReason").value("다음 학기 후임 예약"));

        UUID endRoleRequest = requestGovernedChange(requesterSession, Map.of(
                "changeType", "END_ROLE",
                "targetUserPublicId", firstTeacher.publicId(),
                "role", "TEACHER",
                "reason", "역할 선종료 시도"));
        mockMvc.perform(post("/api/v1/admin/governance/requests/{publicId}/approve", endRoleRequest)
                        .cookie(approverSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"보직 유지 중 역할 종료 거절 확인\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_CONFLICT"));

        SeedAccount student = createActiveAccount(
                "office.student", "일반 학생", "STUDENT", "student office passphrase");
        UUID invalidOfficeRequest = requestGovernedChange(requesterSession, Map.of(
                "changeType", "APPOINT_OFFICE",
                "targetUserPublicId", student.publicId(),
                "office", "STUDENT_AFFAIRS_TEACHER",
                "startsAt", replacementStartsAt.plusSeconds(86_400),
                "replaceExistingAtStart", true,
                "reason", "역할 불일치 시도"));
        mockMvc.perform(post("/api/v1/admin/governance/requests/{publicId}/approve", invalidOfficeRequest)
                        .cookie(approverSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"역할 불일치 거절 확인\"}"))
                .andExpect(status().isConflict());
    }

    private UUID requestGovernedChange(Cookie requesterSession, Map<String, Object> request) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/admin/governance/requests")
                        .cookie(requesterSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn();
        return UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).get("publicId").asText());
    }

    private MvcResult approveGovernedChange(Cookie approverSession, UUID requestPublicId) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/governance/requests/{publicId}/approve", requestPublicId)
                        .cookie(approverSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"독립 승인 완료\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn();
    }

    private MvcResult login(String loginId, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(loginId, password))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    private Cookie requireSessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("login response SESSION cookie").isNotNull();
        return cookie;
    }

    private SeedAccount createPendingAccount(
            String loginId,
            String displayName,
            String role,
            String activationCode,
            Instant expiresAt
    ) {
        SeedAccount account = insertUser(loginId, displayName, "PENDING_ACTIVATION", role);
        jdbcTemplate.update("""
                        INSERT INTO activation_codes (
                            id, user_id, code_hash, expires_at, created_by_user_id, created_at
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?)
                        """,
                UUID.randomUUID().toString(),
                account.id().toString(),
                passwordEncoder.encode(activationCode),
                Timestamp.from(expiresAt),
                account.id().toString(),
                Timestamp.from(Instant.now()));
        return account;
    }

    private SeedAccount createActiveAccount(String loginId, String displayName, String role, String password) {
        SeedAccount account = insertUser(loginId, displayName, "ACTIVE", role);
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO credentials (
                            user_id, password_hash, credential_version, password_changed_at, created_at, updated_at
                        ) VALUES (UUID_TO_BIN(?), ?, 1, ?, ?, ?)
                        """,
                account.id().toString(),
                passwordEncoder.encode(password),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        return account;
    }

    private SeedAccount insertUser(String loginId, String displayName, String status, String role) {
        UUID id = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                        INSERT INTO users (
                            id, public_id, login_id, display_name, account_status, activated_at, created_at, updated_at
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?)
                        """,
                id.toString(), publicId.toString(), loginId, displayName, status,
                "ACTIVE".equals(status) ? Timestamp.from(now) : null,
                Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                        INSERT INTO role_assignments (
                            id, user_id, role_type, starts_at, assigned_by_user_id, assigned_at, reason
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, NULL, ?, '통합 테스트')
                        """,
                UUID.randomUUID().toString(), id.toString(), role,
                Timestamp.from(now.minusSeconds(1)), Timestamp.from(now));
        return new SeedAccount(id, publicId, loginId);
    }

    private UUID createGatheringProposal(Cookie session, String title) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/proposals")
                        .cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "content", "제안 목록의 검색·필터·정렬을 검증하기 위한 제안 본문입니다.",
                                "authorVisibility", "NAMED"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(created.getResponse().getContentAsString());
        return UUID.fromString(body.get("publicId").asText());
    }

    private void setProposalCreatedAt(UUID proposalPublicId, Instant createdAt) {
        jdbcTemplate.update(
                "UPDATE proposals SET created_at = ? WHERE public_id = UUID_TO_BIN(?)",
                Timestamp.from(createdAt), proposalPublicId.toString());
    }

    private List<String> feedTitles(Cookie session, String queryString) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/proposals?" + queryString).cookie(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        return StreamSupport.stream(items.spliterator(), false)
                .map(item -> item.get("title").asText())
                .toList();
    }

    private UUID createFormalProposal(SeedAccount creator, String title) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID publicId = proposalService.create(
                studentPrincipal(creator),
                title,
                "정식 안건 처리 흐름을 검증하기 위한 제안 본문입니다.",
                AuthorVisibility.ANONYMOUS,
                "formal-proposal-test-trace").publicId();
        jdbcTemplate.update("""
                        UPDATE proposals
                        SET workflow_status = 'FORMAL_AGENDA',
                            formalized_at = ?, formalized_support_count = 50, updated_at = ?
                        WHERE public_id = UUID_TO_BIN(?)
                        """,
                Timestamp.from(now), Timestamp.from(now), publicId.toString());
        jdbcTemplate.update("""
                        INSERT INTO proposal_status_history (
                            proposal_id, from_status, to_status, changed_by_user_id,
                            support_count_snapshot, reason, created_at
                        )
                        SELECT id, 'GATHERING_SUPPORT', 'FORMAL_AGENDA', NULL,
                               50, '통합 테스트 정식 안건 승격', ?
                        FROM proposals WHERE public_id = UUID_TO_BIN(?)
                        """,
                Timestamp.from(now), publicId.toString());
        return publicId;
    }

    private void assignTeacherForTest(UUID proposalPublicId, SeedAccount teacher) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbcTemplate.update("""
                        INSERT INTO proposal_teacher_assignments (
                            id, proposal_id, teacher_user_id, assigned_by_user_id,
                            assignment_reason, assigned_at
                        )
                        SELECT UUID_TO_BIN(?), proposal.id, UUID_TO_BIN(?), UUID_TO_BIN(?),
                               '통합 테스트 담당 지정', ?
                        FROM proposals proposal WHERE proposal.public_id = UUID_TO_BIN(?)
                        """,
                UUID.randomUUID().toString(), teacher.id().toString(), teacher.id().toString(),
                Timestamp.from(now), proposalPublicId.toString());
    }

    private void assignOfficeForTest(SeedAccount account, String office) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbcTemplate.update("""
                        INSERT INTO office_assignments (
                            id, user_id, office_type, starts_at,
                            assigned_by_user_id, assigned_at, reason
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?,
                            UUID_TO_BIN(?), ?, '통합 테스트 보직 지정'
                        )
                        """,
                UUID.randomUUID().toString(), account.id().toString(), office,
                Timestamp.from(now.minusSeconds(1)), account.id().toString(), Timestamp.from(now));
    }

    private void vote(
            Cookie session,
            UUID casePublicId,
            String decision,
            String reason,
            String expectedStatus
    ) throws Exception {
        mockMvc.perform(post("/api/v1/moderation/cases/{publicId}/votes/{decision}",
                                casePublicId, decision)
                        .cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", reason))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseStatus").value(expectedStatus));
    }

    private void insertPasswordResetCode(SeedAccount account, String resetCode, Instant expiresAt) {
        jdbcTemplate.update("""
                        INSERT INTO password_reset_tokens (
                            id, user_id, token_hash, expires_at, created_by_user_id, created_at
                        ) VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, UUID_TO_BIN(?), ?)
                        """,
                UUID.randomUUID().toString(),
                account.id().toString(),
                passwordEncoder.encode(resetCode),
                Timestamp.from(expiresAt),
                account.id().toString(),
                Timestamp.from(Instant.now()));
    }

    private AuthPrincipal studentPrincipal(SeedAccount account) {
        return principal(account, "ROLE_STUDENT");
    }

    private AuthPrincipal principal(SeedAccount account, String... authorities) {
        return new AuthPrincipal(
                account.id(), account.publicId(), account.loginId(), account.loginId(),
                1, Instant.now(), List.of(authorities));
    }

    private byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }

    private record LoginPayload(String loginId, String password) {
    }

    private record SeedAccount(UUID id, UUID publicId, String loginId) {
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
