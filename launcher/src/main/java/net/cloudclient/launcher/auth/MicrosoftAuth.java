package net.cloudclient.launcher.auth;

import java.io.IOException;

import net.cloudclient.core.json.Json;
import net.cloudclient.launcher.net.Http;

/**
 * Microsoft account sign-in via the device-code flow.
 *
 * <p>Microsoft does not allow third-party clients to collect an email and
 * password: consumer accounts have no password grant, and any launcher that asks
 * for one is either phishing or using a deprecated endpoint that Microsoft
 * blocks. The supported flow is the account's own sign-in page — here as a
 * device code, so it works with no embedded browser and no redirect URI:
 *
 * <ol>
 *   <li>the launcher shows a short code,</li>
 *   <li>the user enters it at microsoft.com/link on any device and signs in with
 *       their Microsoft email,</li>
 *   <li>the launcher polls until the token arrives.</li>
 * </ol>
 *
 * <p>From there the credentials go through Xbox Live, which is what actually
 * authorises Minecraft (see {@link MinecraftAuth}).
 *
 * <p>A client id is required and cannot be shipped: it identifies the app to
 * Microsoft. Register a free personal-Microsoft-accounts app in Entra and set
 * {@code CLOUDCLIENT_MSA_CLIENT_ID}, or pass {@code --client-id}.
 */
public final class MicrosoftAuth {

    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.signin offline_access";

    /** An access token plus the refresh token that lets it be renewed. */
    public static final class Tokens {
        public final String accessToken;
        public final String refreshToken;

        Tokens(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

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
                        + "Register a free app at https://entra.microsoft.com with the "
                        + "\"Mobile and desktop applications\" platform and the "
                        + "XboxLive.signin scope. See launcher/README.md.");
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
    public static Tokens pollForToken(String clientId, DeviceCode code) throws IOException {
        long deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            sleep(code.intervalSeconds * 1000L);
            Http.Response response = Http.postForm(TOKEN_URL, Http.form(
                    "client_id", clientId,
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code", code.deviceCode));
            String body = response.body == null ? "" : response.body;
            if (response.ok()) {
                java.util.Map<String, Object> json = Json.asObject(Json.parse(body));
                return new Tokens(
                        Json.getString(json, "access_token", null),
                        Json.getString(json, "refresh_token", null));
            }
            if (body.contains("authorization_pending")) continue;
            if (body.contains("slow_down")) {
                sleep(2000L);
                continue;
            }
            throw new IOException("Microsoft sign-in failed: " + body);
        }
        throw new IOException("Microsoft sign-in timed out");
    }

    /**
     * Exchanges a refresh token for a fresh access token, so a saved account can
     * launch without another sign-in. Refresh tokens are single-use for
     * consumers: always persist the newly returned one.
     */
    public static Tokens refresh(String clientId, String refreshToken) throws IOException {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IOException("No refresh token saved; sign in again.");
        }
        Http.Response response = Http.postForm(TOKEN_URL, Http.form(
                "client_id", clientId,
                "grant_type", "refresh_token",
                "refresh_token", refreshToken,
                "scope", SCOPE));
        String body = response.body == null ? "" : response.body;
        if (!response.ok()) {
            throw new IOException("Token refresh failed: HTTP " + response.status + " " + body);
        }
        java.util.Map<String, Object> json = Json.asObject(Json.parse(body));
        return new Tokens(
                Json.getString(json, "access_token", null),
                Json.getString(json, "refresh_token", refreshToken));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
