package kr.hs.gbsw.communication.user.exception;

public class BootstrapAlreadyCompletedException extends RuntimeException {

    public BootstrapAlreadyCompletedException() {
        super("The initial super administrator has already been provisioned");
    }
}
