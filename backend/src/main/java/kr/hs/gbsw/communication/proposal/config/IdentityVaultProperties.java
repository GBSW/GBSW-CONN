package kr.hs.gbsw.communication.proposal.config;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.identity-vault")
public final class IdentityVaultProperties implements InitializingBean {

    private String keyBase64;
    private int keyVersion = 1;
    private Integer activeKeyVersion;
    private Map<Integer, String> keys = new LinkedHashMap<>();
    private Map<Integer, byte[]> decodedKeys;
    private int resolvedActiveKeyVersion;

    @Override
    public void afterPropertiesSet() {
        Map<Integer, byte[]> resolved = decodeKeys(keys);
        if (keyBase64 != null && !keyBase64.isBlank()) {
            requirePositiveVersion(keyVersion);
            byte[] legacyKey = decodeKey(keyBase64, "legacy identity key");
            byte[] existing = resolved.putIfAbsent(keyVersion, legacyKey);
            if (existing != null && !MessageDigest.isEqual(existing, legacyKey)) {
                throw new IllegalArgumentException("Identity Vault legacy key conflicts with keyring version "
                        + keyVersion);
            }
        }
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("At least one Identity Vault key is required");
        }
        resolvedActiveKeyVersion = resolveActiveVersion(resolved);
        if (!resolved.containsKey(resolvedActiveKeyVersion)) {
            throw new IllegalArgumentException("Active Identity Vault key version is not present in the keyring");
        }
        decodedKeys = Map.copyOf(resolved);
        keyBase64 = null;
        keys = Map.of();
    }

    public byte[] activeKey() {
        return keyForVersion(activeKeyVersion())
                .orElseThrow(() -> new IllegalStateException("Active Identity Vault key is unavailable"));
    }

    public int activeKeyVersion() {
        requireInitialized();
        return resolvedActiveKeyVersion;
    }

    public Optional<byte[]> keyForVersion(int version) {
        requireInitialized();
        byte[] key = decodedKeys.get(version);
        return key == null ? Optional.empty() : Optional.of(key.clone());
    }

    public byte[] key() {
        return activeKey();
    }

    public int keyVersion() {
        return activeKeyVersion();
    }

    public String getKeyBase64() {
        return keyBase64;
    }

    public void setKeyBase64(String keyBase64) {
        this.keyBase64 = keyBase64;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(int keyVersion) {
        this.keyVersion = keyVersion;
    }

    public Integer getActiveKeyVersion() {
        return activeKeyVersion;
    }

    public void setActiveKeyVersion(Integer activeKeyVersion) {
        this.activeKeyVersion = activeKeyVersion;
    }

    public Map<Integer, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<Integer, String> keys) {
        this.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys);
    }

    @Override
    public String toString() {
        return "IdentityVaultProperties[keys=REDACTED,activeKeyVersion="
                + (decodedKeys == null ? "unresolved" : resolvedActiveKeyVersion) + "]";
    }

    private int resolveActiveVersion(Map<Integer, byte[]> resolved) {
        if (activeKeyVersion != null) {
            requirePositiveVersion(activeKeyVersion);
            return activeKeyVersion;
        }
        if (keyBase64 != null && !keyBase64.isBlank()) {
            return keyVersion;
        }
        if (resolved.size() == 1) {
            return resolved.keySet().iterator().next();
        }
        throw new IllegalArgumentException("active-key-version is required when multiple Identity Vault keys exist");
    }

    private Map<Integer, byte[]> decodeKeys(Map<Integer, String> configuredKeys) {
        Map<Integer, byte[]> resolved = new LinkedHashMap<>();
        configuredKeys.forEach((version, value) -> {
            if (version == null) {
                throw new IllegalArgumentException("Identity Vault key version must not be null");
            }
            requirePositiveVersion(version);
            resolved.put(version, decodeKey(value, "identity key version " + version));
        });
        return resolved;
    }

    private byte[] decodeKey(String encoded, String label) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(label + " must be valid Base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException(label + " must decode to exactly 32 bytes");
        }
        return decoded;
    }

    private void requirePositiveVersion(int version) {
        if (version < 1) {
            throw new IllegalArgumentException("Identity Vault key versions must be positive");
        }
    }

    private void requireInitialized() {
        if (decodedKeys == null) {
            throw new IllegalStateException("Identity Vault properties have not been initialized");
        }
    }
}
