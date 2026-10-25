// Run: node tests/core.test.js <face.bin>...   (WF files; vendor samples are private and not committed)
const fs = require('fs');
global.window = {};
require('../js/core.js');
const C = window.FaceCore;
let failed = 0;
const ok = (c, m) => { if (!c) { failed++; console.log('FAIL', m); } };

for (const file of process.argv.slice(2)) {
  const d = new Uint8Array(fs.readFileSync(file));
  const wf = C.wfDecode(d);
  const images = new Map(); // offset -> blob
  for (const r of wf.records) {
    if (images.has(r.offset)) continue;
    const img = C.parDecode(d, r.offset);
    const blob = d.slice(r.offset, r.offset + img.size);
    images.set(r.offset, blob);
    const re = C.parEncode(img.pixels, img.width, img.height, img.alpha, img.tile);
    ok(re.length === blob.length && re.every((v, i) => v === blob[i]), `${file}: PAR re-encode differs at ${r.offset} (${blob.length} vs ${re.length})`);
  }
  const problems = C.checkSafety(d);
  console.log(file.split('/').pop(), wf.records.length, 'records', problems.length ? problems.slice(0, 3) : 'safe');
}
console.log(failed ? `${failed} failures` : 'ok');
process.exit(failed ? 1 : 0);
