# Prebuilt jars

Released builds, committed so that a fresh checkout can install and launch
without a JDK 8 + Gradle 2.14 toolchain.

| File | What it is |
|---|---|
| `cloud-client-0.1.1.jar` | The Forge mod (reobfuscated, 115 classes) |
| `cloud-launcher.jar` | The standalone launcher (own window, no Mojang launcher) |

The mod jar carries `cloudclient-build.properties` inside it; the game prints

```
Cloud Client 0.1.1 (build <git-hash> <build-time>)
```

as its first log line. If that line is missing or shows an older version, the
jar in `mods/` is stale.

## Refreshing these files

`install.sh` prefers `build/libs/cloud-client-<version>.jar` when it exists, so
local builds always win. To publish a new release build:

```bash
./scripts/build.sh                       # builds the mod and the launcher
cp build/libs/cloud-client-*.jar prebuilt/   # keep only the newest version
cp launcher/build/cloud-launcher.jar prebuilt/
git add prebuilt/ && git commit -m "Release <version>"
```

Do not edit these files by hand. They are build output, committed for
convenience, and the `build=...` stamp inside them is what identifies them.
