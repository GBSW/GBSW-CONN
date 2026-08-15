package kr.hs.gbsw.communication.proposal.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProposalOwnershipRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProposalOwnershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(UUID proposalId, int keyVersion, byte[] tag, Instant now) {
        jdbcTemplate.update("""
                        INSERT INTO proposal_author_ownership_tags (
                            proposal_id, lookup_key_version, owner_lookup_tag, created_at
                        ) VALUES (UUID_TO_BIN(?), ?, ?, ?)
                        """,
                proposalId.toString(), keyVersion, tag, Timestamp.from(now));
    }

    public boolean insertIfAbsent(UUID proposalId, int keyVersion, byte[] tag, Instant now) {
        try {
            insert(proposalId, keyVersion, tag, now);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    public Optional<StoredOwnershipTag> findByProposalPublicId(UUID publicId) {
        return jdbcTemplate.query("""
                        SELECT ownership.lookup_key_version, ownership.owner_lookup_tag
                        FROM proposal_author_ownership_tags ownership
                        JOIN proposals proposal ON proposal.id = ownership.proposal_id
                        WHERE proposal.public_id = UUID_TO_BIN(?)
                        """,
                (resultSet, rowNumber) -> new StoredOwnershipTag(
                        resultSet.getInt("lookup_key_version"),
                        resultSet.getBytes("owner_lookup_tag")),
                publicId.toString()).stream().findFirst();
    }

    public record StoredOwnershipTag(int keyVersion, byte[] tag) {
        public StoredOwnershipTag {
            tag = tag.clone();
        }

        @Override
        public byte[] tag() {
            return tag.clone();
        }
    }
}
