package kr.hs.gbsw.communication.proposal.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kr.hs.gbsw.communication.proposal.domain.EncryptedProposalIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProposalIdentityRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProposalIdentityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertIdentity(UUID proposalId, EncryptedProposalIdentity identity, Instant now) {
        jdbcTemplate.update("""
                        INSERT INTO proposal_identities (
                            proposal_id, encrypted_user_id, nonce, key_version, created_at
                        ) VALUES (UUID_TO_BIN(?), ?, ?, ?, ?)
                        """,
                proposalId.toString(), identity.ciphertext(), identity.nonce(),
                identity.keyVersion(), Timestamp.from(now));
    }

    public List<MaintenanceCandidate> findWithoutOwnershipTags(int size) {
        return jdbcTemplate.query("""
                        SELECT BIN_TO_UUID(proposal.id) AS proposal_id,
                               BIN_TO_UUID(proposal.public_id) AS proposal_public_id,
                               proposal.created_at,
                               identity.encrypted_user_id,
                               identity.nonce,
                               identity.key_version
                        FROM proposals proposal
                        JOIN proposal_identities identity ON identity.proposal_id = proposal.id
                        LEFT JOIN proposal_author_ownership_tags ownership
                          ON ownership.proposal_id = proposal.id
                        WHERE ownership.proposal_id IS NULL
                        ORDER BY proposal.created_at, proposal.id
                        LIMIT ?
                        """,
                (resultSet, rowNumber) -> new MaintenanceCandidate(
                        UUID.fromString(resultSet.getString("proposal_id")),
                        UUID.fromString(resultSet.getString("proposal_public_id")),
                        resultSet.getTimestamp("created_at").toInstant(),
                        new EncryptedProposalIdentity(
                                resultSet.getBytes("encrypted_user_id"),
                                resultSet.getBytes("nonce"),
                                resultSet.getInt("key_version"))),
                size);
    }

    public boolean deleteAutomaticAuthorSupport(
            UUID proposalId,
            UUID authorUserId,
            Instant proposalCreatedAt
    ) {
        return jdbcTemplate.update("""
                        DELETE FROM proposal_supports
                        WHERE proposal_id = UUID_TO_BIN(?)
                          AND voter_user_id = UUID_TO_BIN(?)
                          AND created_at = ?
                        """,
                proposalId.toString(), authorUserId.toString(), Timestamp.from(proposalCreatedAt)) == 1;
    }

    public record MaintenanceCandidate(
            UUID proposalId,
            UUID proposalPublicId,
            Instant proposalCreatedAt,
            EncryptedProposalIdentity identity
    ) {
    }
}
