package com.loanops.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

final class PolicyHashing {

    private PolicyHashing() {}

    static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static String stableId(String namespace, String value) {
        return UUID.nameUUIDFromBytes((namespace + "\n" + value).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String normalize(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n').strip();
    }
}
