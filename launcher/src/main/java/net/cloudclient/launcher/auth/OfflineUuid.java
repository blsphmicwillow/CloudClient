package net.cloudclient.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Offline profile UUIDs, matching the vanilla server's derivation.
 *
 * <p>Vanilla computes {@code UUID.nameUUIDFromBytes(("OfflinePlayer:" + name))},
 * i.e. a version-3 (MD5) UUID. Reproducing it exactly matters: a mismatch
 * changes your player data on offline servers.
 */
public final class OfflineUuid {

    private OfflineUuid() {
    }

    public static String forName(String username) {
        byte[] hash = md5(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        // Mark as version 3, variant IETF, exactly like UUID.nameUUIDFromBytes.
        hash[6] = (byte) ((hash[6] & 0x0F) | 0x30);
        hash[8] = (byte) ((hash[8] & 0x3F) | 0x80);

        long msb = 0L;
        long lsb = 0L;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (hash[i] & 0xFFL);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (hash[i] & 0xFFL);
        return new UUID(msb, lsb).toString().replace("-", "");
    }

    private static byte[] md5(byte[] input) {
        try {
            return MessageDigest.getInstance("MD5").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }
}
