# Cloud Client Launcher

A dependency-free command-line launcher that installs Minecraft 1.8.9 + Forge,
drops the Cloud Client mod into `mods/`, and boots the game.

It uses the **Microsoft device-code flow**, so there is no embedded browser, no
redirect URI, and no web server: the launcher prints a code, you type it at
`microsoft.com/link` on your phone or another window, and the launcher picks up
the token.

## Build

```bash
cd launcher
./build.sh            # -> build/cloud-launcher.jar
```

Only a JDK is required (8 or newer). No Gradle, no dependencies, no network at
build time.

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
