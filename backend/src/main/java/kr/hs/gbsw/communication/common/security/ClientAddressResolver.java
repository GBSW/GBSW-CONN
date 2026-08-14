package kr.hs.gbsw.communication.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import kr.hs.gbsw.communication.common.config.DeploymentSecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class ClientAddressResolver {

    private final String trustedHeader;
    private final List<CidrBlock> trustedProxyCidrs;

    public ClientAddressResolver(DeploymentSecurityProperties properties) {
        this.trustedHeader = properties.trustedClientAddressHeader();
        this.trustedProxyCidrs = new ArrayList<>();
        for (String value : properties.trustedProxyCidrs()) {
            trustedProxyCidrs.add(CidrBlock.parse(value));
        }
    }

    public String resolve(HttpServletRequest request) {
        InetAddress peer = parseNumericAddress(request.getRemoteAddr(), "remote address");
        Enumeration<String> values = request.getHeaders(trustedHeader);
        if (values == null || !values.hasMoreElements()) {
            return peer.getHostAddress();
        }

        String forwarded = values.nextElement();
        if (values.hasMoreElements() || forwarded == null || forwarded.isBlank() || forwarded.contains(",")) {
            throw new InvalidClientAddressException("Trusted client address header must contain one address");
        }
        if (!isTrustedProxy(peer)) {
            throw new InvalidClientAddressException("Trusted client address header came from an untrusted peer");
        }
        return parseNumericAddress(forwarded.trim(), "trusted client address").getHostAddress();
    }

    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxyCidrs.stream().anyMatch(cidr -> cidr.contains(address));
    }

    private static InetAddress parseNumericAddress(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new InvalidClientAddressException("Missing " + description);
        }
        String candidate = value.trim();
        try {
            if (candidate.indexOf(':') >= 0) {
                if (!candidate.matches("[0-9A-Fa-f:.]+")) {
                    throw new InvalidClientAddressException("Malformed " + description);
                }
                return InetAddress.getByName(candidate);
            }
            String[] octets = candidate.split("\\.", -1);
            if (octets.length != 4) {
                throw new InvalidClientAddressException("Malformed " + description);
            }
            byte[] address = new byte[4];
            for (int index = 0; index < octets.length; index++) {
                if (octets[index].isEmpty() || !octets[index].chars().allMatch(Character::isDigit)) {
                    throw new InvalidClientAddressException("Malformed " + description);
                }
                int octet = Integer.parseInt(octets[index]);
                if (octet > 255) {
                    throw new InvalidClientAddressException("Malformed " + description);
                }
                address[index] = (byte) octet;
            }
            return InetAddress.getByAddress(address);
        } catch (NumberFormatException | UnknownHostException exception) {
            throw new InvalidClientAddressException("Malformed " + description, exception);
        }
    }

    private record CidrBlock(byte[] network, int prefixLength) {
        private static CidrBlock parse(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Trusted proxy CIDR cannot be blank");
            }
            String[] parts = value.trim().split("/", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Trusted proxy CIDR must include a prefix: " + value);
            }
            InetAddress address = parseNumericAddress(parts[0], "trusted proxy CIDR");
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix: " + value, exception);
            }
            byte[] bytes = address.getAddress();
            if (prefix < 0 || prefix > bytes.length * Byte.SIZE) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix: " + value);
            }
            return new CidrBlock(bytes, prefix);
        }

        private boolean contains(InetAddress candidate) {
            byte[] address = candidate.getAddress();
            if (address.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < fullBytes; index++) {
                if (address[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    public static class InvalidClientAddressException extends IllegalArgumentException {
        public InvalidClientAddressException(String message) {
            super(message);
        }

        public InvalidClientAddressException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
