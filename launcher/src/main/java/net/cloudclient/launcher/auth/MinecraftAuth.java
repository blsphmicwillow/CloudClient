package net.cloudclient.launcher.auth;

import java.io.IOException;
import java.util.Map;

import net.cloudclient.core.json.Json;
import net.cloudclient.launcher.net.Http;

/**
 * The Xbox Live / Minecraft authentication chain.
 *
 * <p>Four steps, each needing the previous token:
 * <ol>
 *   <li>Microsoft access token -> Xbox Live user token</li>
 *   <li>Xbox Live user token -> XSTS token (this is where "no Xbox profile" fails)</li>
 *   <li>XSTS token -> Minecraft access token</li>
 *   <li>Minecraft token -> profile (name + uuid)</li>
 * </ol>
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

    private MinecraftAuth() {
    }

    public static Account login(String microsoftAccessToken) throws IOException {
        String xbl = xboxLiveToken(microsoftAccessToken);
        String xsts = xstsToken(xbl);
        String mcToken = minecraftToken(xsts);
        return profile(mcToken);
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

    private static String xstsToken(String xblToken) throws IOException {
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
            throw new IOException("XSTS auth failed: HTTP " + response.status + " " + body);
        }
        return Json.getString(Json.asObject(Json.parse(response.body)), "Token", null);
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

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
