package kr.hs.gbsw.communication.proposal.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.config.ProposalOwnershipProperties;
import kr.hs.gbsw.communication.proposal.repository.ProposalOwnershipRepository;
import kr.hs.gbsw.communication.proposal.repository.ProposalOwnershipRepository.StoredOwnershipTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProposalOwnershipTagServiceTest {

    @Mock
    private ProposalOwnershipRepository repository;

    private ProposalOwnershipTagService service;

    @BeforeEach
    void setUp() {
        ProposalOwnershipProperties properties = new ProposalOwnershipProperties();
        properties.setKeyVersion(7);
        properties.setKeyBase64(encodedKey((byte) 7));
        properties.afterPropertiesSet();
        service = new ProposalOwnershipTagService(properties, repository);
    }

    @Test
    void tagsAreProposalSpecificAndMatchOnlyTheSameUser() {
        UUID userId = UUID.randomUUID();
        UUID firstProposalId = UUID.randomUUID();
        UUID secondProposalId = UUID.randomUUID();
        UUID firstPublicId = UUID.randomUUID();
        UUID secondPublicId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        List<byte[]> values = new ArrayList<>();
        doAnswer(invocation -> {
            values.add(invocation.<byte[]>getArgument(2).clone());
            return null;
        }).when(repository).insert(any(UUID.class), eq(7), any(byte[].class), eq(now));

        service.store(firstProposalId, firstPublicId, userId, now);
        service.store(secondProposalId, secondPublicId, userId, now);

        verify(repository, times(2)).insert(any(UUID.class), eq(7), any(byte[].class), eq(now));
        assertNotEquals(Base64.getEncoder().encodeToString(values.get(0)),
                Base64.getEncoder().encodeToString(values.get(1)));
        when(repository.findByProposalPublicId(firstPublicId))
                .thenReturn(Optional.of(new StoredOwnershipTag(7, values.get(0))));

        assertTrue(service.matches(firstPublicId, userId));
        assertFalse(service.matches(firstPublicId, UUID.randomUUID()));
    }

    @Test
    void missingTagReturnsFalse() {
        UUID proposalPublicId = UUID.randomUUID();
        when(repository.findByProposalPublicId(proposalPublicId)).thenReturn(Optional.empty());

        assertFalse(service.matches(proposalPublicId, UUID.randomUUID()));
    }

    @Test
    void missingHistoricalTagKeyReturnsFalse() {
        UUID proposalPublicId = UUID.randomUUID();
        when(repository.findByProposalPublicId(proposalPublicId))
                .thenReturn(Optional.of(new StoredOwnershipTag(99, new byte[32])));

        assertFalse(service.matches(proposalPublicId, UUID.randomUUID()));
    }

    private String encodedKey(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return Base64.getEncoder().encodeToString(key);
    }
}
