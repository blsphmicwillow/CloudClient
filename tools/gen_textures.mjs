#!/usr/bin/env node
/*
 * Generates the built-in cosmetic textures as real, valid PNG files.
 *
 * No image library: PNG is a simple format (IHDR + deflated scanlines + IEND),
 * and writing it directly keeps the repo free of build-time dependencies.
 * Frame counts matter: the cape sheet is 4 frames stacked vertically because
 * AnimatedTexture expects height % frames == 0.
 *
 *   node tools/gen_textures.mjs
 */
import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

const OUT = join(import.meta.dirname, '..', 'src', 'main', 'resources', 'assets',
  'cloudclient', 'textures', 'cosmetics');

// ------------------------------------------------------------------ PNG writer

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c;
  }
  return table;
})();

function crc32(buf) {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([length, body, crc]);
}

function encodePng(width, height, pixelAt) {
  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (stride + 1)] = 0; // filter: none
    for (let x = 0; x < width; x++) {
      const [r, g, b, a] = pixelAt(x, y);
      const o = y * (stride + 1) + 1 + x * 4;
      raw[o] = r; raw[o + 1] = g; raw[o + 2] = b; raw[o + 3] = a;
    }
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;   // bit depth
  ihdr[9] = 6;   // colour type: RGBA
  ihdr[10] = 0;  // compression
  ihdr[11] = 0;  // filter
  ihdr[12] = 0;  // interlace

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ------------------------------------------------------------------ helpers

const clamp255 = (v) => Math.max(0, Math.min(255, Math.round(v)));
const mix = (a, b, t) => a + (b - a) * t;

function noise(x, y, seed) {
  // Cheap deterministic hash noise so textures are reproducible.
  let h = x * 374761393 + y * 668265263 + seed * 1442695040888963407;
  h = (h ^ (h >> 13)) * 1274126177;
  return ((h ^ (h >> 16)) >>> 0) / 4294967295;
}

// ------------------------------------------------------------------ textures

/** 64x64 per frame, 4 frames: a cloud gradient with drifting highlights. */
function capeSheet() {
  const size = 64;
  const frames = 4;
  return encodePng(size, size * frames, (x, y) => {
    const frame = Math.floor(y / size);
    const fy = y % size;
    const shift = (frame / frames) * 12;
    const wave = Math.sin((x + shift) * 0.16 + fy * 0.09) * 10;
    const cloud = Math.sin(x * 0.09 + fy * 0.05 + shift * 0.2) * 0.5 + 0.5;
    const n = noise(x, fy, 7) * 18;
    const base = 150 + cloud * 60 + wave + n;
    return [clamp255(base * 0.72), clamp255(base * 0.86), clamp255(base * 1.02), 255];
  });
}

/** Ember wing membrane: hot core fading to dark translucent edges. */
function wings() {
  return encodePng(64, 64, (x, y) => {
    const dx = (x - 32) / 32;
    const dy = (y - 40) / 32;
    const d = Math.sqrt(dx * dx + dy * dy);
    const glow = Math.max(0, 1 - d) ** 1.6;
    const streak = Math.sin(x * 0.5 + y * 0.18) * 0.15 + 0.85;
    return [
      clamp255(mix(60, 255, glow) * streak),
      clamp255(mix(16, 160, glow) * streak),
      clamp255(mix(8, 60, glow) * streak),
      clamp255(200 + glow * 55),
    ];
  });
}

/** Gold halo: bright band with a soft falloff, transparent corners. */
function halo() {
  return encodePng(64, 64, (x, y) => {
    const dx = (x - 32) / 32;
    const dy = (y - 32) / 32;
    const d = Math.sqrt(dx * dx + dy * dy);
    const band = Math.exp(-((d - 0.72) ** 2) / 0.012);
    const core = Math.exp(-(d ** 2) / 0.35) * 0.25;
    const a = clamp255((band + core) * 255);
    return [clamp255(255 * (band + core * 0.6)), clamp255(226 * (band + core * 0.5)),
      clamp255(140 * (band + core * 0.3)), a];
  });
}

/** Obsidian horn: dark ridged striations. */
function horns() {
  return encodePng(64, 64, (x, y) => {
    const ridge = Math.sin(y * 0.55 + Math.sin(x * 0.3) * 1.4) * 0.5 + 0.5;
    const shade = 0.35 + ridge * 0.4;
    return [clamp255(48 * shade + 18), clamp255(38 * shade + 14),
      clamp255(62 * shade + 26), 255];
  });
}

/** Pet spark: warm glowing orb. */
function pet() {
  return encodePng(64, 64, (x, y) => {
    const dx = (x - 32) / 32;
    const dy = (y - 32) / 32;
    const d = Math.sqrt(dx * dx + dy * dy);
    const glow = Math.max(0, 1 - d) ** 2.2;
    return [clamp255(mix(120, 255, glow)), clamp255(mix(90, 230, glow)),
      clamp255(mix(200, 255, glow)), clamp255(glow * 255)];
  });
}

const files = [
  ['cape_cloud.png', capeSheet()],
  ['wings_ember.png', wings()],
  ['halo_gold.png', halo()],
  ['horns_obsidian.png', horns()],
  ['pet_spark.png', pet()],
];

mkdirSync(OUT, { recursive: true });
for (const [name, data] of files) {
  const path = join(OUT, name);
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, data);
  console.log(`${name}: ${data.length} bytes`);
}
