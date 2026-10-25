/* ============================================================
   SILENTFIT — Face core (port of Android/core/.../watchface)
   Bindings · PAR encoder · WF writer/reader · compiler · FaceSafety.
   Keep in step with the Kotlin sources; tests compare both byte for byte.
   ============================================================ */
(function () {
  'use strict';

  const W = 240, H = 280;

  // ---------------------------------------------------------------- bindings (Binding.kt)
  // kind: number | sprite | hand; frames per glyph set; ids per record
  const GLYPHS = {
    DIGITS: ['0', '1', '2', '3', '4', '5', '6', '7', '8', '9'],
    WEEKDAYS: ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'],
    AM_PM: ['AM', 'PM'],
    PLACEHOLDER: ['--'],
  };

  const BINDINGS = {
    'hand.hour': { label: 'Hour hand', kind: 'hand', type: 1, id: 0x05 },
    'hand.minute': { label: 'Minute hand', kind: 'hand', type: 1, id: 0x06 },
    'hand.second': { label: 'Second hand', kind: 'hand', type: 1, id: 0x07 },
    'date.month': { label: 'Month', kind: 'number', type: 4, id: 0x02, sample: '10', max: 2 },
    'date.day': { label: 'Day', kind: 'number', type: 4, id: 0x03, sample: '25', max: 2 },
    'date.weekday': { label: 'Weekday', kind: 'sprite', type: 4, id: 0xFF, name: 'PICBOX1', glyphs: 'WEEKDAYS', sample: 'SAT', max: 2 },
    'time.ampm': { label: 'AM / PM', kind: 'sprite', type: 2, id: 0x1F, glyphs: 'AM_PM', sample: 'PM', max: 2 },
    'activity.steps': { label: 'Steps', kind: 'number', type: 4, id: 0x41, sample: '8204', max: 5 },
    'activity.calories': { label: 'Calories', kind: 'number', type: 4, id: 0x40, sample: '312', max: 4 },
    'health.heartRate': { label: 'Heart rate', kind: 'number', type: 4, id: 0xFF, name: 'NUMBER4', sample: '72', max: 3, placeholder: 'N_DATA4' },
    'device.battery': { label: 'Battery', kind: 'number', type: 4, id: 0xFF, name: 'NUMBER2', sample: '86', max: 3 },
    'time.hour': { label: 'Hour', kind: 'number', type: 4, id: 0x08, sample: '10', max: 2, digitIds: [0x08, 0x09] },
    'time.minute': { label: 'Minute', kind: 'number', type: 4, id: 0x0A, sample: '08', max: 2, digitIds: [0x0A, 0x0B] },
    'time.hour.tens': { label: 'Hour, first digit', kind: 'number', type: 4, id: 0x08, sample: '1', max: 1 },
    'time.hour.units': { label: 'Hour, second digit', kind: 'number', type: 4, id: 0x09, sample: '0', max: 1 },
    'time.minute.tens': { label: 'Minute, first digit', kind: 'number', type: 4, id: 0x0A, sample: '0', max: 1 },
    'time.minute.units': { label: 'Minute, second digit', kind: 'number', type: 4, id: 0x0B, sample: '8', max: 1 },
  };

  const binding = (key) => (key ? BINDINGS[key] || null : null);
  const glyphFramesOf = (el) => {
    if (Array.isArray(el.frames)) return el.frames;
    const b = binding(el.bind);
    const set = GLYPHS[(b && b.glyphs) || 'DIGITS'];
    return set.map((f) => (el.uppercase ? f.toUpperCase() : f));
  };
  const isFixedGlyph = (el) => {
    const b = binding(el.bind);
    return el.type === 'sprite' || !!(b && (b.digitIds || b.max === 1));
  };
  const glyphSlotCount = (el) => {
    const b = binding(el.bind);
    return (b && b.digitIds && b.digitIds.length) || 1;
  };
  const isLive = (el) => el.type === 'number' || el.type === 'sprite' || el.type === 'hand';

  /** Screen x of each record of a fixed-glyph element (FaceElement.glyphSlots). */
  function glyphSlots(el, glyphWidth) {
    const count = glyphSlotCount(el);
    const width = glyphWidth * count;
    const w = el.w || 0;
    const x = el.x || 0;
    let start = x;
    if (el.align === 'right' && w > 0) start = x + w - width;
    else if (el.align === 'center' && w > 0) start = x + Math.floor((w - width) / 2);
    return Array.from({ length: count }, (_, i) => start + i * glyphWidth);
  }

  // ---------------------------------------------------------------- bytes
  class Sink {
    constructor() { this.a = []; }
    get size() { return this.a.length; }
    u8(v) { this.a.push(v & 0xFF); return this; }
    u16(v) { return this.u8(v).u8(v >> 8); }
    u32(v) { return this.u8(v).u8(v >> 8).u8(v >> 16).u8(v >>> 24); }
    bytes(b) { for (const x of b) this.a.push(x); return this; }
    zeros(n) { for (let i = 0; i < n; i++) this.a.push(0); return this; }
    set(i, v) { this.a[i] = v & 0xFF; }
    done() { return Uint8Array.from(this.a); }
  }
  const u8 = (d, o) => (o >= 0 && o < d.length ? d[o] : 0);
  const u16 = (d, o) => u8(d, o) | (u8(d, o + 1) << 8);
  const s16 = (d, o) => (u16(d, o) << 16) >> 16;
  const u32 = (d, o) => (u16(d, o) | (u16(d, o + 2) << 16)) >>> 0;
  const ascii = (s) => Array.from(s, (c) => c.charCodeAt(0));

  // ---------------------------------------------------------------- PAR image encoder (Par.kt)
  const PAR_TILE = 30;

  function encodeRun(px, from, n, alpha) {
    const out = new Sink();
    const threshold = alpha ? 4 : 3;
    const ca = new Int32Array(n), cr = new Int32Array(n), cg = new Int32Array(n), cb = new Int32Array(n), c565 = new Int32Array(n);
    for (let i = 0; i < n; i++) {
      const p = px[from + i];
      let a = p >>> 24, r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
      if (!alpha) {
        r = Math.imul(Math.imul(r, a), 0x8081) >>> 23; g = Math.imul(Math.imul(g, a), 0x8081) >>> 23; b = Math.imul(Math.imul(b, a), 0x8081) >>> 23; a = 0;
      }
      ca[i] = a; cr[i] = r >> 3; cg[i] = g >> 2; cb[i] = b >> 3;
      c565[i] = (cr[i] << 11) | (cg[i] << 5) | cb[i];
    }
    let pa = 0, pr = 0, pg = 0, pb = 0, prgb = 0, run = 0, lastDiff = false, diffByte = 0, lit = 0, litPos = 0;

    const flushRun = () => {
      if (lastDiff && run < threshold) {
        out.u8(diffByte);
        for (let i = 0; i < run - 1; i++) out.u8(0x52);
        diffByte = 0x52;
      } else {
        out.u8(0x80 | run);
        if (alpha) out.u8(pa);
        out.u16(prgb);
      }
    };
    const closeLiteral = () => { if (lit > 0) { out.set(litPos, 0xC0 | lit); lit = 0; } };

    for (let i = 0; i < n; i++) {
      const a = ca[i], r = cr[i], g = cg[i], b = cb[i], c = c565[i];
      if (run > 0) {
        if (run <= 62 && a === pa && c === prgb) { run++; continue; }
        flushRun(); run = 0;
      }
      if (a === pa) {
        const sameNext = i + 1 < n && c === c565[i + 1];
        const dr = r - pr, dg = g - pg, db = b - pb;
        if (dr >= -2 && dr <= 1 && dg >= -4 && dg <= 3 && db >= -2 && db <= 1) {
          diffByte = (((dr + 2) << 5) & 0x60) | (((dg + 4) << 2) & 0x1C) | (db + 2);
          if (sameNext) run = 1; else out.u8(diffByte);
          lastDiff = true;
          closeLiteral();
          pa = a; pr = r; pg = g; pb = b; prgb = c;
          continue;
        }
        if (!sameNext) {
          if (lit === 0) { litPos = out.size; out.u8(0); }
          out.u16(c);
          lit++; lastDiff = false;
          if (lit >= 63) closeLiteral();
          pa = a; pr = r; pg = g; pb = b; prgb = c;
          continue;
        }
      }
      lastDiff = false; run = 1;
      closeLiteral();
      pa = a; pr = r; pg = g; pb = b; prgb = c;
    }
    if (run > 0) flushRun();
    closeLiteral();
    return out.done();
  }

  /** pixels: Uint32Array of 0xAARRGGBB, row-major. */
  function parEncode(pixels, width, height, alpha = false, tile = 0) {
    const chunks = [];
    if (tile > 0) {
      const size = tile + 1;
      const buf = new Uint32Array(size * size);
      for (let y = 0; y < height - 1; y += tile) {
        for (let x = 0; x < width - 1; x += tile) {
          buf.fill(0);
          for (let yy = 0; yy < Math.min(size, height - y); yy++) {
            for (let xx = 0; xx < Math.min(size, width - x); xx++) buf[yy * size + xx] = pixels[(y + yy) * width + x + xx];
          }
          chunks.push(encodeRun(buf, 0, buf.length, alpha));
        }
      }
    } else {
      for (let y = 0; y < height; y++) chunks.push(encodeRun(pixels, y * width, width, alpha));
    }
    const offsets = [0];
    chunks.forEach((c, i) => offsets.push(offsets[i] + c.length));
    const count = offsets.length;
    const average = Math.min(Math.floor(offsets[count - 1] / count), 0xFFFF);
    const deltas = offsets.map((o, i) => o - average * i);
    const lo = Math.min(...deltas), hi = Math.max(...deltas);
    const tableWidth = lo >= -128 && hi <= 127 ? 1 : lo >= -0x8000 && hi < 0x8000 ? 2 : lo >= -0x800000 && hi < 0x800000 ? 3 : 4;
    const out = new Sink();
    out.u8(0x50).u8(0x41).u8(0).u8(alpha ? 1 : 0).u16(width).u16(height).u8(tile).u8(tableWidth).u16(average).u32(0);
    for (const d of deltas) for (let b = 0; b < tableWidth; b++) out.u8(d >> (8 * b));
    chunks.forEach((c) => out.bytes(c));
    return out.done();
  }

  /** Reads a PAR header: [width, height, alpha, tile] or null. */
  function parHeader(d, off) {
    if (off < 0 || off + 16 > d.length || d[off] !== 0x50 || d[off + 1] !== 0x41) return null;
    return [u16(d, off + 4), u16(d, off + 6), u8(d, off + 3), u8(d, off + 8)];
  }

  /** Full PAR decode → { width, height, alpha, tile, pixels:Uint32Array(0xAARRGGBB), size }. */
  function parDecode(d, off) {
    const h = parHeader(d, off);
    if (!h) throw new Error('Not a PAR image');
    const [width, height, alphaFlag, tile] = h;
    const alpha = alphaFlag === 1;
    const tableWidth = u8(d, off + 9);
    const average = u16(d, off + 10);
    const count = tile === 0 ? height + 1 : Math.floor((width + tile - 2) / tile) * Math.floor((height + tile - 2) / tile) + 1;
    const table = [];
    for (let i = 0; i < count; i++) {
      let v = 0;
      for (let b = 0; b < tableWidth; b++) v += u8(d, off + 16 + i * tableWidth + b) * 2 ** (8 * b);
      const bits = 8 * tableWidth;
      if (v >= 2 ** (bits - 1)) v -= 2 ** bits;
      table.push(v + average * i);
    }
    const dataStart = off + 16 + count * tableWidth;
    const pixels = new Uint32Array(width * height);
    const e5 = (v) => ((v & 0x1F) << 3) | ((v & 0x1F) >> 2), e6 = (v) => ((v & 0x3F) << 2) | ((v & 0x3F) >> 4);
    const decodeRun = (start, n, out, outFrom) => {
      let pos = start, a = alpha ? 0 : 255, r = 0, g = 0, b = 0, i = 0;
      const put = () => { if (i < n) out[outFrom + i] = ((a << 24) | (e5(r) << 16) | (e6(g) << 8) | e5(b)) >>> 0; i++; };
      while (i < n && pos < d.length) {
        const op = d[pos++];
        if (op < 0x80) { r += ((op >> 5) & 3) - 2; g += ((op >> 2) & 7) - 4; b += (op & 3) - 2; put(); }
        else if (op < 0xC0) {
          if (alpha) a = d[pos++];
          const c = u16(d, pos); pos += 2; r = c >> 11; g = (c >> 5) & 0x3F; b = c & 0x1F;
          for (let k = 0; k < (op & 0x3F); k++) put();
        } else {
          for (let k = 0; k < (op & 0x3F); k++) { const c = u16(d, pos); pos += 2; r = c >> 11; g = (c >> 5) & 0x3F; b = c & 0x1F; put(); }
        }
      }
    };
    if (tile === 0) {
      for (let y = 0; y < height; y++) decodeRun(dataStart + table[y], width, pixels, y * width);
    } else {
      const size = tile + 1, tx = Math.floor((width + tile - 2) / tile), ty = Math.floor((height + tile - 2) / tile);
      const tmp = new Uint32Array(size * size);
      for (let j = 0; j < ty; j++) for (let i = 0; i < tx; i++) {
        decodeRun(dataStart + table[j * tx + i], size * size, tmp, 0);
        for (let yy = 0; yy < size; yy++) for (let xx = 0; xx < size; xx++) {
          const x = i * tile + xx, y = j * tile + yy;
          if (x < width && y < height) pixels[y * width + x] = tmp[yy * size + xx];
        }
      }
    }
    return { width, height, alpha, tile, pixels, size: dataStart + table[table.length - 1] - off };
  }

  // ---------------------------------------------------------------- WF container (WatchFaceFile.kt)
  const WF_HEADER = 0x34, WF_VERSION = 0x0414, SHORT_BODY = 16, LONG_BODY = 39;
  const LONG_TAIL = [0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00];
  const ANCHOR_TAG = { 1: 'extern!I', 2: 'externBy', 3: 'externci' };
  const ANCHOR_OF = { left: 1, center: 2, right: 3 };

  const equalBytes = (a, b) => a.length === b.length && a.every((v, i) => v === b[i]);
  const isLongRecord = (d, body) => body + LONG_BODY <= d.length && String.fromCharCode(...d.slice(body + SHORT_BODY, body + SHORT_BODY + 6)) === 'extern';

  function writeRecordBody(out, r) {
    out.u8(r.type).u8(r.id).u16(r.frames).u16(r.x).u16(r.y);
    if (r.type === 1) out.u16(r.pivotX || 0).u16(r.pivotY || 0).u32(r.angle == null ? 900 : r.angle);
    else if (r.type === 4) { out.u8(1); const n = ascii(r.name || '').slice(0, 7); out.bytes(n).zeros(7 - n.length); }
    else out.zeros(8);
    if (!r.compact) out.bytes(ascii(ANCHOR_TAG[r.anchor || 1])).bytes(LONG_TAIL).u8(r.anchor || 1);
  }

  /** records[].image indexes images[] (Uint8Array PAR blobs); identical blobs are stored once, in first-use order. */
  function wfEncode(width, height, records, images, headerCounts = [1, 1]) {
    const tableSize = records.reduce((s, r) => s + 4 + (r.compact ? SHORT_BODY : LONG_BODY), 0);
    let cursor = WF_HEADER + tableSize;
    const blobs = [], blobOffsets = [], byImage = new Map();
    const offsets = records.map((r) => {
      if (byImage.has(r.image)) return byImage.get(r.image);
      const bytes = images[r.image];
      let dup = blobs.findIndex((b) => equalBytes(b, bytes));
      let off;
      if (dup >= 0) off = blobOffsets[dup];
      else { off = cursor; blobs.push(bytes); blobOffsets.push(off); cursor += bytes.length; }
      byImage.set(r.image, off);
      return off;
    });
    const out = new Sink();
    out.u8(0x57).u8(0x46).u16(WF_VERSION).u32(0).u16(headerCounts[0]).u16(headerCounts[1]);
    out.zeros(0x2A - out.size).u16(width).u16(height).u16(records.length).u32(cursor);
    records.forEach((r, i) => { out.u32(offsets[i]); writeRecordBody(out, r); });
    blobs.forEach((b) => out.bytes(b));
    return out.done();
  }

  // ---------------------------------------------------------------- compiler (FaceCompiler.kt)
  const PREVIEW_W = 164, PREVIEW_H = 191;

  /**
   * rasterizer: { background(spec) → Raster, glyphs(spec, el, frames, at) → Strip, hand(spec, el) → Raster, preview(spec, w, h) → Raster }
   * Raster = { width, height, pixels:Uint32Array }, Strip = { width, frameHeight, frames, pixels }.
   */
  function compile(spec, rasterizer) {
    const images = [], records = [], warnings = [];
    const addImage = (bytes) => {
      const i = images.findIndex((b) => equalBytes(b, bytes));
      if (i >= 0) return i;
      images.push(bytes);
      return images.length - 1;
    };
    const sw = spec.width || W, sh = spec.height || H;

    const bg = rasterizer.background(spec);
    records.push({ type: 2, id: 0x10, frames: 1, x: sw >> 1, y: sh >> 1, image: addImage(parEncode(bg.pixels, bg.width, bg.height)), compact: true });
    const pv = rasterizer.preview(spec, Math.round(PREVIEW_W * sw / W), Math.round(PREVIEW_H * sh / H));
    records.push({ type: 2, id: 0x11, frames: 1, x: 0, y: 0, image: addImage(parEncode(pv.pixels, pv.width, pv.height)), compact: true });

    const hands = [];
    for (const e of spec.elements || []) {
      if (!isLive(e)) continue;
      const b = binding(e.bind);
      if (!b || e.recordId != null || e.recordType != null || e.recordName != null) {
        throw new UnsafeFaceError(`"${e.id}" uses a data id that is not proven safe on this watch`);
      }
      if (e.type === 'hand') {
        if (b.kind !== 'hand') throw new UnsafeFaceError(`"${e.id}" is a hand but is bound to ${e.bind}`);
        const r = rasterizer.hand(spec, e);
        const image = addImage(parEncode(r.pixels, r.width, r.height, true, PAR_TILE));
        hands.push({ type: 1, id: b.id, frames: 1, x: sw >> 1, y: sh >> 1, image, compact: true, src: e.id, pivotX: e.pivotX == null ? 120 : e.pivotX, pivotY: e.pivotY == null ? 140 : e.pivotY, angle: 900 });
        continue;
      }
      if (b.kind === 'hand') throw new UnsafeFaceError(`"${e.id}" shows a hand binding but is not a hand`);
      const frames = glyphFramesOf(e);
      const first = rasterizer.glyphs(spec, e, frames, null);
      if (isFixedGlyph(e)) {
        const ids = b.digitIds || [b.id];
        glyphSlots(e, first.width).forEach((x, i) => {
          const strip = rasterizer.glyphs(spec, e, frames, [x, e.y || 0]);
          const rec = { type: b.type, id: ids[i], frames: frames.length, x, y: e.y || 0, image: addImage(parEncode(strip.pixels, strip.width, strip.height)), anchor: 1, src: e.id };
          if (b.name) rec.name = b.name;
          records.push(rec);
        });
        continue;
      }
      const align = e.align || 'left';
      const anchor = ANCHOR_OF[align] || 1;
      const boxWidth = e.w > 0 ? e.w : first.width * b.max;
      const wireX = anchor === 3 ? -(sw - ((e.x || 0) + boxWidth)) : anchor === 2 ? (e.x || 0) + (boxWidth >> 1) - (sw >> 1) : (e.x || 0);
      if (b.placeholder) {
        const dash = rasterizer.glyphs(spec, e, GLYPHS.PLACEHOLDER, null);
        records.push({ type: 4, id: 0xFF, frames: 1, x: wireX, y: e.y || 0, image: addImage(parEncode(dash.pixels, dash.width, dash.height)), name: b.placeholder, anchor, src: e.id });
      }
      const rec = { type: b.type, id: b.id, frames: frames.length, x: wireX, y: e.y || 0, image: addImage(parEncode(first.pixels, first.width, first.height)), anchor, src: e.id };
      if (b.name) rec.name = b.name;
      records.push(rec);
    }
    hands.sort((a, b) => a.id - b.id).forEach((h) => records.push(h));

    if (!(spec.elements || []).some((e) => /^(hand|time)\./.test(e.bind || ''))) warnings.push('This face does not show the time');
    const bytes = wfEncode(sw, sh, records, images);
    if (bytes.length > MAX_BYTES) warnings.push(`Face is ${Math.round(bytes.length / 1024)} KB; the watch may reject files over ${MAX_BYTES / 1024} KB`);
    return { records, images, bytes, warnings };
  }

  class UnsafeFaceError extends Error {}

  // ---------------------------------------------------------------- FaceSafety.kt
  const MAX_BYTES = 400 * 1024, MAX_RECORDS = 64, MAX_GLYPH = 120, MAX_STRIP = 870;
  const PATTERNS = [
    [2, 0x10, 1, '', false], [2, 0x1F, 2, ''],
    [4, 0x02, 10, ''], [4, 0x03, 10, ''], [4, 0x40, 10, ''], [4, 0x41, 10, ''],
    [4, 0x08, 10, ''], [4, 0x09, 10, ''], [4, 0x0A, 10, ''], [4, 0x0B, 10, ''],
    [4, 0x05, 10, ''], [4, 0x06, 10, ''],
    [4, 0xFF, 7, 'PICBOX1'], [4, 0xFF, 10, 'NUMBER4'], [4, 0xFF, 1, 'N_DATA4'], [4, 0xFF, 10, 'NUMBER2'],
  ].map(([type, id, frames, name, unique = true]) => ({ type, id, frames, name, unique, key: `${type}/${id}/${frames}/${name}` }));
  const HAND_IDS = [0x05, 0x06, 0x07];

  function pointsAtThree(d, off, px, py) {
    const img = parDecode(d, off);
    const mass = [0, 0, 0, 0];
    for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) {
      const a = img.pixels[y * img.width + x] >>> 24;
      const dx = x - px, dy = y - py;
      if (a === 0 || dx * dx + dy * dy < 64) continue;
      const dir = dx > Math.abs(dy) ? 0 : -dx > Math.abs(dy) ? 1 : dy < 0 ? 2 : 3;
      mass[dir] += a;
    }
    return mass[0] > 0 && mass[0] >= 2 * Math.max(mass[1], mass[2], mass[3]);
  }

  /** Mirror of FaceSafety.check; returns the list of problems (empty = proven structures only). */
  function checkSafety(d) {
    const problems = [];
    try { inspect(d, problems); } catch (e) { problems.push(`File is damaged (${e.message})`); }
    return problems;
  }

  function inspect(d, problems) {
    if (!(d.length > WF_HEADER && d[0] === 0x57 && d[1] === 0x46)) { problems.push('Not a WF watch face'); return; }
    if (d.length > MAX_BYTES) problems.push(`File is larger than ${MAX_BYTES / 1024} KB`);
    if (u16(d, 2) !== WF_VERSION) problems.push('Unknown WF version');
    if (u32(d, 4) !== 0) problems.push('Unexpected header flags');
    const c1 = u16(d, 8), c2 = u16(d, 10);
    if (!((c1 === 1 && c2 === 1) || (c1 === 0 && c2 === 0))) problems.push('Unexpected header counts');
    for (let i = 0x0C; i < 0x2A; i++) if (d[i] !== 0) { problems.push('Unexpected header data'); break; }
    const width = u16(d, 0x2A), height = u16(d, 0x2C), count = u16(d, 0x2E);
    if (width !== W || height !== H) problems.push(`Face is ${width}×${height}; this watch needs ${W}×${H}`);
    if (u32(d, 0x30) !== d.length) problems.push('Size field does not match the file');
    if (count < 2 || count > MAX_RECORDS) { problems.push(`Unsupported number of elements (${count})`); return; }
    if (problems.length) return;

    const seen = new Set(), hands = [];
    let tableEnd = WF_HEADER, p = WF_HEADER, previousName = '';
    const ends = [];
    for (let i = 0; i < count; i++) {
      if (p + 4 + SHORT_BODY > d.length) { problems.push('Record table is truncated'); return; }
      const offset = u32(d, p), b = p + 4;
      const type = u8(d, b), id = u8(d, b + 1), frames = u16(d, b + 2), x = s16(d, b + 4), y = s16(d, b + 6);
      const compact = !isLongRecord(d, b);
      if (compact !== (i < 2 || type === 1)) { problems.push(`Element ${i + 1}: unexpected record layout`); return; }
      const bodySize = compact ? SHORT_BODY : LONG_BODY;
      if (b + bodySize > d.length) { problems.push('Record table is truncated'); return; }
      const label = `Element ${i + 1}`;
      const image = parHeader(d, offset);
      if (!image) { problems.push(`${label}: image is missing`); return; }
      const [iw, ih, alpha, tile] = image;
      const zeros = (from, n) => { for (let k = 0; k < n; k++) if (d[from + k] !== 0) return false; return true; };

      if (i === 0) {
        if (type !== 2 || id !== 0x10 || frames !== 1 || x !== W / 2 || y !== H / 2) problems.push('First element must be the background');
        if (iw !== W || ih !== H || alpha !== 0 || tile !== 0) problems.push(`Background must be an opaque ${W}×${H} image`);
        if (!zeros(b + 8, 8)) problems.push(`${label}: unexpected data`);
      } else if (i === 1) {
        if (type !== 2 || id !== 0x11 || frames !== 1 || !((x === 0 && y === 0) || (x === W / 2 && y === H / 2))) problems.push('Second element must be the preview');
        if (iw !== PREVIEW_W || ih !== PREVIEW_H || alpha !== 0 || tile !== 0) problems.push(`Preview must be an opaque ${PREVIEW_W}×${PREVIEW_H} image`);
        if (!zeros(b + 8, 8)) problems.push(`${label}: unexpected data`);
      } else if (type === 1) {
        if (!HAND_IDS.includes(id)) problems.push(`${label}: hand type is not supported on this watch`);
        else if (hands.length && id <= hands[hands.length - 1]) problems.push(`${label}: hands are out of order or repeated`);
        hands.push(id);
        if (frames !== 1 || x !== W / 2 || y !== H / 2) problems.push(`${label}: hand must be centred`);
        if (iw !== W || ih !== H || alpha !== 1 || tile !== PAR_TILE) problems.push(`${label}: hand must be a full-screen transparent image`);
        const px = u16(d, b + 8), py = u16(d, b + 10);
        if (px !== W / 2 || py !== H / 2) problems.push(`${label}: hands must turn around the screen centre`);
        if (u32(d, b + 12) !== 900) problems.push(`${label}: unexpected hand angle`);
        else if (px === W / 2 && py === H / 2 && !pointsAtThree(d, offset, px, py)) problems.push(`${label}: hand image must point at 3 o'clock`);
      } else {
        if (hands.length) problems.push(`${label}: hands must be the last elements`);
        const name = type === 4 ? String.fromCharCode(...d.slice(b + 9, b + 16)).replace(/\0+$/, '') : '';
        const key = `${type}/${id}/${frames}/${name}`;
        const pattern = PATTERNS.find((q) => q.key === key);
        if (!pattern) problems.push(`${label}: data type ${id.toString(16).toUpperCase().padStart(2, '0')}${name ? ' ' + name : ''} is not proven safe on this watch`);
        else {
          if (pattern.unique) { if (seen.has(key)) problems.push(`${label}: ${name || id.toString(16).toUpperCase().padStart(2, '0')} appears twice`); seen.add(key); }
          if (name === 'NUMBER4' && previousName !== 'N_DATA4') problems.push(`${label}: heart rate needs its no-data placeholder first`);
        }
        if (previousName === 'N_DATA4' && name !== 'NUMBER4') problems.push(`${label}: placeholder must be followed by heart rate`);
        previousName = name;
        const extra = type === 4 ? u8(d, b + 8) === 1 : zeros(b + 8, 8);
        if (!extra) problems.push(`${label}: unexpected data`);
        const anchor = u8(d, b + LONG_BODY - 1);
        const tag = String.fromCharCode(...d.slice(b + 16, b + 24));
        let tailOk = true;
        for (let k = 0; k < LONG_TAIL.length; k++) if (d[b + 24 + k] !== LONG_TAIL[k]) tailOk = false;
        if (!tailOk || ANCHOR_TAG[anchor] !== tag) problems.push(`${label}: unexpected layout data`);
        if (alpha !== 0 || tile !== 0) problems.push(`${label}: image must be opaque`);
        if (frames === 0 || ih % frames !== 0) problems.push(`${label}: image does not split into ${frames} frames`);
        const frameHeight = frames === 0 ? ih : Math.floor(ih / frames);
        if (iw < 1 || iw > MAX_GLYPH || frameHeight < 1 || frameHeight > MAX_GLYPH || ih > MAX_STRIP) problems.push(`${label}: text is too large (max ${MAX_STRIP / 10} px tall, ${MAX_GLYPH} px wide per character)`);
        if (x < -W || x > W || y < 0 || y > H - frameHeight) problems.push(`${label}: element is off screen`);
      }
      ends.push([offset, imageEnd(d, offset)]);
      p += 4 + bodySize;
      tableEnd = p;
    }
    if (previousName === 'N_DATA4') problems.push('Heart-rate placeholder is missing its number');
    for (const [offset, end] of ends) if (offset < tableEnd || end > d.length) { problems.push('Image data points outside the file'); break; }
  }

  function imageEnd(d, offset) {
    const h = parHeader(d, offset);
    if (!h) return Infinity;
    const [w, hh, , tile] = h;
    const tableWidth = u8(d, offset + 9);
    if (tableWidth < 1 || tableWidth > 4 || (tile !== 0 && tile !== PAR_TILE) || w === 0 || hh === 0) return Infinity;
    if (u8(d, offset + 2) !== 0 || u32(d, offset + 12) !== 0) return Infinity;
    return offset + parDecode(d, offset).size;
  }

  /** Decodes a WF file into records (for the inspector / import) — mirrors WatchFaceFile.decode. */
  function wfDecode(d) {
    if (!(d.length > WF_HEADER && d[0] === 0x57 && d[1] === 0x46)) throw new Error('Not a WF watch face');
    const count = u16(d, 0x2E);
    let p = WF_HEADER;
    const records = [];
    for (let i = 0; i < count; i++) {
      const offset = u32(d, p), b = p + 4;
      const long = isLongRecord(d, b);
      const type = u8(d, b);
      records.push({
        type, id: u8(d, b + 1), frames: u16(d, b + 2), x: s16(d, b + 4), y: s16(d, b + 6), offset, compact: !long,
        name: type === 4 ? String.fromCharCode(...d.slice(b + 9, b + 16)).replace(/\0+$/, '') : '',
        anchor: long ? u8(d, b + LONG_BODY - 1) : 2,
      });
      p += 4 + (long ? LONG_BODY : SHORT_BODY);
    }
    return { width: u16(d, 0x2A), height: u16(d, 0x2C), records };
  }

  window.FaceCore = {
    W, H, PREVIEW_W, PREVIEW_H, GLYPHS, BINDINGS, binding, glyphFramesOf, isFixedGlyph, glyphSlots, isLive,
    parEncode, parDecode, parHeader, wfEncode, wfDecode, compile, checkSafety, UnsafeFaceError,
    MAX_BYTES, MAX_GLYPH, MAX_STRIP,
  };
})();
