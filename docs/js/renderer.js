/* ============================================================
   SILENTFIT — Face renderer
   Faithful port of AndroidFaceRasterizer.kt to Canvas 2D, so the browser
   draws exactly what the app compiles for the watch.
   Exposes window.WatchFaceRenderer and window.FaceRasterizer.
   ============================================================ */
(function () {
  'use strict';
  const C = window.FaceCore;
  const { binding, glyphFramesOf, isFixedGlyph, glyphSlots } = C;

  // ---------------------------------------------------------------- tables
  const SEGMENTS = {
    '0': 0b0111111, '1': 0b0000110, '2': 0b1011011, '3': 0b1001111, '4': 0b1100110,
    '5': 0b1101101, '6': 0b1111101, '7': 0b0000111, '8': 0b1111111, '9': 0b1101111, '-': 0b1000000, ' ': 0,
  };

  const DOT_FONT_SRC = {
    '0': '01110 10001 10011 10101 11001 10001 01110', '1': '00100 01100 00100 00100 00100 00100 01110',
    '2': '01110 10001 00001 00010 00100 01000 11111', '3': '11111 00010 00100 00010 00001 10001 01110',
    '4': '00010 00110 01010 10010 11111 00010 00010', '5': '11111 10000 11110 00001 00001 10001 01110',
    '6': '00110 01000 10000 11110 10001 10001 01110', '7': '11111 00001 00010 00100 01000 01000 01000',
    '8': '01110 10001 10001 01110 10001 10001 01110', '9': '01110 10001 10001 01111 00001 00010 01100',
    'A': '01110 10001 10001 10001 11111 10001 10001', 'B': '11110 10001 10001 11110 10001 10001 11110',
    'C': '01110 10001 10000 10000 10000 10001 01110', 'D': '11100 10010 10001 10001 10001 10010 11100',
    'E': '11111 10000 10000 11110 10000 10000 11111', 'F': '11111 10000 10000 11110 10000 10000 10000',
    'G': '01110 10001 10000 10111 10001 10001 01111', 'H': '10001 10001 10001 11111 10001 10001 10001',
    'I': '01110 00100 00100 00100 00100 00100 01110', 'J': '00111 00010 00010 00010 00010 10010 01100',
    'K': '10001 10010 10100 11000 10100 10010 10001', 'L': '10000 10000 10000 10000 10000 10000 11111',
    'M': '10001 11011 10101 10101 10001 10001 10001', 'N': '10001 10001 11001 10101 10011 10001 10001',
    'O': '01110 10001 10001 10001 10001 10001 01110', 'P': '11110 10001 10001 11110 10000 10000 10000',
    'Q': '01110 10001 10001 10001 10101 10010 01101', 'R': '11110 10001 10001 11110 10100 10010 10001',
    'S': '01111 10000 10000 01110 00001 00001 11110', 'T': '11111 00100 00100 00100 00100 00100 00100',
    'U': '10001 10001 10001 10001 10001 10001 01110', 'V': '10001 10001 10001 10001 10001 01010 00100',
    'W': '10001 10001 10001 10101 10101 10101 01010', 'X': '10001 10001 01010 00100 01010 10001 10001',
    'Y': '10001 10001 10001 01010 00100 00100 00100', 'Z': '11111 00001 00010 00100 01000 10000 11111',
    '-': '00000 00000 00000 11111 00000 00000 00000', ':': '00000 01100 01100 00000 01100 01100 00000',
    '.': '00000 00000 00000 00000 00000 01100 01100', '%': '11000 11001 00010 00100 01000 10011 00011',
    '/': '00000 00001 00010 00100 01000 10000 00000', ' ': '00000 00000 00000 00000 00000 00000 00000',
  };
  const DOT_FONT = {};
  for (const [k, v] of Object.entries(DOT_FONT_SRC)) DOT_FONT[k] = v.split(' ').map((r) => parseInt(r, 2));

  const FILLABLE = new Set(['rect', 'circle']);
  const FONT_FAMILIES = { doto: '"Doto"', 'space-grotesk': '"Space Grotesk"', 'space-mono': '"Space Mono"' };

  // ---------------------------------------------------------------- colour
  /** Parses #RGB, #RRGGBB, #AARRGGBB → {r,g,b,a(0..255)} like parseColor in FaceSpec.kt. */
  function parseColor(s, fallback = { r: 255, g: 255, b: 255, a: 255 }) {
    const h = String(s == null ? '' : s).replace(/^#/, '');
    if (!/^[0-9a-fA-F]+$/.test(h)) return fallback;
    if (h.length === 3) return { r: parseInt(h[0] + h[0], 16), g: parseInt(h[1] + h[1], 16), b: parseInt(h[2] + h[2], 16), a: 255 };
    if (h.length === 6) return { r: parseInt(h.slice(0, 2), 16), g: parseInt(h.slice(2, 4), 16), b: parseInt(h.slice(4, 6), 16), a: 255 };
    if (h.length === 8) return { a: parseInt(h.slice(0, 2), 16), r: parseInt(h.slice(2, 4), 16), g: parseInt(h.slice(4, 6), 16), b: parseInt(h.slice(6, 8), 16) };
    return fallback;
  }
  const css = (c, alpha = 1) => `rgba(${c.r},${c.g},${c.b},${(c.a / 255) * alpha})`;
  const colorToCSS = (s) => css(parseColor(s));

  // ---------------------------------------------------------------- spec defaults (FaceElement.kt)
  const num = (v, d) => (typeof v === 'number' && isFinite(v) ? v : d);
  function el(e) {
    return {
      ...e,
      x: num(e.x, 0), y: num(e.y, 0), w: num(e.w, 0), h: num(e.h, 0),
      font: e.font || 'space-mono', size: num(e.size, 16), weight: num(e.weight, 400), color: e.color || '#FFFFFF',
      align: e.align || 'left', tracking: num(e.tracking, 0), uppercase: !!e.uppercase,
      stroke: num(e.stroke, 2), fill: e.fill !== false, startAngle: num(e.startAngle, 0), sweep: num(e.sweep, 360),
      count: num(e.count, 12), length: num(e.length, 80), tail: num(e.tail, 12), handStyle: e.handStyle || 'bar',
      pivotX: num(e.pivotX, 120), pivotY: num(e.pivotY, 140), opacity: num(e.opacity, 1),
      glyph: e.glyph || 'font', rotation: num(e.rotation, 0),
    };
  }
  const bgOf = (spec) => {
    const b = spec.background || {};
    return { color: b.color || '#000000', image: b.image || null, pattern: b.pattern || 'none', patternColor: b.patternColor || '#222222', patternSpacing: num(b.patternSpacing, 12) };
  };
  const dims = (spec) => [num(spec.width, 240), num(spec.height, 280)];
  const isLive = (e) => e.type === 'number' || e.type === 'sprite' || e.type === 'hand';

  // ---------------------------------------------------------------- canvas helpers
  function makeCanvas(w, h) {
    const c = document.createElement('canvas');
    c.width = Math.max(1, w); c.height = Math.max(1, h);
    return c;
  }
  const ctx2d = (c) => c.getContext('2d', { willReadFrequently: true });

  function toRaster(canvas) {
    const { width, height } = canvas;
    const d = ctx2d(canvas).getImageData(0, 0, width, height).data;
    const px = new Uint32Array(width * height);
    for (let i = 0; i < px.length; i++) px[i] = ((d[i * 4 + 3] << 24) | (d[i * 4] << 16) | (d[i * 4 + 1] << 8) | d[i * 4 + 2]) >>> 0;
    return { width, height, pixels: px };
  }

  // ---------------------------------------------------------------- assets (base64 images)
  const assetCache = new Map();
  let onAssetReady = null;
  const assetKey = (name, data) => `${name}:${data.length}:${data.slice(-24)}`;
  function asset(spec, name) {
    const data = spec.assets && spec.assets[name];
    if (!data) return null;
    const key = assetKey(name, data);
    let entry = assetCache.get(key);
    if (!entry) {
      const img = new Image();
      entry = { img, ready: false };
      img.onload = () => { entry.ready = true; if (onAssetReady) onAssetReady(); };
      img.src = /^data:/.test(data) ? data : `data:image/png;base64,${data}`;
      assetCache.set(key, entry);
    }
    return entry.ready ? entry.img : null;
  }
  /** Resolves when every asset of the spec has decoded (call before compiling or exporting). */
  function preloadAssets(spec) {
    return Promise.all(Object.keys(spec.assets || {}).map((n) => {
      asset(spec, n);
      const entry = assetCache.get(assetKey(n, spec.assets[n]));
      return entry.ready ? null : entry.img.decode().then(() => { entry.ready = true; }).catch(() => {});
    }));
  }

  // ---------------------------------------------------------------- text metrics
  function fontCss(e) {
    const family = FONT_FAMILIES[e.font] || FONT_FAMILIES['space-mono'];
    const weight = e.font === 'doto' || e.font === 'space-grotesk' ? e.weight : (e.weight >= 600 ? 700 : 400);
    return `${weight} ${e.size}px ${family}, monospace`;
  }
  let scratch = null;
  function measure(e, text) {
    if (!scratch) scratch = ctx2d(makeCanvas(4, 4));
    scratch.font = fontCss(e);
    scratch.letterSpacing = `${e.tracking * e.size}px`;
    const m = scratch.measureText(text);
    return { width: m.width, ascent: m.fontBoundingBoxAscent, descent: m.fontBoundingBoxDescent };
  }

  const rotationOf = (e) => (e.type === 'number' ? 0 : Math.floor((((e.rotation % 360) + 360) % 360) / 90) * 90);
  const dotPitch = (e) => Math.max(1.5, e.size / 7);
  const segWidth = (e) => e.size * 0.56;
  const segGap = (e) => e.size * 0.14;
  const isSegmentChar = (c) => /[0-9\- ]/.test(c);
  const advance = (e, c) => (e.glyph === 'segment' && isSegmentChar(c) ? segWidth(e) : e.glyph === 'segment' && c === ':' ? e.size * 0.2 : dotPitch(e) * 5);
  function styledWidth(e, text) {
    if (!text.length) return 0;
    const gap = e.glyph === 'segment' ? segGap(e) : dotPitch(e);
    let w = 0;
    for (const c of text) w += advance(e, c);
    return w + gap * (text.length - 1);
  }

  /** Unrotated size of one frame that fits every string in frames. */
  function cellSize(e, frames) {
    let w, h;
    if (e.glyph === 'dot-matrix') {
      w = Math.ceil(Math.max(...frames.map((t) => styledWidth(e, t.toUpperCase())))) + 2;
      h = Math.ceil(dotPitch(e) * 7) + 2;
    } else if (e.glyph === 'segment') {
      w = Math.ceil(Math.max(...frames.map((t) => styledWidth(e, t.toUpperCase())))) + 2;
      h = Math.ceil(e.size) + 2;
    } else {
      w = Math.ceil(Math.max(...frames.map((t) => measure(e, t).width))) + 1;
      const m = measure(e, 'M');
      h = Math.ceil(m.ascent + m.descent);
    }
    return [Math.max(1, w), Math.max(1, h)];
  }

  function textLeft(e, width) {
    if (e.align === 'center') return e.w > 0 ? e.x + (e.w - width) / 2 : e.x - width / 2;
    if (e.align === 'right') return e.w > 0 ? e.x + e.w - width : e.x - width;
    return e.x;
  }

  function applyRotation(ctx, rot, cw, ch) {
    if (rot === 90) { ctx.translate(ch, 0); ctx.rotate(Math.PI / 2); }
    else if (rot === 180) { ctx.translate(cw, ch); ctx.rotate(Math.PI); }
    else if (rot === 270) { ctx.translate(0, cw); ctx.rotate(-Math.PI / 2); }
  }

  const circle = (ctx, x, y, r) => { ctx.beginPath(); ctx.arc(x, y, Math.max(0, r), 0, Math.PI * 2); ctx.fill(); };
  function rrect(ctx, x, y, w, h, r) {
    const rr = Math.max(0, Math.min(r, Math.abs(w) / 2, Math.abs(h) / 2));
    ctx.beginPath();
    ctx.moveTo(x + rr, y); ctx.lineTo(x + w - rr, y); ctx.arcTo(x + w, y, x + w, y + rr, rr);
    ctx.lineTo(x + w, y + h - rr); ctx.arcTo(x + w, y + h, x + w - rr, y + h, rr);
    ctx.lineTo(x + rr, y + h); ctx.arcTo(x, y + h, x, y + h - rr, rr);
    ctx.lineTo(x, y + rr); ctx.arcTo(x, y, x + rr, y, rr); ctx.closePath();
  }

  // ---------------------------------------------------------------- glyph drawing
  function drawDots(ctx, c, left, top, p, lit, ghost) {
    const rows = DOT_FONT[c] || DOT_FONT[' '];
    const r = Math.max(0.6, p * 0.42);
    for (let row = 0; row < 7; row++) for (let col = 0; col < 5; col++) {
      const paint = (rows[row] >> (4 - col)) & 1 ? lit : ghost;
      if (!paint) continue;
      ctx.fillStyle = paint;
      circle(ctx, left + col * p + p / 2, top + row * p + p / 2, r);
    }
  }

  function drawSegments(ctx, c, left, top, w, h, lit, ghost) {
    const mask = SEGMENTS[c] || 0;
    const t = h * 0.13, inset = t * 0.6, mid = top + h / 2;
    const boxes = [
      [left + inset, top, left + w - inset, top + t],
      [left + w - t, top + inset, left + w, mid - t * 0.35],
      [left + w - t, mid + t * 0.35, left + w, top + h - inset],
      [left + inset, top + h - t, left + w - inset, top + h],
      [left, mid + t * 0.35, left + t, top + h - inset],
      [left, top + inset, left + t, mid - t * 0.35],
      [left + inset, mid - t / 2, left + w - inset, mid + t / 2],
    ];
    boxes.forEach(([l, tp, r, b], i) => {
      const paint = (mask >> i) & 1 ? lit : ghost;
      if (!paint) return;
      ctx.fillStyle = paint;
      rrect(ctx, l, tp, r - l, b - tp, t / 2);
      ctx.fill();
    });
  }

  /** Draws text centred in an unrotated cw×ch cell at the origin. */
  function drawGlyphText(ctx, e, text, cw, ch) {
    if (e.glyph !== 'dot-matrix' && e.glyph !== 'segment') {
      const m = measure(e, text);
      ctx.font = fontCss(e);
      ctx.letterSpacing = `${e.tracking * e.size}px`;
      ctx.textBaseline = 'alphabetic';
      ctx.fillStyle = css(parseColor(e.color));
      ctx.fillText(text, (cw - m.width) / 2, m.ascent);
      return;
    }
    const t = text.toUpperCase();
    const lit = css(parseColor(e.color));
    const ghost = e.ghost ? css(parseColor(e.ghost)) : null;
    const gap = e.glyph === 'segment' ? segGap(e) : dotPitch(e);
    let x = (cw - styledWidth(e, t)) / 2;
    for (const c of t) {
      if (e.glyph === 'segment' && isSegmentChar(c)) drawSegments(ctx, c, x, 1, segWidth(e), e.size, lit, ghost);
      else if (e.glyph === 'segment' && c === ':') {
        const r = e.size * 0.065;
        ctx.fillStyle = lit;
        circle(ctx, x + e.size * 0.1, 1 + e.size * 0.3, r);
        circle(ctx, x + e.size * 0.1, 1 + e.size * 0.7, r);
      } else drawDots(ctx, c, x, (ch - dotPitch(e) * 7) / 2, dotPitch(e), lit, ghost);
      x += advance(e, c) + gap;
    }
  }

  // ---------------------------------------------------------------- statics
  function drawPattern(ctx, spec) {
    const b = bgOf(spec);
    if (b.pattern === 'none') return;
    const [W, H] = dims(spec);
    const col = css(parseColor(b.patternColor, { r: 34, g: 34, b: 34, a: 255 }));
    const step = Math.min(64, Math.max(4, b.patternSpacing));
    ctx.fillStyle = col; ctx.strokeStyle = col; ctx.lineWidth = 1;
    if (b.pattern === 'dots') {
      for (let y = step / 2; y < H; y += step) for (let x = step / 2; x < W; x += step) circle(ctx, x, y, 1);
    } else if (b.pattern === 'grid') {
      ctx.beginPath();
      for (let x = 0; x <= W; x += step) { ctx.moveTo(x, 0); ctx.lineTo(x, H); }
      for (let y = 0; y <= H; y += step) { ctx.moveTo(0, y); ctx.lineTo(W, y); }
      ctx.stroke();
    } else if (b.pattern === 'lines') {
      ctx.beginPath();
      for (let y = step / 2; y < H; y += step) { ctx.moveTo(0, y); ctx.lineTo(W, y); }
      ctx.stroke();
    }
  }

  function drawCover(ctx, img, w, h) {
    const scale = Math.max(w / img.width, h / img.height);
    const sw = w / scale, sh = h / scale;
    ctx.imageSmoothingEnabled = true;
    ctx.drawImage(img, Math.trunc((img.width - sw) / 2), Math.trunc((img.height - sh) / 2), Math.trunc(sw), Math.trunc(sh), 0, 0, w, h);
  }

  function drawShape(ctx, e, alpha) {
    const color = parseColor(e.color);
    const paint = css(color, alpha);
    const filled = e.fill && FILLABLE.has(e.shape);
    const l = e.x, t = e.y, rgt = e.x + e.w, btm = e.y + e.h, w = e.w, h = e.h;
    const cx = (l + rgt) / 2, cy = (t + btm) / 2, r = Math.min(w, h) / 2;
    ctx.fillStyle = paint; ctx.strokeStyle = paint; ctx.lineWidth = e.stroke; ctx.lineCap = 'butt';
    const rad = (d) => (d * Math.PI) / 180;
    switch (e.shape) {
      case 'rect':
        rrect(ctx, l, t, w, h, Math.min(e.size, r));
        if (filled) ctx.fill(); else ctx.stroke();
        break;
      case 'circle':
        ctx.beginPath(); ctx.arc(cx, cy, Math.max(0, filled ? r : r - e.stroke / 2), 0, Math.PI * 2);
        if (filled) ctx.fill(); else ctx.stroke();
        break;
      case 'ring':
        ctx.beginPath(); ctx.arc(cx, cy, Math.max(0, r - e.stroke / 2), 0, Math.PI * 2); ctx.stroke();
        break;
      case 'arc':
        ctx.beginPath(); ctx.arc(cx, cy, Math.max(0, r - e.stroke / 2), rad(e.startAngle - 90), rad(e.startAngle - 90 + e.sweep)); ctx.stroke();
        break;
      case 'line':
        ctx.beginPath(); ctx.moveTo(l, t); ctx.lineTo(rgt, btm); ctx.stroke();
        break;
      case 'ticks': {
        const n = Math.min(240, Math.max(1, e.count));
        for (let i = 0; i < n; i++) {
          const a = rad(e.startAngle + (i * e.sweep) / n - 90);
          const major = n >= 24 && i % Math.trunc(n / 12) === 0;
          const inner = r - (major ? e.size * 1.8 : e.size);
          ctx.lineWidth = major ? e.stroke * 1.5 : e.stroke;
          ctx.beginPath();
          ctx.moveTo(cx + Math.cos(a) * inner, cy + Math.sin(a) * inner);
          ctx.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
          ctx.stroke();
        }
        break;
      }
      case 'dot-ring': {
        const n = Math.min(240, Math.max(1, e.count));
        const dot = e.stroke, rr = r - dot * 1.5;
        for (let i = 0; i < n; i++) {
          const a = rad(e.startAngle + (i * e.sweep) / n - 90);
          const major = n >= 24 && i % Math.trunc(n / 12) === 0;
          circle(ctx, cx + Math.cos(a) * rr, cy + Math.sin(a) * rr, major ? dot * 1.6 : dot);
        }
        break;
      }
      case 'segments': {
        const n = Math.min(120, Math.max(1, e.count));
        const gap = 2, sw = (w - gap * (n - 1)) / n;
        const lit = Math.round(n * (e.sweep / 360));
        for (let i = 0; i < n; i++) {
          ctx.fillStyle = css(color, i < lit ? alpha : alpha / 4);
          ctx.fillRect(l + i * (sw + gap), t, sw, h);
        }
        break;
      }
      case 'dot-matrix': {
        const step = e.stroke * 3;
        for (let y = t + step / 2; y < btm; y += step) for (let x = l + step / 2; x < rgt; x += step) circle(ctx, x, y, e.stroke);
        break;
      }
      case 'halftone': {
        const pitch = Math.max(4, e.size);
        for (let y = t + pitch / 2; y < btm; y += pitch) for (let x = l + pitch / 2; x < rgt; x += pitch) {
          const dotR = e.stroke * (1 - Math.hypot(x - cx, y - cy) / r);
          if (dotR >= 0.9) circle(ctx, x, y, dotR);
        }
        break;
      }
      default: break;
    }
  }

  function drawStatic(ctx, spec, raw) {
    const e = el(raw);
    const alpha = Math.round(Math.min(1, Math.max(0, e.opacity)) * 255) / 255;
    ctx.save();
    if (e.type === 'text') {
      ctx.globalAlpha = alpha;
      const text = e.uppercase ? (e.text || '').toUpperCase() : (e.text || '');
      const [cw, ch] = cellSize(e, [text]);
      const rot = rotationOf(e);
      ctx.translate(textLeft(e, rot % 180 === 90 ? ch : cw), e.y);
      applyRotation(ctx, rot, cw, ch);
      drawGlyphText(ctx, e, text, cw, ch);
    } else if (e.type === 'image') {
      const img = e.src ? asset(spec, e.src) : null;
      if (img) { ctx.globalAlpha = alpha; ctx.imageSmoothingEnabled = true; ctx.drawImage(img, e.x, e.y, Math.max(1, e.w), Math.max(1, e.h)); }
    } else if (e.type === 'shape') {
      drawShape(ctx, e, alpha);
    }
    ctx.restore();
  }

  function drawBackground(ctx, spec) {
    const [W, H] = dims(spec);
    const b = bgOf(spec);
    ctx.save();
    ctx.fillStyle = css(parseColor(b.color, { r: 0, g: 0, b: 0, a: 255 }));
    ctx.fillRect(0, 0, W, H);
    const img = b.image ? asset(spec, b.image) : null;
    if (img) drawCover(ctx, img, W, H);
    drawPattern(ctx, spec);
    ctx.restore();
    (spec.elements || []).filter((e) => !isLive(e) && e.hidden !== true).forEach((e) => drawStatic(ctx, spec, e));
  }

  // ---------------------------------------------------------------- hands
  function drawHand(ctx, raw, degrees) {
    const e = el(raw);
    const px = e.pivotX, py = e.pivotY, len = e.length, tail = e.tail, w = e.stroke;
    const col = css(parseColor(e.color));
    ctx.save();
    ctx.translate(px, py); ctx.rotate(((degrees - 90) * Math.PI) / 180); ctx.translate(-px, -py);
    ctx.fillStyle = col; ctx.strokeStyle = col; ctx.lineCap = 'round';
    switch (e.handStyle) {
      case 'needle':
        ctx.beginPath(); ctx.moveTo(px - tail, py - w / 2); ctx.lineTo(px + len, py); ctx.lineTo(px - tail, py + w / 2); ctx.closePath(); ctx.fill();
        break;
      case 'line':
        ctx.lineWidth = w; ctx.beginPath(); ctx.moveTo(px - tail, py); ctx.lineTo(px + len, py); ctx.stroke();
        break;
      case 'dot': circle(ctx, px + len, py, w); break;
      case 'dots': {
        const step = w * 2.6;
        for (let d = step; d <= len; d += step) circle(ctx, px + d, py, w / 2 + 0.5);
        break;
      }
      case 'outline':
        ctx.lineWidth = 2; rrect(ctx, px + tail, py - w / 2, len - tail, w, w / 2); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(px, py); ctx.lineTo(px + tail, py); ctx.stroke();
        break;
      default: rrect(ctx, px - tail, py - w / 2, len + tail, w, w / 2); ctx.fill();
    }
    if (e.size > 0 && e.handStyle !== 'dot') circle(ctx, px, py, e.size / 4);
    ctx.restore();
  }

  // ---------------------------------------------------------------- live glyphs
  let bgCache = null;
  function backgroundCanvas(spec) {
    const ready = [...assetCache.values()].filter((a) => a.ready).length;
    const key = JSON.stringify([spec.background, (spec.elements || []).filter((e) => !isLive(e) && e.hidden !== true), spec.width, spec.height, ready]);
    if (bgCache && bgCache.key === key) return bgCache.canvas;
    const [W, H] = dims(spec);
    const canvas = makeCanvas(W, H);
    drawBackground(ctx2d(canvas), spec);
    bgCache = { key, canvas };
    return canvas;
  }

  function backgroundColorBehind(spec, e, fw, fh) {
    const b = bgOf(spec);
    if (!b.image && b.pattern === 'none' && !(spec.elements || []).some((o) => !isLive(o) && o.type !== 'text')) {
      const c = parseColor(b.color, { r: 0, g: 0, b: 0, a: 255 });
      return `rgb(${c.r},${c.g},${c.b})`;
    }
    const [W, H] = dims(spec);
    const x = Math.min(W - 1, Math.max(0, e.align === 'right' ? e.x + Math.max(e.w, fw) - (fw >> 1) : e.x + (fw >> 1)));
    const y = Math.min(H - 1, Math.max(0, e.y + (fh >> 1)));
    const d = ctx2d(backgroundCanvas(spec)).getImageData(x, y, 1, 1).data;
    return `rgb(${d[0]},${d[1]},${d[2]})`;
  }

  function glyphs(spec, raw, frames, at) {
    const e = el(raw);
    const [cw, ch] = cellSize(e, frames);
    const rot = rotationOf(e);
    const [fw, fh] = rot % 180 === 90 ? [ch, cw] : [cw, ch];
    const canvas = makeCanvas(fw, fh * frames.length);
    const ctx = ctx2d(canvas);
    if (!at) { ctx.fillStyle = backgroundColorBehind(spec, e, fw, fh); ctx.fillRect(0, 0, canvas.width, canvas.height); }
    frames.forEach((text, i) => {
      const top = i * fh;
      ctx.save();
      ctx.beginPath(); ctx.rect(0, top, fw, fh); ctx.clip();
      if (at) {
        ctx.fillStyle = '#000'; ctx.fillRect(0, top, fw, fh);
        ctx.drawImage(backgroundCanvas(spec), -at[0], top - at[1]);
      }
      ctx.translate(0, top);
      applyRotation(ctx, rot, cw, ch);
      drawGlyphText(ctx, e, text, cw, ch);
      ctx.restore();
    });
    const r = toRaster(canvas);
    // Live glyph images are opaque on the watch.
    for (let i = 0; i < r.pixels.length; i++) r.pixels[i] = (r.pixels[i] | 0xFF000000) >>> 0;
    return { width: fw, frameHeight: fh, frames: frames.length, pixels: r.pixels, canvas };
  }

  // ---------------------------------------------------------------- full render
  const SAMPLE = { hour: 10, minute: 8, second: 36 };
  function sampleValue(e, t) {
    const b = binding(e.bind);
    if (!b) return '8';
    if (e.bind === 'time.hour') return String(t.hour).padStart(2, '0');
    if (e.bind === 'time.minute') return String(t.minute).padStart(2, '0');
    return b.sample || '8';
  }
  const isSprite = (e) => { const b = binding(e.bind); return b ? b.kind === 'sprite' : e.type === 'sprite'; };
  function frameIndices(e, frames, value) {
    if (isSprite(e)) return [Math.max(0, frames.findIndex((f) => f.toLowerCase() === String(value).toLowerCase()))];
    return [...String(value)].filter((c) => /\d/.test(c)).map((c) => +c).filter((i) => i < frames.length);
  }

  /**
   * Draws the whole face the way the watch does, scaled to the canvas.
   * opts: { time:{hour,minute,second}, values:{ bind: text } }.
   */
  function renderFace(ctx, spec, opts) {
    const o = opts || {};
    const t = { ...SAMPLE, ...(o.time || {}) };
    const values = o.values || {};
    const [W, H] = dims(spec);
    ctx.save();
    ctx.setTransform(ctx.canvas.width / W, 0, 0, ctx.canvas.height / H, 0, 0);
    ctx.imageSmoothingEnabled = true;
    drawBackground(ctx, spec);
    for (const raw of spec.elements || []) {
      if (!isLive(raw) || raw.type === 'hand' || raw.hidden === true) continue;
      const e = el(raw);
      const frames = glyphFramesOf(e);
      const value = values[e.bind] != null ? String(values[e.bind]) : sampleValue(e, t);
      if (isFixedGlyph(e)) {
        const [cw, ch] = cellSize(e, frames);
        const rot = rotationOf(e);
        const slots = glyphSlots(e, rot % 180 === 90 ? ch : cw);
        const texts = isSprite(e) && e.type === 'sprite'
          ? [frames[frameIndices(e, frames, value)[0]]]
          : value.replace(/\D/g, '').padStart(slots.length, '0').slice(-slots.length).split('').map((d) => (frames[+d] != null ? frames[+d] : frames[0]));
        slots.forEach((x, i) => {
          ctx.save(); ctx.translate(x, e.y); applyRotation(ctx, rot, cw, ch); drawGlyphText(ctx, e, texts[i], cw, ch); ctx.restore();
        });
        continue;
      }
      const strip = glyphs(spec, e, frames, null);
      const indices = frameIndices(e, frames, value);
      const b = binding(e.bind);
      const total = indices.length * strip.width;
      const right = e.w > 0 ? e.x + e.w : e.x + strip.width * (b ? b.max : 1);
      let x = e.align === 'right' ? right - total : e.align === 'center' ? Math.trunc((e.x + right) / 2) - Math.trunc(total / 2) : e.x;
      for (const i of indices) {
        ctx.drawImage(strip.canvas, 0, i * strip.frameHeight, strip.width, strip.frameHeight, x, e.y, strip.width, strip.frameHeight);
        x += strip.width;
      }
    }
    (spec.elements || []).filter((e) => e.type === 'hand' && e.hidden !== true)
      .sort((a, b) => ((binding(a.bind) || {}).id || 0) - ((binding(b.bind) || {}).id || 0))
      .forEach((e) => {
        const angle = e.bind === 'hand.hour' ? ((t.hour % 12) + t.minute / 60) * 30 : e.bind === 'hand.minute' ? (t.minute + t.second / 60) * 6 : t.second * 6;
        drawHand(ctx, e, angle);
      });
    ctx.restore();
  }

  /** Screen-space box an element occupies (selection and dragging). Returns {x,y,w,h}. */
  function bounds(spec, raw) {
    const e = el(raw);
    switch (e.type) {
      case 'hand': return { x: e.pivotX - 10, y: e.pivotY - 10, w: 20, h: 20 };
      case 'number': case 'sprite': {
        const [cw, ch] = cellSize(e, glyphFramesOf(e));
        const [fw, fh] = rotationOf(e) % 180 === 90 ? [ch, cw] : [cw, ch];
        const b = binding(e.bind);
        const count = e.type === 'sprite' ? 1 : (b && b.digitIds ? b.digitIds.length : b ? b.max : 2);
        return { x: e.x, y: e.y, w: e.w > 0 ? e.w : fw * count, h: fh };
      }
      case 'text': {
        const text = e.uppercase ? (e.text || '').toUpperCase() : (e.text || '');
        const [cw, ch] = cellSize(e, [text]);
        const [bw, bh] = rotationOf(e) % 180 === 90 ? [ch, cw] : [cw, ch];
        return { x: textLeft(e, bw), y: e.y, w: bw, h: bh };
      }
      default: return { x: e.x, y: e.y, w: Math.max(e.w, 4), h: Math.max(e.h, 4) };
    }
  }

  /** Rasterizer used by FaceCore.compile (mirror of AndroidFaceRasterizer's FaceRasterizer methods). */
  const FaceRasterizer = {
    background(spec) { return toRaster(backgroundCanvas(spec)); },
    glyphs(spec, e, frames, at) {
      const g = glyphs(spec, e, frames, at);
      return { width: g.width, frameHeight: g.frameHeight, frames: g.frames, pixels: g.pixels, height: g.frameHeight * g.frames };
    },
    hand(spec, e) {
      const [W, H] = dims(spec);
      const canvas = makeCanvas(W, H);
      drawHand(ctx2d(canvas), e, 90);
      return toRaster(canvas);
    },
    preview(spec, width, height) {
      const [W, H] = dims(spec);
      const full = makeCanvas(W, H);
      renderFace(ctx2d(full), spec);
      const small = makeCanvas(width, height);
      const sctx = ctx2d(small);
      sctx.imageSmoothingEnabled = true; sctx.imageSmoothingQuality = 'high';
      sctx.drawImage(full, 0, 0, width, height);
      const r = toRaster(small);
      for (let i = 0; i < r.pixels.length; i++) r.pixels[i] = (r.pixels[i] | 0xFF000000) >>> 0;
      return r;
    },
  };

  window.FaceRasterizer = FaceRasterizer;
  window.WatchFaceRenderer = {
    renderFace, bounds, parseColor, colorToCSS, DOT_FONT, SEGMENTS, preloadAssets,
    setAssetListener(fn) { onAssetReady = fn; },
    sampleTime: SAMPLE,
  };
})();
