package kr.hs.gbsw.communication.proposal.exception;

public class IdentityKeyUnavailableException extends IllegalStateException {

    private final int keyVersion;

    public IdentityKeyUnavailableException(int keyVersion) {
        super("Identity key version " + keyVersion + " is unavailable");
        this.keyVersion = keyVersion;
    }

    public int keyVersion() {
        return keyVersion;
    }
}
