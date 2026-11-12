# Roadmap

Status of the four phases in the brief, plus the work each still needs.

## Phase 1 — Foundation

**Done**
- Module kernel: lifecycle, ordering, enable persistence, failure isolation.
- Event bus with priority + cancellation.
- Strict JSON codec and dot-path config with clamping.
- Render-only entity culling, source-level particle filtering, adaptive render
  distance (all default-off).
- Table-based fast maths for render-side trig.

**Outstanding**
- **Chunk mesh worker pool.** This is the real performance work and the reason no
  FPS number appears anywhere in this repo. It means replacing vanilla
  `RenderChunk` upload with a worker-built direct `ByteBuffer` and draining a
  concurrent queue on the render thread. High risk, high reward, not started.
- **Benchmark harness.** A reproducible stress map + frame-time capture before
  and after each optimisation, so claims are measured rather than asserted.
- **GL state cache.** Every `GlStateManager` transition during chunk and entity
  rendering should be deduplicated; needs profiling first to know if it matters
  on 1.8.9's fixed pipeline.

## Phase 2 — Core cosmetics

**Done**
- Verlet cape: 8×4 segments, 6 solver iterations, body collision, state blend
  over 0.3s, velocity-clamped and NaN-repaired.
- Wing rig: 4 bones, per-bone phase lag, mirrored wing, 5-particle/tick budget.
- Halos: ring mesh, spin, pulse, hover.
- Horns: procedural tapered tube, rigid/curved, mount transform in head space.
- Anchors: head-mounted cosmetics inherit head yaw/pitch; torso anchors do not.
- LOD: continuous 0–2 blend with crossfade alphas that sum to 1.

**Outstanding**
- Cosmetic locker UI (the module menu exists; the locker does not).
- In-game preview rendering on the player model in the menu.
- Wire the renderer up and fix any MCP mapping mismatches — the current source
  has never been compiled.
- Visual tuning: cape stiffness/wind feel, wing proportions, halo scale.

## Phase 3 — Pets and UGC

**Done**
- Pet flock: boids separation, spring-damper follow, shoulder riding, name/scale/
  tint, 4-pet cap.
- `.cloudpack` format, hardened loader, registry, limits, script rejection.
- Animated texture frame selection and generated built-in textures.

**Outstanding**
- **Vanilla mob pets.** Today every pet renders as a tinted cube. Rendering a
  real baby zombie/wolf/cat/parrot means constructing a client-side entity with
  no world and feeding it through `RenderManager`, or porting the models — both
  need care and testing.
- Pet wardrobe UI.
- Cloud Studio (web preview + OBJ/Blockbench import + keyframe editor).
- UGC hub: hosting, scanning, reporting, moderation queue, creator payouts.
- Sandboxed scripting, if it can be done safely (see `docs/CLOUDPACK.md`).

## Phase 4 — Mod suite and polish

**Done**
- FPS/CPS/keystrokes/armour HUD and a searchable module menu.

**Outstanding**
- Armour status hotbar counts, ToggleSprint/Sneak indicators, smooth zoom,
  crosshair editor, ping/TPS display.
- Scoreboard/tab customisation, waypoints.
- Friends/parties — needs a backend service and account identity. A client-only
  build cannot do this honestly.
- Replay recorder.
- Apollo-like server API: a server plugin that can disable cosmetic categories
  per gamemode. Requires a server-side project; the client has no way to receive
  such a signal today (by design, it sends nothing).
- Crash handler with mixin transformation stack capture.
- Low-end hardware regression matrix (Intel HD 4000 / 4 GB).

## Deliberate non-goals

- Unlocking or re-skinning another client's paid cosmetics.
- Any packet, movement, reach or hitbox modification.
- Cross-client cosmetic sync without an explicit, server-aware transport.

## Suggested next three steps

1. Build the project on a real 1.8.9 toolchain and fix mapping mismatches.
2. Run it in a dev world; confirm the cape hangs, the wings flap, and the keybind
   opens the menu.
3. Profile before touching performance: build the benchmark harness in Phase 1 so
   the chunk-mesh rewrite can be judged against a number.
