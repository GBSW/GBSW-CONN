package kr.hs.gbsw.communication.auth.domain;

public enum ThrottleOperation {
    LOGIN("LOGIN_ACCOUNT", "LOGIN_IP"),
    ACTIVATION("ACTIVATION_ACCOUNT", "ACTIVATION_IP"),
    PASSWORD_RESET("PASSWORD_RESET_ACCOUNT", "PASSWORD_RESET_IP"),
    REAUTHENTICATION("REAUTHENTICATION_ACCOUNT", "REAUTHENTICATION_IP"),
    IDENTITY_REVEAL("IDENTITY_REVEAL_ACCOUNT", "IDENTITY_REVEAL_IP");

    private final String accountScope;
    private final String ipScope;

    ThrottleOperation(String accountScope, String ipScope) {
        this.accountScope = accountScope;
        this.ipScope = ipScope;
    }

    public String accountScope() {
        return accountScope;
    }

    public String ipScope() {
        return ipScope;
    }
}
