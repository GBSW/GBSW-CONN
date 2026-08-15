package kr.hs.gbsw.communication.auth.delivery;

import java.time.Instant;
import java.util.UUID;

public record CredentialDeliveryCommand(
        UUID governanceRequestPublicId,
        UUID targetUserPublicId,
        String recipientReference,
        CredentialDeliveryType deliveryType,
        String oneTimeCode,
        Instant expiresAt
) {
}
