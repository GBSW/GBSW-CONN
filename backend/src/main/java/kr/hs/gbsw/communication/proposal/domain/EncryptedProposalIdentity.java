package kr.hs.gbsw.communication.proposal.domain;

public record EncryptedProposalIdentity(
        byte[] ciphertext,
        byte[] nonce,
        int keyVersion
) {
    public EncryptedProposalIdentity {
        ciphertext = ciphertext.clone();
        nonce = nonce.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    @Override
    public byte[] nonce() {
        return nonce.clone();
    }
}
