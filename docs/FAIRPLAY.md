# Fair play

The requirement was blunt: **never get banned for an advantage.** That is a
design constraint, not a disclaimer, so here is exactly what the client does and
does not do.

## Hard rules the code enforces

| Rule | Where |
|---|---|
| The mod cannot load on a server (`clientSideOnly = true`) | `CloudClient` |
| Cosmetics are never replicated: no packets, no custom channel, no payload | `CosmeticWorld`, `CosmeticRenderer` |
| Players are never culled from rendering | `CullingFilter` |
| Dropped items are never culled | `CullingFilter` |
| Culling is render-only; entity ticking and hit detection are untouched | `EntityCullHandler` (`RenderLivingEvent.Pre`) |
| Cosmetic modules cannot alter movement, reach, hitboxes or timing | whole tree — there is no code path that writes to player state |

Cosmetics are **local to your own game**. Your cape, wings, halo, horns and pets
are visible to you. Other players do not see them, because the client never
transmits anything. That is a deliberate trade: the alternative (a cosmetic sync
channel) means custom packets on every server you join, which is precisely the
pattern anti-cheats and server rules treat as suspicious.

## What is deliberately *not* implemented

- No reach, hitbox, aim, autoclicker, velocity or timer changes.
- No packet manipulation of any kind.
- No hiding players behind walls (that would be a genuine information
  advantage).
- No "free" unlock of another client's paid cosmetics. This project ships its
  own original cosmetic engine and assets.
- No server-side component that could be used to fingerprint or track players.

## Performance options and their risk

All three are **off by default**, and none of them change game state:

1. **Entity culling** — skips drawing distant non-player entities. Visual only.
   Some servers dislike aggressive entity culling because it can hide projectiles
   at range, so keep the default distance (64 blocks) or leave it off in
   competitive play.
2. **PvP particles** — suppresses crit, potion and block-break particles at
   spawn. Purely cosmetic in vanilla, but if a server's rules forbid particle
   changes, leave it off.
3. **Dynamic render distance** — changes a video setting based on FPS. Safe, just
   potentially distracting; there is a 60-tick cooldown and hysteresis.

If you want the most conservative configuration, leave all three off. The client
still runs; you simply lose a few frames.

## Cosmetics and server rules

Some servers have cosmetic rules of their own (for example "no wings in
competitive gamemodes"). Because cosmetics are client-side only, they cannot be
detected or enforced server-side — which also means **you cannot be punished for
a packet the client never sent**, but you can still be asked to turn them off by
staff if a tournament's rules say so. Treat that as a courtesy rule, not a
technical one.

## Reporting a problem

If you ever observe the client sending traffic it should not, that is a bug:
`CosmeticRenderer` and `CosmeticTicker` contain no networking calls at all, so
any outbound traffic is either vanilla or another mod.
