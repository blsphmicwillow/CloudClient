package net.cloudclient.launcher.auth;

import java.io.IOException;

import net.cloudclient.core.json.Json;
import net.cloudclient.launcher.net.Http;

/**
 * Microsoft device-code login.
 *
 * <p>Device code is used instead of an embedded browser because it needs no
 * redirect URI, no local web server and no browser dependency: the launcher
 * shows a code, the user types it at microsoft.com/link on any device.
 *
 * <p>A client id is required and cannot be shipped: it identifies the app to
 * Microsoft. Register a free "personal Microsoft accounts" app in Entra
 * (Azure AD) with the Minecraft API permission, then either set
 * {@code CLOUDCLIENT_MSA_CLIENT_ID} or pass {@code --client-id}.
 */
public final class MicrosoftAuth {

    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.signin offline_access";

    public static final class DeviceCode {
        public final String verificationUri;
        public final String userCode;
        public final String deviceCode;
        public final int expiresInSeconds;
        public final int intervalSeconds;

        DeviceCode(String verificationUri, String userCode, String deviceCode,
                   int expiresInSeconds, int intervalSeconds) {
            this.verificationUri = verificationUri;
            this.userCode = userCode;
            this.deviceCode = deviceCode;
            this.expiresInSeconds = expiresInSeconds;
            this.intervalSeconds = intervalSeconds;
        }
    }

    private MicrosoftAuth() {
    }

    public static String clientId(String override) {
        if (override != null && !override.trim().isEmpty()) return override.trim();
        String fromEnv = System.getenv("CLOUDCLIENT_MSA_CLIENT_ID");
        if (fromEnv != null && !fromEnv.trim().isEmpty()) return fromEnv.trim();
        throw new IllegalStateException(
                "No Microsoft client id. Set CLOUDCLIENT_MSA_CLIENT_ID or pass --client-id.\n"
                        + "Register a free app at https://entra.microsoft.com with a "
                        + "\"Mobile and desktop applications\" platform, then add the "
                        + "Xbox Live signin scope. See launcher/README.md.");
    }

    public static DeviceCode requestDeviceCode(String clientId) throws IOException {
        Http.Response response = Http.postForm(DEVICE_CODE_URL, Http.form(
                "client_id", clientId,
                "scope", SCOPE));
        if (!response.ok()) {
            throw new IOException("devicecode failed: HTTP " + response.status + " " + response.body);
        }
        java.util.Map<String, Object> json = Json.asObject(Json.parse(response.body));
        return new DeviceCode(
                Json.getString(json, "verification_uri", "https://microsoft.com/link"),
                Json.getString(json, "user_code", ""),
                Json.getString(json, "device_code", ""),
                Json.getInt(json, "expires_in", 900),
                Math.max(1, Json.getInt(json, "interval", 5)));
    }

    /** Polls until the user finishes signing in. Blocking; call from a worker. */
    public static String pollForToken(String clientId, DeviceCode code) throws IOException {
        long deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            sleep(code.intervalSeconds * 1000L);
            Http.Response response = Http.postForm(TOKEN_URL, Http.form(
                    "client_id", clientId,
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code", code.deviceCode));
            String body = response.body == null ? "" : response.body;
            if (response.ok()) {
                return Json.getString(Json.asObject(Json.parse(body)), "access_token", null);
            }
            if (body.contains("authorization_pending")) continue;
            if (body.contains("slow_down")) {
                sleep(2000L);
                continue;
            }
            throw new IOException("Microsoft login failed: " + body);
        }
        throw new IOException("Microsoft login timed out");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
