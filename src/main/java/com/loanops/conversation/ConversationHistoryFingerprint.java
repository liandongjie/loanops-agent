package com.loanops.conversation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class ConversationHistoryFingerprint {

    private static final byte[] FORMAT = "loanops-conversation-history-v1".getBytes(StandardCharsets.UTF_8);

    private ConversationHistoryFingerprint() {
    }

    public static String sha256(List<ConversationHistoryMessage> history) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateBytes(digest, FORMAT);
            updateInt(digest, history.size());
            for (ConversationHistoryMessage message : history) {
                updateInt(digest, message.sequenceNo());
                updateBytes(digest, message.role().getBytes(StandardCharsets.UTF_8));
                updateBytes(digest, message.content().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static void updateBytes(MessageDigest digest, byte[] value) {
        updateInt(digest, value.length);
        digest.update(value);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
}
