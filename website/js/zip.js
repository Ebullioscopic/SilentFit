/* ============================================================
   SILENTFIT — Minimal ZIP reader/writer for .sfface packages
   Writer: STORED entries with CRC and sizes in the local header (accepted by
   Java's ZipInputStream, which the Android app uses). Reader: STORED + DEFLATE.
   ============================================================ */
(function () {
  'use strict';

  let table = null;
  function crc32(bytes) {
    if (!table) {
      table = new Uint32Array(256);
      for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c >>> 0;
      }
    }
    let crc = 0xFFFFFFFF;
    for (let i = 0; i < bytes.length; i++) crc = table[(crc ^ bytes[i]) & 0xFF] ^ (crc >>> 8);
    return (crc ^ 0xFFFFFFFF) >>> 0;
  }

  const enc = new TextEncoder();

  /** entries: [{ name, data:Uint8Array }] → Uint8Array */
  function zipStore(entries) {
    const parts = [], central = [];
    let offset = 0;
    const push = (u8) => { parts.push(u8); offset += u8.length; };
    for (const e of entries) {
      const name = enc.encode(e.name);
      const crc = crc32(e.data);
      const local = new DataView(new ArrayBuffer(30));
      local.setUint32(0, 0x04034B50, true);
      local.setUint16(4, 10, true);        // version needed
      local.setUint16(6, 0x0800, true);    // UTF-8 names
      local.setUint16(8, 0, true);         // STORED
      local.setUint16(10, 0, true); local.setUint16(12, 0x21, true); // 1980-01-01
      local.setUint32(14, crc, true);
      local.setUint32(18, e.data.length, true);
      local.setUint32(22, e.data.length, true);
      local.setUint16(26, name.length, true);
      local.setUint16(28, 0, true);
      const start = offset;
      push(new Uint8Array(local.buffer)); push(name); push(e.data);
      const c = new DataView(new ArrayBuffer(46));
      c.setUint32(0, 0x02014B50, true);
      c.setUint16(4, 20, true); c.setUint16(6, 10, true); c.setUint16(8, 0x0800, true); c.setUint16(10, 0, true);
      c.setUint16(12, 0, true); c.setUint16(14, 0x21, true);
      c.setUint32(16, crc, true); c.setUint32(20, e.data.length, true); c.setUint32(24, e.data.length, true);
      c.setUint16(28, name.length, true);
      c.setUint32(42, start, true);
      central.push(new Uint8Array(c.buffer), name);
    }
    const cdStart = offset;
    central.forEach(push);
    const end = new DataView(new ArrayBuffer(22));
    end.setUint32(0, 0x06054B50, true);
    end.setUint16(8, entries.length, true); end.setUint16(10, entries.length, true);
    end.setUint32(12, offset - cdStart, true); end.setUint32(16, cdStart, true);
    push(new Uint8Array(end.buffer));
    const out = new Uint8Array(offset);
    let p = 0;
    for (const part of parts) { out.set(part, p); p += part.length; }
    return out;
  }

  async function inflateRaw(data) {
    if (typeof DecompressionStream === 'undefined') throw new Error('This browser cannot read compressed .sfface files');
    const stream = new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
    return new Uint8Array(await new Response(stream).arrayBuffer());
  }

  /** Reads a ZIP (central directory). Returns [{ name, data }]; rejects oversized archives. */
  async function unzip(bytes, maxTotal = 8 * 1024 * 1024) {
    const dv = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
    let eocd = -1;
    for (let i = bytes.length - 22; i >= Math.max(0, bytes.length - 65557); i--) {
      if (dv.getUint32(i, true) === 0x06054B50) { eocd = i; break; }
    }
    if (eocd < 0) throw new Error('Not a valid .sfface archive');
    const count = dv.getUint16(eocd + 10, true);
    let p = dv.getUint32(eocd + 16, true);
    const out = [];
    let total = 0;
    for (let i = 0; i < count; i++) {
      if (dv.getUint32(p, true) !== 0x02014B50) throw new Error('Damaged archive');
      const method = dv.getUint16(p + 10, true);
      const size = dv.getUint32(p + 20, true);
      const nameLen = dv.getUint16(p + 28, true), extraLen = dv.getUint16(p + 30, true), commentLen = dv.getUint16(p + 32, true);
      const local = dv.getUint32(p + 42, true);
      const name = new TextDecoder().decode(bytes.subarray(p + 46, p + 46 + nameLen));
      p += 46 + nameLen + extraLen + commentLen;
      if (name.endsWith('/')) continue;
      const start = local + 30 + dv.getUint16(local + 26, true) + dv.getUint16(local + 28, true);
      const raw = bytes.subarray(start, start + size);
      const data = method === 0 ? raw : method === 8 ? await inflateRaw(raw) : null;
      if (!data) throw new Error('Unsupported compression in archive');
      total += data.length;
      if (total > maxTotal) throw new Error('Archive is too large');
      out.push({ name, data });
    }
    return out;
  }

  window.SFZip = { zipStore, unzip, crc32 };
})();
