package net.cloudclient.launcher.auth;

/**
 * A resolved Minecraft account.
 *
 * <p>{@code accessToken} and {@code refreshToken} are secrets: they are never
 * logged, never written to the console, and only persisted by
 * {@link AccountStore} with owner-only file permissions.
 */
public final class Account {

    public final String username;
    public final String uuid;
    public final String accessToken;
    public final String refreshToken;
    /** "msa" for Microsoft accounts; "legacy" for offline/testing profiles. */
    public final String userType;
    public final boolean offline;
    /** Xbox gamertag, when the profile service answered. */
    public final String gamertag;
    /** Xbox user id (XUID), the stable Xbox identity. */
    public final String xuid;
    public final long obtainedAtMillis;

    public Account(String username, String uuid, String accessToken, String refreshToken,
                   String userType, boolean offline) {
        this(username, uuid, accessToken, refreshToken, userType, offline, null, null);
    }

    public Account(String username, String uuid, String accessToken, String refreshToken,
                   String userType, boolean offline, String gamertag, String xuid) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userType = userType;
        this.offline = offline;
        this.gamertag = gamertag;
        this.xuid = xuid;
        this.obtainedAtMillis = System.currentTimeMillis();
    }

    /** What to show in the launcher: the Xbox identity when there is one. */
    public String displayName() {
        return gamertag != null && !gamertag.trim().isEmpty() ? gamertag : username;
    }

    public static Account offline(String username) {
        // Offline UUIDs must be deterministic: servers that run in offline mode
        // derive the profile id from the name, so a random UUID would break
        // inventories and permissions.
        String uuid = OfflineUuid.forName(username);
        return new Account(username, uuid, "0", null, "legacy", true);
    }

    public boolean isExpired() {
        // Microsoft-issued Minecraft tokens last 24h; refresh a bit early.
        return !offline && System.currentTimeMillis() - obtainedAtMillis > 23L * 60L * 60L * 1000L;
    }

    /** Safe for logs: names and ids only, never tokens. */
    @Override
    public String toString() {
        String name = gamertag != null && !gamertag.isEmpty()
                ? gamertag + " / " + username
                : username;
        String xbox = xuid != null && !xuid.isEmpty() ? ", xuid " + xuid : "";
        return name + " (" + uuid + xbox + (offline ? ", offline" : "") + ")";
    }
}
