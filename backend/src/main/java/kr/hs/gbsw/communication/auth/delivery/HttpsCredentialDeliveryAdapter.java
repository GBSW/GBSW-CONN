package kr.hs.gbsw.communication.auth.delivery;

import java.net.URI;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpsCredentialDeliveryAdapter implements CredentialDeliveryPort {

    private final String endpoint;
    private final String bearerToken;
    private final DeploymentSecurityProperties deploymentProperties;

    public HttpsCredentialDeliveryAdapter(
            @Value("${CREDENTIAL_DELIVERY_ENDPOINT:}") String endpoint,
            @Value("${CREDENTIAL_DELIVERY_BEARER_TOKEN:}") String bearerToken,
            DeploymentSecurityProperties deploymentProperties
    ) {
        this.endpoint = endpoint;
        this.bearerToken = bearerToken;
        this.deploymentProperties = deploymentProperties;
    }

    @Override
    public CredentialDeliveryReceipt deliver(CredentialDeliveryCommand command) {
        if (endpoint.isBlank() || bearerToken.isBlank()) {
            throw new IllegalStateException("Recipient-bound credential delivery is not configured");
        }
        URI uri = URI.create(endpoint);
        if (!isAllowedEndpoint(uri)) {
            throw new IllegalStateException(
                    "Credential delivery endpoint must use HTTPS, except loopback HTTP outside production");
        }
        ResponseEntity<Void> response = RestClient.create()
                .post()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .body(new DeliveryPayload(
                        command.governanceRequestPublicId(), command.targetUserPublicId(),
                        command.recipientReference(), command.deliveryType().name(),
                        command.oneTimeCode(), command.expiresAt()))
                .retrieve()
                .toBodilessEntity();
        return new CredentialDeliveryReceipt(response.getHeaders().getFirst("X-Delivery-Id"));
    }

    private boolean isAllowedEndpoint(URI uri) {
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return true;
        }
        if (!"http".equalsIgnoreCase(uri.getScheme()) || deploymentProperties.production()) {
            return false;
        }
        String host = uri.getHost();
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    private record DeliveryPayload(
            java.util.UUID requestPublicId,
            java.util.UUID userPublicId,
            String recipientReference,
            String credentialType,
            String oneTimeCode,
            java.time.Instant expiresAt
    ) {
    }
}
