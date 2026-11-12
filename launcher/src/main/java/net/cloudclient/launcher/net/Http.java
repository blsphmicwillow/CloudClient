package net.cloudclient.launcher.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal HTTP helper on {@code HttpURLConnection}.
 *
 * <p>No Apache HttpClient / OkHttp: the launcher ships as a single jar with no
 * dependencies, which matters because it must run on a bare Java 8 install.
 */
public final class Http {

    public static final class Response {
        public final int status;
        public final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public String header(String name) {
            return null;
        }
    }

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;

    private Http() {
    }

    public static Response get(String url) throws IOException {
        return request("GET", url, null, null);
    }

    public static Response postForm(String url, Map<String, String> form) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        return request("POST", url, sb.toString(),
                "application/x-www-form-urlencoded; charset=utf-8");
    }

    public static Response postJson(String url, String json, String bearer) throws IOException {
        return request("POST", url, json, "application/json", bearer);
    }

    private static Response request(String method, String url, String body,
                                    String contentType) throws IOException {
        return request(method, url, body, contentType, null);
    }

    private static Response request(String method, String url, String body,
                                    String contentType, String bearer) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestMethod(method);
        conn.setRequestProperty("User-Agent", "CloudClientLauncher/1.0");
        conn.setRequestProperty("Accept", "application/json");
        if (contentType != null) conn.setRequestProperty("Content-Type", contentType);
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer);
        conn.setInstanceFollowRedirects(true);

        if (body != null) {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(payload.length);
            OutputStream out = conn.getOutputStream();
            try {
                out.write(payload);
            } finally {
                out.close();
            }
        }

        int status = conn.getResponseCode();
        InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = stream == null ? "" : readAll(stream);
        conn.disconnect();
        return new Response(status, text);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        try {
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
        } finally {
            in.close();
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, String> form(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
