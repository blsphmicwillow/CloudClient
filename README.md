# Cloud Client

A client-side PvP client for **Minecraft 1.8.9 (Forge)**, built from scratch:
Verlet cloth capes, rigged wings, halos, horns, local pets, a HUD suite, and
fair-play performance options.

No Lunar Client code, binaries or assets are used. Nothing here unlocks,
spoofs or re-skins a paid cosmetic from another client, and nothing is sent to a
server — see [docs/FAIRPLAY.md](docs/FAIRPLAY.md) before you install it.

---

## What is actually implemented

This is a large specification, so the honest split between *working*,
*scaffolded* and *not started* matters more than a feature list.

| Area | Status | Notes |
|---|---|---|
| Module kernel + event bus | **Done** | Priority ordering, cancellation, enable-state persistence, per-module failure isolation |
| JSON codec + config store | **Done** | Dependency-free, strict parser; dot-path config with clamping |
| Cape Verlet cloth (8×4) | **Done** | Fixed 1/120s substeps, distance + bend constraints, body collision, state blending, teleport/lag-spike safe |
| Wing rig (4 bones) | **Done** | Procedural flap, per-bone phase lag, mirrored second wing, particle emission budget |
| Halos | **Done** | Ring mesh, spin, brightness pulse, hover |
| Horns | **Done** | Procedural tapered tube, rigid/curved, scale/pitch/yaw |
| Local pets | **Partial** | Boid separation + spring follow + shoulder riding work; pets render as tinted cubes, **vanilla mob models are not wired up yet** |
| Cosmetic renderer | **Done** | Batched Tessellator draws, animated texture V-offset, LOD math; compiles against 1.8.9 |
| `.cloudpack` format + loader | **Done** | Zip-slip rejection, size/poly budgets, PNG header checks, script rejection, registry |
| HUD (FPS, CPS, keystrokes, armour) | **Done** | Positions come from `HudLayout`, editable by dragging in the menu |
| Mod menu (right-shift) | **Done** | Searchable module list, sliders, toggles, colour cycling, keybind capture, HUD drag editor; settings are declared per module, so the UI is generic |
| Entity culling | **Done** | `RenderLivingEvent.Pre`, render-only, never culls players or dropped items, off by default |
| Dynamic render distance | **Done** | Hysteresis + cooldown; off by default |
| Launcher (account + boot) | **MVP** | Device-code Microsoft login, 1.8.9 + Forge install, classpath/natives assembly, offline UUIDs; `--dry-run` verified, a full boot is not |
| Chunk-mesh worker pool | **Not started** | Multi-threaded `RenderChunk` replacement is the single largest remaining perf task |
| LWJGL3 bridge | **Not started** | Optional path only; see roadmap |
| Friends / parties / replay | **Not started** | Needs a backend; deliberately out of scope for a client-only build |
| UGC hub + Cloud Studio | **Not started** | Format and loader exist; the web editor and hosting do not |
| Cross-client cosmetic visibility | **Not started (by design)** | Would require a packet channel or a server API. See FAIRPLAY.md |

## Verification status — read this

- **Core engine: verified.** 127 assertions pass on a bare JDK 17
  (`--release 8`), covering the event bus, JSON, config, module lifecycle,
  anchor transforms, matrices, fast maths, cape stability, wing motion, pet
  flocking, halo/horn geometry, LOD continuity, profile round-trips and the pack
  loader's reject cases.
- **Forge layer: compiles.** `gradle build` produces a reobfuscated
  `cloud-client-0.1.0.jar` (115 classes) with JDK 8 + Gradle 2.14.1 +
  ForgeGradle 2.1 against mappings `stable_22`. Every hook is a public Forge
  event; there are **no Mixins and no extra jar to install**.
- **In-game boot: not verified here.** This machine has no display, and
  `runClient` cannot finish because ForgeGradle fetches 1.8.9 assets over plain
  HTTP, which the asset host now rejects. The mod jar is real and reobfuscated,
  but nobody has seen the menu open yet.
- **Performance: unmeasured.** No FPS benchmark has been run. The optimisations
  are structurally sound (render-only culling, source-level particle rejection),
  but any specific "+30% FPS" claim would be invented, so none is made.
- **Cosmetics: not visually verified.** No OpenGL context exists here; the cape
  and wing maths is tested, the pixels are not.

## Building

1.8.9 pins the toolchain: **Gradle 2.14.x + JDK 8**, ForgeGradle 2.1.

