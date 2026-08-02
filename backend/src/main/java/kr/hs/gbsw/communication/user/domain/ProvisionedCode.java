package kr.hs.gbsw.communication.user.domain;

import java.time.Instant;
import java.util.UUID;

public record ProvisionedCode(UUID userPublicId, String code, Instant expiresAt) {
}
