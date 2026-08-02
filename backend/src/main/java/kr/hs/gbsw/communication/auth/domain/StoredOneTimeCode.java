package kr.hs.gbsw.communication.auth.domain;

import java.time.Instant;
import java.util.UUID;

public record StoredOneTimeCode(UUID id, String codeHash, Instant expiresAt) {
}