```bash
cd cloud-client
gradle setupDecompWorkspace     # first time only
gradle build                    # jar in build/libs/
gradle runClient                # dev client, keybind: right-shift
```

No Mixin, no tweaker, no extra runtime jar: drop the built jar into `mods/`.
`gradle runClient` is provided by ForgeGradle but is not usable on a headless
box (see verification).

Run the engine tests without Minecraft:

```bash
javac --release 8 -d out $(find src/core/java tools/test -name '*.java')
java -cp out net.cloudclient.test.CoreTests
java -cp out net.cloudclient.test.SettingsTests
java -cp out net.cloudclient.test.TextureCheck
node tools/lint_sources.mjs     # unresolved imports, duplicate types, dangling module ids
```

## Launcher (connect an account, then boot)

```bash
cd launcher && ./build.sh                 # build/cloud-launcher.jar, no deps
java -jar build/cloud-launcher.jar login   # Microsoft device-code sign in
java -jar build/cloud-launcher.jar launch  # installs 1.8.9 + Forge once, copies the mod, boots
```

See [launcher/README.md](launcher/README.md) for creating the required Microsoft
client id, offline profiles, and `launch --dry-run`.

## Installing on Linux

`install.sh` finds your 1.8.9 Forge instances and copies the jars into their
`mods/` folder. It detects the official launcher (`~/.minecraft`), PrismLauncher,
MultiMC, ATLauncher and Flatpak builds of Prism/MultiMC, matching instances by
their `mmc-pack.json` / `minecraftinstance.json` (Minecraft 1.8.9 + Forge).

```bash
./scripts/build.sh                 # builds the jar, runs the engine tests first
./install.sh --list                # show detected 1.8.9 Forge instances
./install.sh                       # install into all of them
```

Useful flags:

| Flag | Meaning |
|---|---|
| `--instance DIR` | target one instance explicitly |
| `--dry-run` | print every action, change nothing |
| `--uninstall` | remove the client from the target instance(s) |

What it does, precisely:

- resolves the jar from `build/libs/`, then `libs/`, then the project root;
- checks the jar actually contains `mcmod.info` before copying it;
- backs up an identical existing jar to `*.bak` instead of overwriting silently;
- removes older `cloud-client-*.jar` versions so two builds can't load at once;
- writes `mods/.cloudclient-installed` as a marker;
- on `--uninstall`, removes only the client jar and its marker.

Nothing is written outside the instance directory, and no `sudo` is needed.

To produce a shareable tarball:

```bash
./scripts/package.sh               # dist/cloud-client-<version>-linux.tar.gz
```

## Regenerating textures

The five built-in cosmetic textures are generated, not binary blobs of unknown
origin:

```bash
node tools/gen_textures.mjs
```

`TextureCheck` verifies each file parses as PNG, has an alpha channel, and that
the cape sheet's height divides by its frame count (4), which is exactly the rule
`PackLoader` enforces on user packs.

## Architecture

```
src/core/java/net/cloudclient/     no Minecraft imports; unit-tested
  core/          event bus, module kernel, config, strict JSON
  math/          Vec3, Mat4, table-based FastMath
  cosmetic/      anchors, profile, CosmeticWorld, cape/, wings/, halo/, horns/, pet/
  pack/          .cloudpack manifest, PNG header reader, loader, registry

launcher/src/main/java/             dependency-free CLI launcher (account, install, boot)

src/main/java/net/cloudclient/     Forge 1.8.9 glue
  CloudClient    @Mod entry point
  client/        CosmeticTicker (data extraction), CullingFilter, ParticleFilter
  render/        CosmeticRenderer (batched Tessellator draws)
  hud/ gui/      HUD overlay and the right-shift module menu
  module/        cosmetic + performance module definitions
  client/        EntityCullHandler (Forge event, no Mixin)
```

Design rules that keep it maintainable:

- The core never imports Minecraft, so physics and formats stay testable.
- Modules subscribe once at init and guard with `isEnabled()`, so toggling is a
  boolean branch rather than bus churn.
- Rendering happens in `RenderWorldLastEvent` with an explicit camera offset,
  instead of injecting into entity renderers and fighting the model matrix.
- Physics uses fixed timesteps and hard finiteness repairs: a bad frame must
  never corrupt a vertex buffer.

## Licence and attribution

Original code. Built-in textures are generated by `tools/gen_textures.mjs`. The
`README`/spec references Lunar Client only as prior art; no code or assets from
Lunar, Badlion, Feather or any other client are included.
