package net.cloudclient.launcher.game;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Downloads with optional SHA-1 verification and atomic rename. */
public final class Downloader {

    private static final int BUFFER = 16384;

    private Downloader() {
    }

    public static void fetch(String url, File target, String sha1) throws IOException {
        if (target.isFile() && (sha1 == null || sha1.equalsIgnoreCase(sha1Of(target)))) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Cannot create " + parent);
        }

        File temp = new File(target.getPath() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setRequestProperty("User-Agent", "CloudClientLauncher/1.0");
        connection.setInstanceFollowRedirects(true);

        int status = connection.getResponseCode();
        if (status != 200) {
            connection.disconnect();
            throw new IOException("HTTP " + status + " for " + url);
        }

        MessageDigest digest = sha1 == null ? null : sha1Digest();
        InputStream in = connection.getInputStream();
        FileOutputStream out = new FileOutputStream(temp);
        try {
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                if (digest != null) digest.update(buffer, 0, read);
            }
        } finally {
            try {
                in.close();
            } finally {
                out.close();
            }
            connection.disconnect();
        }

        if (digest != null) {
            String actual = hex(digest.digest());
            if (!actual.equalsIgnoreCase(sha1)) {
                temp.delete();
                throw new IOException("Checksum mismatch for " + url
                        + "\n  expected " + sha1 + "\n  actual   " + actual);
            }
        }
        if (!temp.renameTo(target)) {
            if (!target.delete() || !temp.renameTo(target)) {
                throw new IOException("Cannot move download into place: " + target);
            }
        }
    }

    public static String sha1Of(File file) {
        if (!file.isFile()) return null;
        MessageDigest digest = sha1Digest();
        InputStream in = null;
        try {
            in = new java.io.FileInputStream(file);
            byte[] buffer = new byte[BUFFER];
            int read;
            while ((read = in.read(buffer)) > 0) digest.update(buffer, 0, read);
            return hex(digest.digest());
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
