package kr.hs.gbsw.communication.proposal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.hs.gbsw.communication.audit.repository.AuditLogRepository;
import kr.hs.gbsw.communication.auth.domain.AuthPrincipal;
import kr.hs.gbsw.communication.proposal.config.ProposalProperties;
import kr.hs.gbsw.communication.proposal.domain.AuthorVisibility;
import kr.hs.gbsw.communication.proposal.domain.ProposalViewRecord;
import kr.hs.gbsw.communication.proposal.domain.ProposalVisibilityStatus;
import kr.hs.gbsw.communication.proposal.domain.ProposalWorkflowStatus;
import kr.hs.gbsw.communication.proposal.dto.response.ProposalDetailResponse;
import kr.hs.gbsw.communication.proposal.repository.ProposalRepository;
import kr.hs.gbsw.communication.proposal.repository.ProposalWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProposalServiceIdentityIsolationTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Mock
    private ProposalRepository repository;
    @Mock
    private ProposalIdentityVaultService identityVaultService;
    @Mock
    private ProposalOwnershipTagService ownershipTagService;
    @Mock
    private ProposalWorkflowRepository workflowRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private ProposalService service;

    @BeforeEach
    void setUp() {
        service = new ProposalService(
                repository,
                identityVaultService,
                ownershipTagService,
                new ProposalProperties(50, 3),
                workflowRepository,
                auditLogRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void missingOwnershipTagDoesNotBreakOrdinaryDetail() {
        UUID userId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        AuthPrincipal viewer = student(userId);
        ProposalViewRecord proposal = new ProposalViewRecord(
                publicId,
                "title",
                "content",
                AuthorVisibility.ANONYMOUS,
                null,
                ProposalWorkflowStatus.GATHERING_SUPPORT,
                ProposalVisibilityStatus.VISIBLE,
                0,
                false,
                null,
                null,
                NOW);
        when(repository.findDetail(publicId, userId, NOW)).thenReturn(Optional.of(proposal));
        when(repository.findStatusHistory(publicId)).thenReturn(List.of());
        when(workflowRepository.findOfficialResponses(publicId)).thenReturn(List.of());
        when(ownershipTagService.matches(publicId, userId)).thenReturn(false);

        ProposalDetailResponse response = service.get(viewer, publicId);

        assertFalse(response.viewerCanEdit());
    }

    @Test
    void createStartsAtZeroWithoutAutomaticAuthorSupport() {
        AuthPrincipal actor = student(UUID.randomUUID());
        when(repository.isActiveStudent(eq(actor.userId()), any(Instant.class))).thenReturn(true);

        ProposalDetailResponse response = service.create(
                actor,
                " title ",
                " content ",
                AuthorVisibility.ANONYMOUS,
                "trace");

        assertEquals(0, response.supportCount());
        assertFalse(response.viewerSupported());
        verify(repository, never()).insertSupport(any(UUID.class), any(UUID.class), any(Instant.class));
        verify(identityVaultService).storeIdentity(
                any(UUID.class), any(UUID.class), eq(actor.userId()), eq(NOW));
        verify(ownershipTagService).store(
                any(UUID.class), any(UUID.class), eq(actor.userId()), eq(NOW));
    }

    private AuthPrincipal student(UUID userId) {
        return new AuthPrincipal(
                userId,
                UUID.randomUUID(),
                "student",
                "Student",
                1,
                NOW,
                List.of("ROLE_STUDENT"));
    }
}
