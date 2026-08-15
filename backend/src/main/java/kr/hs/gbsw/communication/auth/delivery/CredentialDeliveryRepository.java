package kr.hs.gbsw.communication.auth.delivery;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CredentialDeliveryRepository {

    private final JdbcTemplate jdbcTemplate;

    public CredentialDeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordDelivered(
            UUID governanceRequestId,
            UUID targetUserId,
            CredentialDeliveryType type,
            CredentialDeliveryReceipt receipt,
            Instant now
    ) {
        jdbcTemplate.update("""
                        INSERT INTO credential_delivery_records (
                            id, privileged_change_request_id, target_user_id, delivery_type,
                            delivery_status, provider_message_id, attempted_at, delivered_at
                        ) VALUES (
                            UUID_TO_BIN(?), UUID_TO_BIN(?), UUID_TO_BIN(?), ?,
                            'DELIVERED', ?, ?, ?
                        )
                        """,
                UUID.randomUUID().toString(), governanceRequestId.toString(), targetUserId.toString(),
                type.name(), receipt.providerMessageId(), Timestamp.from(now), Timestamp.from(now));
    }
}
