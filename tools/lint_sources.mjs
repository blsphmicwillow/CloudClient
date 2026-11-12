#!/usr/bin/env node
/*
 * Static checks for the whole source tree.
 *
 * The Forge layer cannot be compiled without a 1.8.9 toolchain, so this catches
 * the classes of mistake a compiler would otherwise find first:
 *   - unbalanced braces/parens (truncated or merged edits)
 *   - imports of net.cloudclient.* classes that do not exist
 *   - duplicate class declarations
 *   - module ids referenced by a renderer but never registered
 *
 * It is not a substitute for compiling. It is a cheap sanity net.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = join(import.meta.dirname, '..');
const SRC = [join(ROOT, 'src', 'core', 'java'), join(ROOT, 'src', 'main', 'java')];

const files = [];
function walk(dir) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) walk(path);
    else if (path.endsWith('.java')) files.push(path);
  }
}
for (const dir of SRC) walk(dir);

const problems = [];
const classFiles = new Map(); // fqn -> file
const declared = new Map();   // simple name -> [file]

/** Strips comments and string/char literals so brace counting is meaningful. */
function strip(source) {
  let out = '';
  let i = 0;
  let mode = 'code';
  while (i < source.length) {
    const c = source[i];
    const next = source[i + 1];
    if (mode === 'code') {
      if (c === '/' && next === '/') { mode = 'line'; i += 2; continue; }
      if (c === '/' && next === '*') { mode = 'block'; i += 2; continue; }
      if (c === '"') { mode = 'string'; out += ' '; i++; continue; }
      if (c === "'") { mode = 'char'; out += ' '; i++; continue; }
      out += c; i++; continue;
    }
    if (mode === 'line') { if (c === '\n') { mode = 'code'; out += c; } i++; continue; }
    if (mode === 'block') { if (c === '*' && next === '/') { mode = 'code'; i += 2; } else i++; continue; }
    if (mode === 'string') { if (c === '\\') i += 2; else { if (c === '"') mode = 'code'; i++; } continue; }
    if (mode === 'char') { if (c === '\\') i += 2; else { if (c === "'") mode = 'code'; i++; } continue; }
  }
  return out;
}

// ---- pass 1: index every type and check balance -----------------------------
const parsed = [];
for (const file of files) {
  const raw = readFileSync(file, 'utf8');
  const code = strip(raw);
  const rel = relative(ROOT, file);
  parsed.push({ file, rel, raw, code });

  for (const [open, close, name] of [['{', '}', 'braces'], ['(', ')', 'parens'], ['[', ']', 'brackets']]) {
    const a = (code.match(new RegExp(`\\${open}`, 'g')) || []).length;
    const b = (code.match(new RegExp(`\\${close}`, 'g')) || []).length;
    if (a !== b) problems.push(`${rel}: unbalanced ${name} (${a} open, ${b} close)`);
  }

  const pkg = /package\s+([\w.]+)\s*;/.exec(code);
  if (!pkg) problems.push(`${rel}: missing package declaration`);

  for (const m of code.matchAll(/(?:public\s+|protected\s+|private\s+)?(?:static\s+)?(?:final\s+|abstract\s+)?(?:class|interface|enum|@interface)\s+(\w+)/g)) {
    const simple = m[1];
    const fqn = pkg ? `${pkg[1]}.${simple}` : simple;
    if (classFiles.has(fqn)) problems.push(`${rel}: duplicate type ${fqn} (also ${classFiles.get(fqn)})`);
    classFiles.set(fqn, rel);
    const list = declared.get(simple) || [];
    list.push(rel);
    declared.set(simple, list);
  }
}

// ---- pass 2: imports resolve, module ids exist -----------------------------
const allRaw = parsed.map((p) => p.raw).join('\n');
for (const { rel, code } of parsed) {
  for (const m of code.matchAll(/^import\s+(?:static\s+)?(net\.cloudclient\.[\w.]+);/gm)) {
    const imported = m[1];
    if (classFiles.has(imported)) continue;
    const outer = imported.slice(0, imported.lastIndexOf('.'));
    if (classFiles.has(outer)) continue;              // nested type
    if (declared.has(imported.slice(imported.lastIndexOf('.') + 1))) continue;
    problems.push(`${rel}: import does not resolve: ${imported}`);
  }
}

const referenced = new Set();
for (const m of allRaw.matchAll(/isEnabled\("([\w.]+)"\)/g)) referenced.add(m[1]);
const defined = new Set();
for (const m of allRaw.matchAll(/Toggle\("([\w.]+)"/g)) defined.add(m[1]);
for (const id of referenced) {
  if (!defined.has(id)) problems.push(`module id referenced but never registered: ${id}`);
}

console.log(`scanned ${files.length} java files, ${classFiles.size} types`);
console.log(`module ids: ${defined.size} defined, ${referenced.size} referenced`);
if (problems.length) {
  console.log('\nPROBLEMS:');
  for (const p of problems) console.log('  - ' + p);
  process.exit(1);
}
console.log('static checks passed');
