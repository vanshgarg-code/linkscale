package com.linkscale.backend.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class CodeGenerator {

    private static final String BASE36_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(BASE36_ALPHABET.length());
            sb.append(BASE36_ALPHABET.charAt(index));
        }
        return sb.toString();
    }
}

