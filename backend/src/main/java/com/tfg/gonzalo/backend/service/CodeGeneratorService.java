package com.tfg.gonzalo.backend.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Service;

@Service
public class CodeGeneratorService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int EVENT_CODE_LENGTH = 8;
    private static final int GROUP_CODE_LENGTH = 6;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateEventCode() {
        return generateCode(EVENT_CODE_LENGTH);
    }

    public String generateGroupCode() {
        return generateCode(GROUP_CODE_LENGTH);
    }

    private String generateCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            int nextCharIndex = secureRandom.nextInt(ALPHABET.length());
            builder.append(ALPHABET.charAt(nextCharIndex));
        }
        return builder.toString();
    }
}