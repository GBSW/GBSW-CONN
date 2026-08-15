package kr.hs.gbsw.communication.proposal.service;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import kr.hs.gbsw.communication.proposal.config.IdentityVaultProperties;
import kr.hs.gbsw.communication.proposal.domain.EncryptedProposalIdentity;
import kr.hs.gbsw.communication.proposal.exception.IdentityKeyUnavailableException;
import org.springframework.stereotype.Service;

@Service
public class ProposalIdentityCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final IdentityVaultProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public ProposalIdentityCipher(IdentityVaultProperties properties) {
        this.properties = properties;
    }

    public EncryptedProposalIdentity encrypt(UUID proposalPublicId, UUID userId) {
        byte[] nonce = new byte[NONCE_BYTES];
        byte[] key = properties.activeKey();
        byte[] plaintext = uuidBytes(userId);
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(uuidBytes(proposalPublicId));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return new EncryptedProposalIdentity(ciphertext, nonce, properties.activeKeyVersion());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Proposal identity encryption failed", exception);
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    public UUID decrypt(UUID proposalPublicId, EncryptedProposalIdentity identity) {
        byte[] key = properties.keyForVersion(identity.keyVersion())
                .orElseThrow(() -> new IdentityKeyUnavailableException(identity.keyVersion()));
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, identity.nonce()));
            cipher.updateAAD(uuidBytes(proposalPublicId));
            byte[] plaintext = cipher.doFinal(identity.ciphertext());
            try {
                if (plaintext.length != 16) {
                    throw new IllegalStateException("Invalid proposal identity plaintext");
                }
                ByteBuffer buffer = ByteBuffer.wrap(plaintext);
                return new UUID(buffer.getLong(), buffer.getLong());
            } finally {
                Arrays.fill(plaintext, (byte) 0);
            }
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Proposal identity decryption failed", exception);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
