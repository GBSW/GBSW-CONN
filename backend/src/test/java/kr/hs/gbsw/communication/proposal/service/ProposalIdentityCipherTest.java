package kr.hs.gbsw.communication.proposal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.config.IdentityVaultProperties;
import kr.hs.gbsw.communication.proposal.domain.EncryptedProposalIdentity;
import kr.hs.gbsw.communication.proposal.exception.IdentityKeyUnavailableException;
import org.junit.jupiter.api.Test;

class ProposalIdentityCipherTest {

    @Test
    void legacySingleKeyConfigurationStillEncryptsAndDecrypts() {
        IdentityVaultProperties properties = legacyProperties(1, encodedKey((byte) 1));
        ProposalIdentityCipher cipher = new ProposalIdentityCipher(properties);
        UUID proposalPublicId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        EncryptedProposalIdentity encrypted = cipher.encrypt(proposalPublicId, userId);

        assertEquals(1, encrypted.keyVersion());
        assertEquals(userId, cipher.decrypt(proposalPublicId, encrypted));
    }

    @Test
    void activeKeyEncryptsAndStoredVersionSelectsExactlyOneDecryptionKey() {
        IdentityVaultProperties properties = keyringProperties(
                2,
                Map.of(1, encodedKey((byte) 1), 2, encodedKey((byte) 2)));
        ProposalIdentityCipher cipher = new ProposalIdentityCipher(properties);
        UUID proposalPublicId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        EncryptedProposalIdentity encrypted = cipher.encrypt(proposalPublicId, userId);

        assertEquals(2, encrypted.keyVersion());
        assertEquals(userId, cipher.decrypt(proposalPublicId, encrypted));
    }

    @Test
    void missingStoredVersionFailsWithoutTryingAnotherKey() {
        UUID proposalPublicId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProposalIdentityCipher oldCipher = new ProposalIdentityCipher(
                legacyProperties(1, encodedKey((byte) 1)));
        EncryptedProposalIdentity encrypted = oldCipher.encrypt(proposalPublicId, userId);
        ProposalIdentityCipher currentCipher = new ProposalIdentityCipher(
                legacyProperties(2, encodedKey((byte) 2)));

        assertThrows(
                IdentityKeyUnavailableException.class,
                () -> currentCipher.decrypt(proposalPublicId, encrypted));
    }

    private IdentityVaultProperties legacyProperties(int version, String key) {
        IdentityVaultProperties properties = new IdentityVaultProperties();
        properties.setKeyVersion(version);
        properties.setKeyBase64(key);
        properties.afterPropertiesSet();
        return properties;
    }

    private IdentityVaultProperties keyringProperties(int activeVersion, Map<Integer, String> keys) {
        IdentityVaultProperties properties = new IdentityVaultProperties();
        properties.setActiveKeyVersion(activeVersion);
        properties.setKeys(keys);
        properties.afterPropertiesSet();
        return properties;
    }

    private String encodedKey(byte value) {
        byte[] key = new byte[32];
        Arrays.fill(key, value);
        return Base64.getEncoder().encodeToString(key);
    }
}
