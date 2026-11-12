# Cloud Client Launcher

A dependency-free command-line launcher that installs Minecraft 1.8.9 + Forge,
drops the Cloud Client mod into `mods/`, and boots the game.

## How sign-in works (and why there is no password field)

Microsoft accounts are Xbox accounts, and Minecraft authorises through Xbox
Live. The chain is:

```
Microsoft sign-in  ->  Xbox Live token  ->  XSTS token  ->  Minecraft token
                                                            ->  gamertag + XUID
```

The launcher shows your **gamertag** in the account chip, with the Minecraft name
beside it, and stores the XUID as the stable Xbox identity.

**There is deliberately no email/password form.** Microsoft does not offer a
password grant for consumer accounts: any third-party launcher asking for your
Microsoft password is either phishing or using an endpoint Microsoft has
retired. The supported flow is Microsoft's own sign-in page — here as a
**device code** (no embedded browser, no redirect URI, no local web server):

1. the launcher shows a short code (and the log prints it),
2. you open `microsoft.com/link` and sign in with your Microsoft email there,
3. the launcher polls until Xbox authorises Minecraft.

Your password is only ever typed into Microsoft's page, never into this app.

The launcher also stores the OAuth **refresh token** (owner-only `0600`), so it
renews an expired session on launch instead of asking you to sign in again.

## Build

```bash
cd launcher
./build.sh            # -> build/cloud-launcher.jar
```

Only a JDK is required (8 or newer). No Gradle, no dependencies, no network at
build time.

## The window

```bash
java -jar build/cloud-launcher.jar
```

This is Cloud Client's own front end — it never starts the game through the
Mojang launcher. It shows the account chip with a painted cloud badge beside the
nickname (green for a Microsoft account, grey for an offline profile), the
installed version, a PLAY button and a live log of install/launch steps.

`--screenshot out.png` renders the same panel headlessly to a PNG, which is
useful for documentation and for checking layout without a display.
`--logo out.png` renders the app mark on its own (512px), which is what the
window, the taskbar icon and packaging art use.

## Sign in

A Microsoft **client id** is required and cannot be bundled: it identifies the
application to Microsoft. Create one once:

1. Open <https://entra.microsoft.com> → **App registrations** → **New registration**.
2. Supported account types: **Personal Microsoft accounts** (Minecraft accounts
   are personal accounts, not work accounts).
3. **Authentication** → **Add a platform** → **Mobile and desktop applications**.
4. **API permissions** → **Add a permission** → **APIs my organization uses** →
   search `XboxLive` → **Delegated permissions** → `XboxLive.signin`.
5. Copy the **Application (client) ID**.

Then either export it or pass it per run:

```bash
export CLOUDCLIENT_MSA_CLIENT_ID="<your-client-id>"
java -jar build/cloud-launcher.jar login

# or
java -jar build/cloud-launcher.jar login --client-id "<your-client-id>"
```

The token is written to `~/.cloudclient/account.json` with owner-only (`0600`)
permissions. It is never printed.

If the account has never signed in to Xbox, Microsoft returns
`2148916233`; open <https://xbox.com> once with that account and retry. Child
accounts (`2148916238`) need a family group.

## Offline profile

For single-player testing or offline-mode servers:

```bash
java -jar build/cloud-launcher.jar offline Steve
```

Offline UUIDs are derived the same way the vanilla server derives them
(`OfflinePlayer:<name>` → MD5, version 3), so inventories and permissions match
what an offline server expects.

## Launch

```bash
java -jar build/cloud-launcher.jar launch
```

On first run this:

1. fetches Mojang's version manifest and the 1.8.9 metadata,
2. downloads the 1.8.9 client jar,
3. downloads Forge `11.15.1.2318-1.8.9` and runs its installer,
4. downloads the remaining libraries and extracts natives,
5. copies `build/libs/cloud-client-*.jar` into `mods/`,
6. starts the game.

Everything lives under `~/.cloudclient` (`--game-dir` overrides it).

### Assets

Minecraft's sounds and language files live in an asset cache that the vanilla
launcher would normally own. Because this launcher is standalone, `launch`
downloads the 1.8.9 asset index and every object itself (content-addressed, each
verified by SHA-1). To repair a partial download without launching:

```bash
java -jar build/cloud-launcher.jar assets
```

### Dry run

`launch --dry-run` resolves the version, libraries, classpath and arguments and
prints the exact command, **without** downloading game files or starting the
game. Useful for checking your setup, and it is what the automated check in
this repository exercises:

```bash
java -jar build/cloud-launcher.jar launch --dry-run
```

## What this launcher does not do

- It does not patch or bypass Minecraft's authentication. Servers see a normal,
  unmodified client.
- It does not bundle Microsoft credentials, Forge, or Minecraft itself.
- It does not manage multiple profiles or Java versions; use `--game-dir` and a
  launcher-side `JAVA_HOME` if you need that.
