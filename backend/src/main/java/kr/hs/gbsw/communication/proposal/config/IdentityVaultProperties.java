package kr.hs.gbsw.communication.proposal.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.identity-vault")
public final class IdentityVaultProperties {

    private final byte[] key;
    private final int keyVersion;

    public IdentityVaultProperties(@NotBlank String keyBase64, @Min(1) int keyVersion) {
        try {
            this.key = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Identity Vault key must be valid Base64", exception);
        }
        if (key.length != 32) {
            throw new IllegalArgumentException("Identity Vault key must decode to exactly 32 bytes");
        }
        this.keyVersion = keyVersion;
    }

    public byte[] key() {
        return key.clone();
    }

    public int keyVersion() {
        return keyVersion;
    }

    @Override
    public String toString() {
        return "IdentityVaultProperties[key=REDACTED,keyVersion=" + keyVersion + "]";
    }
}
