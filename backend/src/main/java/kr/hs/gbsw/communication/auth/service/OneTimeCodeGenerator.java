package kr.hs.gbsw.communication.auth.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class OneTimeCodeGenerator {

    private static final char[] ALPHABET =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final int CODE_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        char[] code = new char[CODE_LENGTH];
        for (int index = 0; index < code.length; index++) {
            code[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
