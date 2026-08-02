package kr.hs.gbsw.communication.auth.service;

import kr.hs.gbsw.communication.auth.exception.PasswordPolicyException;
import kr.hs.gbsw.communication.common.config.ApplicationSecurityProperties;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    private final ApplicationSecurityProperties.Credentials properties;

    public PasswordPolicy(ApplicationSecurityProperties properties) {
        this.properties = properties.credentials();
    }

    public void validate(String password) {
        int length = password.codePointCount(0, password.length());
        if (length < properties.minimumPasswordLength() || length > properties.maximumPasswordLength()) {
            throw new PasswordPolicyException(
                    properties.minimumPasswordLength(),
                    properties.maximumPasswordLength());
        }
    }
}
