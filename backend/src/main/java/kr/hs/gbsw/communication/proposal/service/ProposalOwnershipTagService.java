package kr.hs.gbsw.communication.proposal.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kr.hs.gbsw.communication.proposal.config.ProposalOwnershipProperties;
import kr.hs.gbsw.communication.proposal.repository.ProposalOwnershipRepository;
import kr.hs.gbsw.communication.proposal.repository.ProposalOwnershipRepository.StoredOwnershipTag;
import org.springframework.stereotype.Service;

@Service
public class ProposalOwnershipTagService {

    private static final byte[] DOMAIN = "gbsw:proposal-owner:v1"
            .getBytes(StandardCharsets.US_ASCII);

    private final ProposalOwnershipProperties properties;
    private final ProposalOwnershipRepository repository;

    public ProposalOwnershipTagService(
            ProposalOwnershipProperties properties,
            ProposalOwnershipRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
    }

    public void store(UUID proposalId, UUID proposalPublicId, UUID userId, Instant now) {
        int keyVersion = properties.activeKeyVersion();
        byte[] key = properties.activeKey();
        byte[] tag = calculateTag(key, proposalPublicId, userId);
        try {
            repository.insert(proposalId, keyVersion, tag, now);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(tag, (byte) 0);
        }
    }

    public boolean storeIfAbsent(UUID proposalId, UUID proposalPublicId, UUID userId, Instant now) {
        int keyVersion = properties.activeKeyVersion();
        byte[] key = properties.activeKey();
        byte[] tag = calculateTag(key, proposalPublicId, userId);
        try {
            return repository.insertIfAbsent(proposalId, keyVersion, tag, now);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(tag, (byte) 0);
        }
    }

    public boolean matches(UUID proposalPublicId, UUID userId) {
        Optional<StoredOwnershipTag> stored = repository.findByProposalPublicId(proposalPublicId);
        if (stored.isEmpty()) {
            return false;
        }
        Optional<byte[]> configuredKey = properties.keyForVersion(stored.get().keyVersion());
        if (configuredKey.isEmpty()) {
            return false;
        }

        byte[] key = configuredKey.get();
        byte[] expected = calculateTag(key, proposalPublicId, userId);
        byte[] actual = stored.get().tag();
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(expected, (byte) 0);
            Arrays.fill(actual, (byte) 0);
        }
    }

    private byte[] calculateTag(byte[] key, UUID proposalPublicId, UUID userId) {
        byte[] proposalBytes = uuidBytes(proposalPublicId);
        byte[] userBytes = uuidBytes(userId);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(DOMAIN);
            mac.update(proposalBytes);
            return mac.doFinal(userBytes);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Proposal ownership tag calculation failed", exception);
        } finally {
            Arrays.fill(proposalBytes, (byte) 0);
            Arrays.fill(userBytes, (byte) 0);
        }
    }

    private byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
