package kr.hs.gbsw.communication.auth.delivery;

public interface CredentialDeliveryPort {
    CredentialDeliveryReceipt deliver(CredentialDeliveryCommand command);
}
