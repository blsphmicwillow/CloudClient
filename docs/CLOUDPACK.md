# The `.cloudpack` format (API v1)

A pack is a ZIP archive with `manifest.json` at the root.

```
my-pack.cloudpack
├── manifest.json
├── textures/
│   ├── cape.png
│   └── wings.png
├── models/
│   └── wings.json
└── README.txt
```

## Manifest

```json
{
  "id": "aurora",
  "name": "Aurora Collection",
  "version": "1.2.0",
  "author": "you",
  "description": "Animated capes and wings",
  "api": 1,
  "cosmetics": [
    {
      "id": "aurora_cape",
      "name": "Aurora Cape",
      "type": "cape",
      "texture": "textures/cape.png",
      "triangles": 900,
      "scale": 1.0,
      "animation": { "frames": 4, "fps": 10 },
      "physics": { "stiffness": 0.9, "windScale": 1.0, "flutterAmp": 0.4, "flutterFreq": 1.1 }
    }
  ]
}
```

| Field | Required | Rules |
|---|---|---|
| `id` (pack) | yes | `[a-z0-9][a-z0-9_.-]{0,63}` |
| `name` | yes | 1–64 characters |
| `version` | yes | semver-like, e.g. `1.2.0` |
| `author` | no | max 64 characters |
| `api` | yes | must be `1` |
| `cosmetics` | yes | 1–64 entries |
| `id` (cosmetic) | yes | unique within the pack |
| `type` | yes | `cape`, `wings`, `halo`, `horns`, `pet`, `hat`, `aura` |
| `texture` | yes | `.png`, must exist, max edge 2048 |
| `model` | no | `.json` or `.obj`, must exist if declared |
| `triangles` | no | must not exceed 20000 |
| `scale` | no | 0.05–4.0 |
| `animation.frames` | no | 1–64; sheet height must divide by this |
| `animation.fps` | no | 0–60 |
| `physics.*` | no | cape parameters; see defaults in `CosmeticDefinition` |

## Animated textures

Frames are stacked **vertically**, one frame per row band:

```
+-----------+
|  frame 0  |   height = frameHeight * frames
+-----------+
|  frame 1  |   width  = one frame
+-----------+
```

`height % frames == 0` is enforced at install time, because a non-divisible
sheet is the most common authoring mistake and produces visible seams once the V
coordinate starts moving.

The renderer binds the sheet once and animates by moving V, so adding frames
costs no extra draw calls.

## Hard limits (enforced by `PackLoader`)

| Limit | Value |
|---|---|
| Archive entries | 512 |
| Single entry | 8 MiB |
| Total uncompressed | 32 MiB |
| Cosmetics per pack | 64 |
| Declared triangles | 20 000 |
| Texture edge | 2048 |
| Animation frames | 64 |

## Security rules

These are rejections, not warnings:

- absolute paths (`/etc/passwd`), drive letters (`C:`), backslashes,
  `..` segments, control characters (zip-slip);
- more than the entry/size limits (zip bombs);
- malformed JSON, duplicate keys are tolerated but not relied on;
- textures that are not PNGs, or whose header contradicts their dimensions;
- **any `scripts/` directory or `.lua` file** — see below.

## Scripting: deliberately not shipped

The specification asks for a sandboxed Lua API. Shipping a script engine that
grants "rendering and local state only" is a real sandboxing project: it needs a
restricted bootstrap, an instruction budget, no `require`, no file or socket
access, and a hostile-code test corpus. None of that exists yet, so API v1
**rejects** packs containing scripts instead of silently ignoring them. A pack
that claims behaviour it will not get is worse than one that fails to install.

## Versioning

Installing a pack with an existing `id` replaces it. Equipped cosmetics store
`packId:cosmeticId`, so updating a pack keeps outfits intact as long as cosmetic
ids are stable. Never reuse an id for different content — bump it instead.
