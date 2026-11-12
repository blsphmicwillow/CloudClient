package net.cloudclient.launcher.auth;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import net.cloudclient.core.json.Json;
import net.cloudclient.launcher.net.Http;

/**
 * The Xbox Live / Minecraft authentication chain.
 *
 * <p>Minecraft accounts are Xbox accounts. Four steps, each needing the previous
 * token:
 * <ol>
 *   <li>Microsoft access token -> Xbox Live user token</li>
 *   <li>Xbox Live user token -> XSTS token (plus the user hash that identifies
 *       the account to Xbox services)</li>
 *   <li>XSTS token -> Minecraft access token</li>
 *   <li>Minecraft token -> profile (name + uuid)</li>
 * </ol>
 *
 * <p>The gamertag and XUID come from the Xbox profile service using the same
 * XSTS token, so the launcher can show the Xbox identity rather than just the
 * Minecraft name.
 */
public final class MinecraftAuth {

    private static final String XBL_URL =
            "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL =
            "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";
    private static final String XBOX_PROFILE_URL =
            "https://profile.xboxlive.com/users/me/profile/settings"
                    + "?settings=Gamertag,Xuid,GameDisplayPicRaw";

    /** XSTS token plus the user hash Xbox services expect. */
    public static final class Xsts {
        public final String token;
        public final String userHash;

        Xsts(String token, String userHash) {
            this.token = token;
            this.userHash = userHash;
        }
    }

    private MinecraftAuth() {
    }

    /** Full sign-in: Microsoft token in, ready-to-use account out. */
    public static Account login(String microsoftAccessToken, String refreshToken)
            throws IOException {
        String xbl = xboxLiveToken(microsoftAccessToken);
        Xsts xsts = xstsToken(xbl);
        String mcToken = minecraftToken(xsts.token);

        Account profile = profile(mcToken);
        String gamertag = null;
        String xuid = null;
        try {
            Map<String, String> xbox = xboxProfile(xsts);
            gamertag = xbox.get("Gamertag");
            xuid = xbox.get("Xuid");
        } catch (IOException ignored) {
            // A profile-service hiccup must not block a working login; the
            // Minecraft name is still a usable display name.
        }
        return new Account(profile.username, profile.uuid, mcToken, refreshToken,
                "msa", false, gamertag, xuid);
    }

    private static String xboxLiveToken(String msaToken) throws IOException {
        String payload = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":"
                + "\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + escape(msaToken) + "\"},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
        Http.Response response = Http.postJson(XBL_URL, payload, null);
        if (!response.ok()) {
            throw new IOException("Xbox Live auth failed: HTTP " + response.status
                    + " " + response.body);
        }
        Map<String, Object> json = Json.asObject(Json.parse(response.body));
        return Json.getString(json, "Token", null);
    }

    private static Xsts xstsToken(String xblToken) throws IOException {
        String payload = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\""
                + escape(xblToken) + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\","
                + "\"TokenType\":\"JWT\"}";
        Http.Response response = Http.postJson(XSTS_URL, payload, null);
        if (!response.ok()) {
            String body = response.body == null ? "" : response.body;
            if (body.contains("2148916233")) {
                throw new IOException("This Microsoft account has no Xbox profile. "
                        + "Sign in to xbox.com once to create one, then retry.");
            }
            if (body.contains("2148916238")) {
                throw new IOException("This is a child account and needs a family group "
                        + "before it can play Minecraft.");
            }
            throw new IOException("Xbox authorisation failed: HTTP " + response.status
                    + " " + body);
        }

        Map<String, Object> json = Json.asObject(Json.parse(response.body));
        String token = Json.getString(json, "Token", null);
        String userHash = null;
        Map<String, Object> claims = Json.child(json, "DisplayClaims");
        if (claims != null) {
            java.util.List<Object> xui = Json.children(claims, "xui");
            if (xui != null && !xui.isEmpty() && xui.get(0) instanceof Map) {
                userHash = Json.getString(Json.asObject(xui.get(0)), "uhs", null);
            }
        }
        if (userHash == null) {
            throw new IOException("Xbox authorisation returned no user hash");
        }
        return new Xsts(token, userHash);
    }

    private static String minecraftToken(String xstsToken) throws IOException {
        String payload = "{\"identityToken\":\"XBL3.0 x=;" + escape(xstsToken) + "\"}";
        Http.Response response = Http.postJson(MC_LOGIN_URL, payload, null);
        if (!response.ok()) {
            throw new IOException("Minecraft login failed: HTTP " + response.status
                    + " " + response.body);
        }
        return Json.getString(Json.asObject(Json.parse(response.body)), "access_token", null);
    }

    public static Account profile(String mcToken) throws IOException {
        Http.Response response = Http.get(MC_PROFILE_URL + "?access_token=" + mcToken);
        if (response.status == 404) {
            throw new IOException("This account does not own Minecraft: Java Edition.");
        }
        if (!response.ok()) {
            throw new IOException("Profile fetch failed: HTTP " + response.status);
        }
        Map<String, Object> json = Json.asObject(Json.parse(response.body));
        String name = Json.getString(json, "name", "player");
        String id = Json.getString(json, "id", "");
        return new Account(name, id, mcToken, null, "msa", false);
    }

    /** Gamertag / XUID / avatar, authenticated with the XSTS token. */
    private static Map<String, String> xboxProfile(Xsts xsts) throws IOException {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Authorization", "XBL3.0 x=" + xsts.userHash + ";" + xsts.token);
        headers.put("Accept", "application/json");
        Http.Response response = Http.get(XBOX_PROFILE_URL, headers);
        if (!response.ok()) {
            throw new IOException("Xbox profile failed: HTTP " + response.status);
        }
        Map<String, String> out = new LinkedHashMap<String, String>();
        Map<String, Object> json = Json.asObject(Json.parse(response.body));
        java.util.List<Object> users = Json.children(json, "profileUsers");
        if (users == null || users.isEmpty() || !(users.get(0) instanceof Map)) return out;

        Map<String, Object> user = Json.asObject(users.get(0));
        java.util.List<Object> settings = Json.children(user, "settings");
        if (settings == null) return out;
        for (Object setting : settings) {
            if (!(setting instanceof Map)) continue;
            Map<String, Object> map = Json.asObject(setting);
            String id = Json.getString(map, "id", null);
            String value = Json.getString(map, "value", null);
            if (id != null && value != null && !value.isEmpty()) out.put(id, value);
        }
        return out;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
