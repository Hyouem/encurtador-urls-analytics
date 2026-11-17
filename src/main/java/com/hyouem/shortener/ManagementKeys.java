package com.hyouem.shortener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class ManagementKeys {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ManagementKeys() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static boolean matches(String candidate, String expectedHash) {
        if (candidate == null || expectedHash == null
                || candidate.length() != 43) {
            return false;
        }

        try {
            byte[] expected = HexFormat.of().parseHex(expectedHash);
            byte[] actual = HexFormat.of().parseHex(hash(candidate));
            return MessageDigest.isEqual(actual, expected);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}