/* ============================================================
   SILENTFIT — Watch Face Designer
   Layers, inspector, canvas editing, undo/redo, JSON view,
   import/export (.sfface zip, .sfface.json, compiled .bin, .png)
   and the same safety checks the Android app runs before installing.
   ============================================================ */
(function () {
  'use strict';

  const FC = window.FaceCore;
  const R = window.WatchFaceRenderer;
  const Z = window.SFZip;

  const W0 = 240, H0 = 280;
  const DRAFT_KEY = 'silentfit-designer-draft';
  const ASSET_PLACEHOLDER = '<image data>';
  const MAX_IMPORT = 8 * 1024 * 1024;
  const MAX_ELEMENTS = 150;
  const TYPES = ['text', 'number', 'sprite', 'shape', 'hand', 'image'];
  const SHAPES = ['rect', 'circle', 'ring', 'arc', 'line', 'ticks', 'dot-ring', 'segments', 'dot-matrix', 'halftone'];
  const HAND_STYLES = ['bar', 'needle', 'line', 'dot', 'dots', 'outline'];
  const NUMBER_ORDER = ['time.hour', 'time.minute', 'date.day', 'date.month', 'activity.steps', 'activity.calories',
    'health.heartRate', 'device.battery', 'time.hour.tens', 'time.hour.units', 'time.minute.tens', 'time.minute.units'];
  const SPRITE_ORDER = ['date.weekday', 'time.ampm'];
  const HAND_ORDER = ['hand.hour', 'hand.minute', 'hand.second'];
  const GROUPS = [
    ['hands', 'HANDS', (e) => e.type === 'hand'],
    ['live', 'LIVE DATA', (e) => e.type === 'number' || e.type === 'sprite'],
    ['static', 'STATIC', (e) => e.type === 'text' || e.type === 'shape' || e.type === 'image'],
  ];
  const OVERRIDES = [
    ['date.weekday', 'Day', ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']],
    ['date.day', 'Date', ['1', '9', '25', '31']],
    ['activity.steps', 'Steps', ['0', '8204', '24680', '99999']],
    ['activity.calories', 'Kcal', ['0', '312', '1250', '9999']],
    ['health.heartRate', 'HR', ['0', '72', '128', '190']],
    ['device.battery', 'Batt', ['5', '86', '100']],
  ];

  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const clone = (o) => JSON.parse(JSON.stringify(o));
  const isInput = (t) => t && (/^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName) || t.isContentEditable);
  const COLOR_RE = /^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i;
  const hex6 = (s) => { const c = R.parseColor(s); return '#' + [c.r, c.g, c.b].map((v) => v.toString(16).padStart(2, '0')).join(''); };

  function bytesToBase64(bytes) {
    let s = '';
    for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    return btoa(s);
  }
  function base64ToBytes(b64) {
    const s = atob(String(b64).replace(/^data:[^,]*,/, ''));
    const out = new Uint8Array(s.length);
    for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
    return out;
  }
  const safeName = (s, fallback) => (String(s || '').replace(/[^A-Za-z0-9 _-]/g, '').trim().replace(/\s+/g, '-').toLowerCase().slice(0, 40) || fallback);

  /** Validates untrusted JSON and returns a clean FaceSpec (throws with a readable message). */
  function normalizeSpec(raw) {
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) throw new Error('Not a SilentFit face');
    if (raw.format !== 'silentfit.face') throw new Error(`Not a SilentFit face (format "${String(raw.format).slice(0, 40)}")`);
    const width = Number.isInteger(raw.width) ? raw.width : W0, height = Number.isInteger(raw.height) ? raw.height : H0;
    if (width < 64 || width > 1024 || height < 64 || height > 1024) throw new Error('Unsupported screen size');
    if (raw.elements != null && !Array.isArray(raw.elements)) throw new Error('"elements" must be a list');
    if ((raw.elements || []).length > MAX_ELEMENTS) throw new Error(`Too many elements (max ${MAX_ELEMENTS})`);
    const seen = new Set();
    const elements = (raw.elements || []).map((e, i) => {
      if (!e || typeof e !== 'object' || !TYPES.includes(e.type)) throw new Error(`Element ${i + 1} has an unknown type`);
      let id = typeof e.id === 'string' && e.id.trim() ? e.id.slice(0, 40) : `${e.type}-${i + 1}`;
      while (seen.has(id)) id += '_';
      seen.add(id);
      return { ...e, id };
    });
    const assets = {};
    if (raw.assets && typeof raw.assets === 'object') {
      for (const [k, v] of Object.entries(raw.assets)) if (typeof v === 'string') assets[k] = v;
    }
    const bg = raw.background && typeof raw.background === 'object' ? raw.background : {};
    return { ...raw, format: 'silentfit.face', version: 1, name: String(raw.name || 'Untitled').slice(0, 32), width, height, background: bg, elements, assets };
  }

  function downloadBlob(blob, name) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = name;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
  }

  class FaceDesigner {
    constructor() {
      this.spec = clone(window.DEFAULT_FACE);
      this.selectedId = null;
      this.zoom = 2;
      this.showGrid = false;
      this.showCode = false;
      this.minutes = 10 * 60 + 8;
      this.overrides = {};
      this.undoStack = []; this.redoStack = [];
      this.lastUndoKey = null; this.lastUndoAt = 0;
      this.drag = null;
      this.guides = [];
      this.report = { problems: [], notes: [], records: 0, bytes: 0 };

      this.initDOM();
      this.bindEvents();
      this.bindKeyboard();
      this.restore();
      this.fitZoom();
      this.syncAll();
      R.setAssetListener(() => { this.render(); this.queueEvaluate(); });
    }

    // ------------------------------------------------------------ setup
    initDOM() {
      const $ = (id) => document.getElementById(id);
      this.canvas = $('watch-canvas');
      this.ctx = this.canvas.getContext('2d');
      this.outer = document.querySelector('.watch-canvas-outer');
      this.container = $('canvas-container');
      this.layersList = $('layers-list');
      this.inspector = $('inspector-scroll');
      this.nameInput = $('face-name');
      this.timeDisplay = $('time-display');
      this.timeSlider = $('time-slider');
      this.zoomDisplay = $('zoom-level');
      this.safety = $('safety-indicator');
      this.sizeDisplay = $('file-size');
      this.recordDisplay = $('record-count');
      this.codeEditor = $('code-editor');
      this.codeView = $('code-view');
      this.overrideBar = $('value-overrides');
      this.canvas.style.imageRendering = 'auto';
      this.canvas.style.touchAction = 'none';
      this.toastEl = document.createElement('div');
      this.toastEl.className = 'toast';
      this.toastEl.setAttribute('role', 'status');
      document.body.appendChild(this.toastEl);
    }

    on(id, fn) { const el = document.getElementById(id); if (el) el.addEventListener('click', fn); }

    bindEvents() {
      this.nameInput.addEventListener('input', () => { this.pushUndo('name'); this.spec.name = this.nameInput.value.slice(0, 32); this.changed(false); });
      this.timeSlider.addEventListener('input', () => { this.minutes = +this.timeSlider.value; this.render(); this.updateTimeDisplay(); });
      this.canvas.addEventListener('pointerdown', (e) => this.onPointerDown(e));
      this.canvas.addEventListener('pointermove', (e) => this.onPointerMove(e));
      this.canvas.addEventListener('pointerup', (e) => this.onPointerUp(e));
      this.canvas.addEventListener('pointercancel', (e) => this.onPointerUp(e));
      this.canvas.addEventListener('dblclick', () => { const i = this.inspector.querySelector('[data-prop="text"]'); if (i) i.focus(); });

      this.codeEditor.addEventListener('input', () => this.applyCode());
      this.on('btn-undo', () => this.undo());
      this.on('btn-redo', () => this.redo());
      this.on('btn-new', () => this.newFace());
      this.on('btn-zoom-in', () => this.setZoom(this.zoom + 1));
      this.on('btn-zoom-out', () => this.setZoom(this.zoom - 1));
      this.on('btn-grid', () => this.toggleGrid());
      this.on('btn-code', () => this.toggleCode());
      this.on('btn-presets', () => this.showPresets());
      this.on('btn-import', () => this.importDialog());
      this.on('btn-export', () => this.showExport());
      this.on('btn-shortcuts', () => this.openModal('shortcuts-modal'));
      this.on('safety-indicator', () => this.showProblems());
      this.on('btn-delete', () => this.deleteSelected());
      this.on('btn-dup', () => this.duplicateSelected());
      this.on('btn-up', () => this.reorder(1));
      this.on('btn-down', () => this.reorder(-1));
      TYPES.forEach((t) => this.on(`add-${t}`, () => this.addElement(t)));
      const act = (id, fn) => { const el = document.getElementById(id); if (!el) return; const run = () => fn(); el.addEventListener('click', run); el.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); run(); } }); };
      act('export-sfface', () => this.exportPackage());
      act('export-json', () => this.exportJson());
      act('export-bin', () => this.exportBin());
      act('export-png', () => this.exportPng());
      document.querySelectorAll('.modal-close').forEach((b) => b.addEventListener('click', () => this.closeModals()));
      document.querySelectorAll('.modal-overlay').forEach((o) => o.addEventListener('click', (e) => { if (e.target === o) this.closeModals(); }));

      // Layers (delegated)
      this.layersList.addEventListener('click', (e) => {
        const lock = e.target.closest('[data-lock]');
        if (lock) { this.toggleLock(lock.dataset.lock); e.stopPropagation(); return; }
        const item = e.target.closest('.layer-item');
        if (item) this.select(item.dataset.id);
      });
      this.layersList.addEventListener('keydown', (e) => {
        if ((e.key === 'Enter' || e.key === ' ') && e.target.classList.contains('layer-item')) { e.preventDefault(); this.select(e.target.dataset.id); }
      });

      // Inspector (delegated)
      this.inspector.addEventListener('input', (e) => this.onInspectorInput(e));
      this.inspector.addEventListener('change', (e) => this.onInspectorChange(e));
      this.inspector.addEventListener('click', (e) => this.onInspectorClick(e));

      this.overrideBar.addEventListener('click', (e) => {
        const chip = e.target.closest('[data-override]');
        if (!chip) return;
        const [, , values] = OVERRIDES.find((o) => o[0] === chip.dataset.override);
        const cur = this.overrides[chip.dataset.override] != null ? this.overrides[chip.dataset.override] : (FC.binding(chip.dataset.override) || {}).sample;
        this.overrides[chip.dataset.override] = values[(values.indexOf(String(cur)) + 1) % values.length];
        this.updateOverrideBar(); this.render();
      });
      window.addEventListener('resize', () => {
        if (!this.userZoomed) { this.fitZoom(); this.updateZoom(); }
        this.render();
      });
    }

    bindKeyboard() {
      document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
          if (document.querySelector('.modal-overlay.visible')) this.closeModals();
          else if (this.selectedId) this.select(null);
          return;
        }
        if (isInput(e.target) || document.querySelector('.modal-overlay.visible')) return;
        const meta = e.metaKey || e.ctrlKey;
        const k = e.key.toLowerCase();
        const el = this.selected();
        if (meta && k === 'z') { e.preventDefault(); e.shiftKey ? this.redo() : this.undo(); }
        else if (meta && k === 'y') { e.preventDefault(); this.redo(); }
        else if (meta && k === 'd') { e.preventDefault(); this.duplicateSelected(); }
        else if (meta && k === ']') { e.preventDefault(); this.reorder(1); }
        else if (meta && k === '[') { e.preventDefault(); this.reorder(-1); }
        else if (meta && k === 'e') { e.preventDefault(); this.showExport(); }
        else if (meta && k === '/') { e.preventDefault(); this.toggleCode(); }
        else if (meta) return;
        else if (k === 'delete' || k === 'backspace') { e.preventDefault(); this.deleteSelected(); }
        else if (k === 'l') this.toggleLock(this.selectedId);
        else if (k === 'g') this.toggleGrid();
        else if (k === '+' || k === '=') this.setZoom(this.zoom + 1);
        else if (k === '-') this.setZoom(this.zoom - 1);
        else if (k.startsWith('arrow') && el && el.type !== 'hand' && !el.locked) {
          e.preventDefault();
          const step = e.shiftKey ? 8 : 1;
          this.pushUndo('nudge');
          if (k === 'arrowup') el.y = (el.y || 0) - step;
          if (k === 'arrowdown') el.y = (el.y || 0) + step;
          if (k === 'arrowleft') el.x = (el.x || 0) - step;
          if (k === 'arrowright') el.x = (el.x || 0) + step;
          this.changed(false);
          this.syncPositionInputs();
        }
      });
    }

    // ------------------------------------------------------------ state
    selected() { return this.spec.elements.find((e) => e.id === this.selectedId) || null; }
    byId(id) { return this.spec.elements.find((e) => e.id === id) || null; }
    toast(msg) {
      this.toastEl.textContent = msg; this.toastEl.classList.add('visible');
      clearTimeout(this._toast); this._toast = setTimeout(() => this.toastEl.classList.remove('visible'), 3200);
    }

    pushUndo(key) {
      const now = Date.now();
      if (key && key === this.lastUndoKey && now - this.lastUndoAt < 900) { this.lastUndoAt = now; return; }
      this.undoStack.push(JSON.stringify(this.spec));
      if (this.undoStack.length > 80) this.undoStack.shift();
      this.redoStack = [];
      this.lastUndoKey = key || null; this.lastUndoAt = now;
    }
    undo() { this.step(this.undoStack, this.redoStack); }
    redo() { this.step(this.redoStack, this.undoStack); }
    step(from, to) {
      if (!from.length) return;
      to.push(JSON.stringify(this.spec));
      this.spec = JSON.parse(from.pop());
      this.lastUndoKey = null;
      if (!this.byId(this.selectedId)) this.selectedId = null;
      this.syncAll();
    }

    /** Call after any spec mutation. structural=true rebuilds the layer list and inspector. */
    changed(structural) {
      this.render();
      if (structural) { this.renderLayers(); this.renderInspector(); this.updateOverrideBar(); }
      this.refreshCode();
      this.queueEvaluate();
      this.saveDraft();
    }
    syncAll() {
      this.nameInput.value = this.spec.name;
      this.updateTimeDisplay(); this.updateZoom(); this.renderLayers(); this.renderInspector(); this.updateOverrideBar();
      this.render(); this.refreshCode(); this.queueEvaluate();
    }

    saveDraft() {
      clearTimeout(this._save);
      this._save = setTimeout(() => { try { localStorage.setItem(DRAFT_KEY, JSON.stringify(this.spec)); } catch (_) { /* quota */ } }, 400);
    }
    restore() {
      const key = new URLSearchParams(location.search).get('preset');
      if (key && Object.prototype.hasOwnProperty.call(window.PRESETS, key)) { this.spec = this.presetSpec(key); return; }
      try {
        const saved = localStorage.getItem(DRAFT_KEY);
        if (saved) this.spec = normalizeSpec(JSON.parse(saved));
      } catch (_) { /* ignore a damaged draft */ }
    }
    presetSpec(key) {
      const { category, description, ...spec } = clone(window.PRESETS[key]);
      return normalizeSpec(spec);
    }
    newFace() {
      if (this.spec.elements.length && !confirm('Start a new blank face? Undo can bring the current one back.')) return;
      this.pushUndo(); this.spec = clone(window.DEFAULT_FACE); this.selectedId = null; this.syncAll(); this.saveDraft();
    }

    // ------------------------------------------------------------ view
    values() {
      const h = Math.floor(this.minutes / 60);
      return { 'time.ampm': h < 12 ? 'AM' : 'PM', ...this.overrides };
    }
    time() { return { hour: Math.floor(this.minutes / 60), minute: this.minutes % 60, second: 36 }; }
    updateTimeDisplay() {
      this.timeSlider.value = this.minutes;
      this.timeDisplay.textContent = `${String(Math.floor(this.minutes / 60)).padStart(2, '0')}:${String(this.minutes % 60).padStart(2, '0')}`;
    }

    fitZoom() {
      const h = this.container.clientHeight - 80, w = this.container.clientWidth - 80;
      this.zoom = Math.max(1, Math.min(4, Math.floor(Math.min(h / H0, w / W0)) || 1));
    }
    setZoom(z) { this.userZoomed = true; this.zoom = Math.max(1, Math.min(4, z)); this.updateZoom(); this.render(); }
    updateZoom() {
      const [w, h] = [this.spec.width || W0, this.spec.height || H0];
      this.canvas.style.width = `${w * this.zoom}px`; this.canvas.style.height = `${h * this.zoom}px`;
      this.outer.style.borderRadius = `${28 * this.zoom}px`;
      this.outer.style.setProperty('--grid', `${8 * this.zoom}px`);
      this.outer.classList.toggle('grid-visible', this.showGrid);
      this.zoomDisplay.textContent = `${this.zoom}×`;
    }
    toggleGrid() {
      this.showGrid = !this.showGrid; this.updateZoom();
      document.getElementById('btn-grid').classList.toggle('active', this.showGrid);
    }

    render() {
      const [w, h] = [this.spec.width || W0, this.spec.height || H0];
      const scale = this.zoom * Math.max(1, window.devicePixelRatio || 1);
      const cw = Math.round(w * scale), ch = Math.round(h * scale);
      if (this.canvas.width !== cw || this.canvas.height !== ch) { this.canvas.width = cw; this.canvas.height = ch; }
      this.ctx.clearRect(0, 0, cw, ch);
      R.renderFace(this.ctx, this.spec, { time: this.time(), values: this.values() });
      this.drawOverlay(scale);
    }

    drawOverlay(scale) {
      const ctx = this.ctx;
      ctx.save();
      ctx.setTransform(scale, 0, 0, scale, 0, 0);
      const px = 1 / scale;
      if (this.guides.length) {
        ctx.strokeStyle = 'rgba(215,25,33,0.9)'; ctx.lineWidth = px;
        for (const g of this.guides) {
          ctx.beginPath();
          if (g.axis === 'x') { ctx.moveTo(g.at, 0); ctx.lineTo(g.at, this.spec.height); } else { ctx.moveTo(0, g.at); ctx.lineTo(this.spec.width, g.at); }
          ctx.stroke();
        }
      }
      const el = this.selected();
      if (el) {
        ctx.strokeStyle = '#5B9BF6'; ctx.fillStyle = '#5B9BF6'; ctx.lineWidth = 1.5 * px;
        if (el.type === 'hand') {
          const x = el.pivotX == null ? 120 : el.pivotX, y = el.pivotY == null ? 140 : el.pivotY;
          ctx.beginPath(); ctx.moveTo(x - 8, y); ctx.lineTo(x + 8, y); ctx.moveTo(x, y - 8); ctx.lineTo(x, y + 8); ctx.stroke();
        } else {
          const b = R.bounds(this.spec, el);
          ctx.setLineDash([4 * px, 3 * px]);
          ctx.strokeRect(b.x - 0.5, b.y - 0.5, b.w + 1, b.h + 1);
          ctx.setLineDash([]);
          if (this.resizable(el)) { const s = 8 * px; ctx.fillRect(b.x + b.w - s / 2, b.y + b.h - s / 2, s, s); }
        }
      }
      ctx.restore();
    }

    resizable(el) { return !el.locked && (el.type === 'shape' || el.type === 'image'); }

    // ------------------------------------------------------------ pointer editing
    toSpec(e) {
      const r = this.canvas.getBoundingClientRect();
      return [((e.clientX - r.left) / r.width) * this.spec.width, ((e.clientY - r.top) / r.height) * this.spec.height];
    }
    hit(x, y) {
      let best = null, area = Infinity;
      for (let i = this.spec.elements.length - 1; i >= 0; i--) {
        const el = this.spec.elements[i];
        if (el.type === 'hand' || el.locked) continue;
        const b = R.bounds(this.spec, el);
        if (x >= b.x && x <= b.x + b.w && y >= b.y && y <= b.y + b.h) {
          const a = b.w * b.h;
          if (a < area) { best = el; area = a; }
        }
      }
      return best;
    }
    onPointerDown(e) {
      if (e.button != null && e.button !== 0) return;
      const [x, y] = this.toSpec(e);
      const cur = this.selected();
      let mode = 'move', target = null;
      if (cur && this.resizable(cur)) {
        const b = R.bounds(this.spec, cur);
        if (Math.abs(x - (b.x + b.w)) <= 8 / this.zoom + 2 && Math.abs(y - (b.y + b.h)) <= 8 / this.zoom + 2) { mode = 'resize'; target = cur; }
      }
      if (!target) target = this.hit(x, y);
      if (!target) { this.select(null); return; }
      if (target.id !== this.selectedId) this.select(target.id);
      this.canvas.setPointerCapture(e.pointerId);
      this.drag = { mode, id: target.id, x, y, ox: target.x || 0, oy: target.y || 0, ow: target.w || 0, oh: target.h || 0, snapshot: JSON.stringify(this.spec), moved: false };
    }
    onPointerMove(e) {
      const d = this.drag;
      if (!d) return;
      const el = this.byId(d.id);
      if (!el) return;
      const [x, y] = this.toSpec(e);
      const dx = Math.round(x - d.x), dy = Math.round(y - d.y);
      if (!d.moved && Math.abs(dx) + Math.abs(dy) < 2) return;
      if (!d.moved) { d.moved = true; this.undoStack.push(d.snapshot); this.redoStack = []; this.lastUndoKey = null; }
      this.guides = [];
      if (d.mode === 'resize') {
        el.w = Math.max(1, d.ow + dx); el.h = Math.max(1, d.oh + dy);
      } else {
        el.x = d.ox + dx; el.y = d.oy + dy;
        const b = R.bounds(this.spec, el);
        const snap = (centre, mid, axis, prop) => { if (Math.abs(centre - mid) <= 3) { el[prop] += Math.round(mid - centre); this.guides.push({ axis, at: mid }); } };
        snap(b.x + b.w / 2, this.spec.width / 2, 'x', 'x');
        snap(b.y + b.h / 2, this.spec.height / 2, 'y', 'y');
      }
      this.render();
      this.syncPositionInputs();
      this.queueEvaluate(true);
    }
    onPointerUp() {
      if (!this.drag) return;
      const moved = this.drag.moved;
      this.drag = null; this.guides = [];
      this.render();
      if (moved) { this.refreshCode(); this.queueEvaluate(); this.saveDraft(); }
    }

    // ------------------------------------------------------------ element operations
    uniqueId(base) {
      let n = 1, id;
      do { id = `${base}-${n++}`; } while (this.byId(id));
      return id;
    }
    usedBinds(exceptId) { return new Set(this.spec.elements.filter((e) => e.id !== exceptId && e.bind).map((e) => e.bind)); }
    conflicts(bind, exceptId) {
      const used = this.usedBinds(exceptId);
      if (used.has(bind)) return true;
      const family = /^time\.(hour|minute)(\.|$)/.exec(bind);
      if (family) {
        const base = `time.${family[1]}`;
        if (bind === base) return used.has(`${base}.tens`) || used.has(`${base}.units`);
        return used.has(base);
      }
      return false;
    }
    freeBind(order, exceptId) { return order.find((b) => !this.conflicts(b, exceptId)) || null; }

    addElement(type) {
      if (this.spec.elements.length >= MAX_ELEMENTS) return this.toast(`Element limit reached (${MAX_ELEMENTS})`);
      const id = this.uniqueId(type);
      const el = { id, type, x: 0, y: 100 };
      if (type === 'text') Object.assign(el, { text: 'TEXT', font: 'space-mono', size: 16, color: '#FFFFFF' });
      else if (type === 'number' || type === 'sprite') {
        const bind = this.freeBind(type === 'sprite' ? SPRITE_ORDER : NUMBER_ORDER);
        if (!bind) return this.toast(type === 'sprite' ? 'Weekday and AM/PM are already on the face' : 'Every data field is already on the face');
        Object.assign(el, { bind, glyph: 'dot-matrix', size: type === 'sprite' ? 14 : 28, color: '#FFFFFF', ghost: '#262626' });
      } else if (type === 'shape') Object.assign(el, { shape: 'ring', w: 60, h: 60, color: '#FFFFFF', stroke: 3, fill: false });
      else if (type === 'hand') {
        const bind = this.freeBind(HAND_ORDER);
        if (!bind) return this.toast('The hour, minute and second hands are already on the face');
        Object.assign(el, { bind, handStyle: 'bar', length: bind === 'hand.hour' ? 55 : 80, tail: 12, stroke: bind === 'hand.second' ? 3 : 6, color: '#FFFFFF', size: 0 });
        delete el.x; delete el.y;
      } else if (type === 'image') { Object.assign(el, { w: 60, h: 60 }); }
      this.pushUndo();
      this.spec.elements.push(el);
      this.selectedId = id;
      if (type !== 'hand' && type !== 'image') {
        const b = R.bounds(this.spec, el);
        el.x = Math.max(0, Math.round((this.spec.width - b.w) / 2));
      } else if (type === 'image') el.x = Math.round((this.spec.width - 60) / 2);
      this.changed(true);
      if (type === 'image') this.chooseImage((name) => { const e2 = this.byId(id); if (e2) { e2.src = name; this.changed(true); } });
    }

    deleteSelected() {
      const el = this.selected();
      if (!el) return;
      this.pushUndo();
      this.spec.elements = this.spec.elements.filter((e) => e.id !== el.id);
      this.selectedId = null;
      this.changed(true);
    }
    duplicateSelected() {
      const el = this.selected();
      if (!el) return;
      const copy = clone(el);
      if (el.bind) {
        const order = el.type === 'hand' ? HAND_ORDER : el.type === 'sprite' ? SPRITE_ORDER : NUMBER_ORDER;
        const free = this.freeBind(order);
        if (!free) return this.toast('No free data field to duplicate into');
        copy.bind = free;
      }
      copy.id = this.uniqueId(el.type);
      copy.locked = false;
      if (el.type !== 'hand') { copy.x = (el.x || 0) + 8; copy.y = (el.y || 0) + 8; }
      this.pushUndo();
      this.spec.elements.push(copy);
      this.selectedId = copy.id;
      this.changed(true);
    }
    reorder(dir) {
      const el = this.selected();
      if (!el) return;
      const group = GROUPS.find((g) => g[2](el));
      const arr = this.spec.elements;
      let i = arr.indexOf(el);
      for (let j = i + dir; j >= 0 && j < arr.length; j += dir) {
        if (group[2](arr[j])) { this.pushUndo(); [arr[i], arr[j]] = [arr[j], arr[i]]; this.changed(true); return; }
      }
    }
    toggleLock(id) {
      const el = this.byId(id);
      if (!el) return;
      this.pushUndo(); el.locked = !el.locked; if (!el.locked) delete el.locked;
      this.changed(true);
    }
    select(id) {
      this.selectedId = id;
      this.renderLayers(); this.renderInspector(); this.render();
    }

    // ------------------------------------------------------------ layers
    renderLayers() {
      if (!this.spec.elements.length) {
        this.layersList.innerHTML = '<div style="padding:16px;text-align:center;color:var(--text-disabled);font-size:12px">No elements yet.<br>Add one below.</div>';
        return;
      }
      const icons = { text: 'T', number: '#', sprite: '◈', shape: '◻', hand: '↻', image: '⊞' };
      const bad = new Set(this.report.problems.map((p) => p.id).filter(Boolean));
      let html = '';
      for (const [key, label, test] of GROUPS) {
        const items = this.spec.elements.filter(test);
        if (!items.length) continue;
        html += `<div class="layer-group-header" data-group="${key}">${label}</div>`;
        for (const e of [...items].reverse()) {
          const sub = e.bind || e.shape || e.type;
          html += `<div class="layer-item${e.id === this.selectedId ? ' selected' : ''}${e.locked ? ' locked' : ''}" data-id="${esc(e.id)}" role="button" tabindex="0">
            <span class="layer-item-icon">${icons[e.type]}</span>
            <span class="layer-item-name">${esc(e.id)}${bad.has(e.id) ? ' <span style="color:var(--accent)" title="Has a problem">●</span>' : ''}</span>
            <span class="layer-item-type">${esc(sub)}</span>
            <button class="lock-icon" data-lock="${esc(e.id)}" title="${e.locked ? 'Unlock' : 'Lock'} (L)" aria-label="${e.locked ? 'Unlock' : 'Lock'} ${esc(e.id)}" style="background:none;border:none;cursor:pointer;color:inherit">${e.locked ? '🔒' : '⌾'}</button>
          </div>`;
        }
      }
      this.layersList.innerHTML = html;
    }

    // ------------------------------------------------------------ inspector
    num(label, prop, el, o = {}) {
      const v = el[prop] == null ? (o.def == null ? '' : o.def) : el[prop];
      return `<span class="inspector-label"${o.title ? ` title="${esc(o.title)}"` : ''}>${esc(label)}</span>
        <input type="number" class="inspector-input inspector-input-sm" data-prop="${prop}" data-kind="${o.kind || 'int'}" value="${esc(v)}"${o.min != null ? ` min="${o.min}"` : ''}${o.max != null ? ` max="${o.max}"` : ''}${o.step ? ` step="${o.step}"` : ''}${o.blank ? ' placeholder="auto"' : ''} aria-label="${esc(label)}">`;
    }
    color(label, prop, el, def) {
      const v = el[prop.replace(/^bg:/, '')] || def;
      return `<div class="inspector-section"><div class="inspector-section-title">${esc(label)}</div>
        <div class="inspector-row inspector-color">
          <label class="color-swatch" style="background:${esc(v)};position:relative;overflow:hidden"><input type="color" data-prop="${prop}" data-kind="colorpick" value="${hex6(v)}" style="opacity:0;position:absolute;inset:0;width:100%;height:100%;cursor:pointer" aria-label="${esc(label)} picker"></label>
          <input type="text" class="color-input" data-prop="${prop}" data-kind="color" value="${esc(v)}" maxlength="9" spellcheck="false" aria-label="${esc(label)} hex">
        </div></div>`;
    }
    seg(prop, options, current, titleAttr) {
      return `<div class="glyph-selector">${options.map(([val, text]) => `<button class="glyph-option${String(current) === String(val) ? ' selected' : ''}" data-set="${prop}" data-value="${esc(val)}"${titleAttr ? ` title="${esc(titleAttr)}"` : ''}>${esc(text)}</button>`).join('')}</div>`;
    }
    chips(prop, list, current, disabledFn, labelFn) {
      return `<div class="binding-chips">${list.map((v) => {
        const dis = disabledFn && disabledFn(v);
        return `<button class="binding-chip${current === v ? ' selected' : ''}${dis ? ' used' : ''}" data-set="${prop}" data-value="${esc(v)}"${dis ? ' disabled title="Already used by another element"' : ''}>${esc(labelFn ? labelFn(v) : v)}</button>`;
      }).join('')}</div>`;
    }
    section(title, body) { return `<div class="inspector-section"><div class="inspector-section-title">${esc(title)}</div>${body}</div>`; }

    renderInspector() {
      const top = this.inspector.scrollTop;
      this.paintInspector();
      this.inspector.scrollTop = top;
    }

    paintInspector() {
      const el = this.selected();
      if (!el) { this.inspector.innerHTML = this.backgroundPanel(); return; }
      const own = this.report.problems.filter((p) => p.id === el.id);
      let h = own.length ? this.section('PROBLEMS', own.map((p) => `<div style="color:var(--accent);font-size:12px;margin-bottom:4px">${esc(p.msg)}</div>`).join('')) : '';
      h += this.section('ELEMENT', `<div class="inspector-row"><span class="inspector-label">ID</span><input type="text" class="inspector-input" data-prop="id" data-kind="id" value="${esc(el.id)}" maxlength="40" aria-label="Element id"></div>
        <div class="inspector-row"><span class="inspector-label">Type</span><span style="font-family:var(--font-data);font-size:12px;color:var(--text-secondary)">${esc(el.type)}</span></div>`);
      if (el.type !== 'hand') {
        h += this.section('POSITION', `<div class="inspector-row">${this.num('X', 'x', el, { def: 0 })}${this.num('Y', 'y', el, { def: 0 })}</div>` +
          `<div class="inspector-row">${this.num('W', 'w', el, { min: 0, blank: true, title: 'Box width. Alignment applies inside this box.' })}${(el.type === 'shape' || el.type === 'image') ? this.num('H', 'h', el, { min: 0, blank: true }) : ''}</div>`);
      }
      if (el.type === 'text') h += this.textPanel(el);
      else if (el.type === 'number' || el.type === 'sprite') h += this.livePanel(el);
      else if (el.type === 'shape') h += this.shapePanel(el);
      else if (el.type === 'hand') h += this.handPanel(el);
      else if (el.type === 'image') h += this.imagePanel(el);
      if (el.type !== 'image') h += this.color('COLOR', 'color', el, el.type === 'hand' ? '#FFFFFF' : '#FFFFFF');
      if (el.type === 'text' || el.type === 'shape' || el.type === 'image') {
        const o = el.opacity == null ? 1 : el.opacity;
        h += this.section('OPACITY', `<div class="inspector-row"><input type="range" class="inspector-input" data-prop="opacity" data-kind="float" min="0" max="1" step="0.05" value="${o}" style="flex:1" aria-label="Opacity"><span class="inspector-label" data-opacity-label style="min-width:36px;text-align:right">${Math.round(o * 100)}%</span></div>`);
      }
      this.inspector.innerHTML = h;
    }

    glyphPicker(el) {
      return this.section('GLYPH', this.seg('glyph', [['font', 'Font'], ['dot-matrix', 'LED'], ['segment', '7-Seg']], el.glyph || 'font'));
    }
    fontPanel(el) {
      const g = el.glyph || 'font';
      if (g !== 'font') {
        return this.section('SIZE', `<div class="inspector-row">${this.num('Size', 'size', el, { def: 16, min: 6, max: 120, title: g === 'dot-matrix' ? 'Text height; dot pitch is size ÷ 7' : 'Digit height' })}</div>`) +
          this.color('UNLIT DOTS / SEGMENTS', 'ghost', el, '#262626') +
          `<div class="inspector-section"><button class="glyph-option" data-action="clear-ghost" style="width:100%">Hide unlit</button></div>`;
      }
      const f = el.font || 'space-mono';
      return this.section('FONT', this.seg('font', [['doto', 'Doto'], ['space-mono', 'Mono'], ['space-grotesk', 'Grotesk']], f) +
        `<div class="inspector-row" style="margin-top:8px">${this.num('Size', 'size', el, { def: 16, min: 6, max: 120 })}${this.num('Wt', 'weight', el, { def: 400, min: 100, max: 900, step: 100, title: 'Space Mono: 600+ is bold' })}</div>
         <div class="inspector-row">${this.num('Track', 'tracking', el, { def: 0, min: -0.2, max: 1, step: 0.01, kind: 'float' })}${el.type === 'text' ? `<label style="display:flex;align-items:center;gap:4px;font-size:11px;color:var(--text-secondary)"><input type="checkbox" data-prop="uppercase" data-kind="bool"${el.uppercase ? ' checked' : ''}> CAPS</label>` : ''}</div>`);
    }
    alignPanel(el) {
      return this.section('ALIGN', this.seg('align', [['left', '←'], ['center', '↔'], ['right', '→']], el.align || 'left', 'Alignment inside the W box'));
    }
    rotationPanel(el) {
      return this.section('ROTATION', this.seg('rotation', [[0, '0°'], [90, '90°'], [180, '180°'], [270, '270°']], el.rotation || 0));
    }
    textPanel(el) {
      return this.section('TEXT', `<div class="inspector-row"><input type="text" class="inspector-input" data-prop="text" data-kind="str" value="${esc(el.text || '')}" placeholder="Enter text…" aria-label="Text"></div>`) +
        this.glyphPicker(el) + this.fontPanel(el) + this.alignPanel(el) + this.rotationPanel(el);
    }
    livePanel(el) {
      const list = el.type === 'sprite' ? SPRITE_ORDER : NUMBER_ORDER;
      const labels = (b) => (FC.binding(b) || {}).label || b;
      const b = FC.binding(el.bind);
      let h = this.section('BINDING', this.chips('bind', list, el.bind, (v) => this.conflicts(v, el.id), labels));
      if (el.bind && !b) h += `<div class="inspector-section" style="color:var(--accent);font-size:12px">"${esc(el.bind)}" is not supported on this watch.</div>`;
      h += this.glyphPicker(el) + this.fontPanel(el) + this.alignPanel(el);
      if (el.type === 'sprite') h += this.rotationPanel(el);
      return h;
    }
    shapePanel(el) {
      const s = el.shape || 'rect';
      return this.section('SHAPE', this.chips('shape', SHAPES, s)) +
        this.section('STYLE', `<div class="inspector-row">${this.num('Stroke', 'stroke', el, { def: 2, min: 0.5, max: 40, step: 0.5, kind: 'float' })}${this.num(s === 'rect' ? 'Radius' : 'Size', 'size', el, { def: 16, min: 0, max: 120, kind: 'float' })}</div>
          <div class="inspector-row">${this.num('Count', 'count', el, { def: 12, min: 1, max: 240 })}<label style="display:flex;align-items:center;gap:4px;font-size:11px;color:var(--text-secondary)"><input type="checkbox" data-prop="fill" data-kind="bool"${el.fill !== false ? ' checked' : ''}> Fill</label></div>
          <div class="inspector-row">${this.num('Start°', 'startAngle', el, { def: 0, min: 0, max: 360, kind: 'float' })}${this.num('Sweep°', 'sweep', el, { def: 360, min: 0, max: 360, kind: 'float' })}</div>`);
    }
    handPanel(el) {
      const b = FC.binding(el.bind);
      const hint = el.pivotX != null && el.pivotX !== 120 || el.pivotY != null && el.pivotY !== 140
        ? `<div style="color:var(--accent);font-size:12px;margin-top:6px">Pivot is ${esc(el.pivotX)}, ${esc(el.pivotY)}. The watch only turns hands around 120, 140.</div>
           <button class="glyph-option" data-action="reset-pivot" style="width:100%;margin-top:6px">Reset pivot</button>` : '';
      return this.section('HAND', this.chips('bind', HAND_ORDER, el.bind, (v) => this.conflicts(v, el.id), (v) => v.split('.')[1])) +
        this.section('STYLE', `<div class="hand-style-grid">${HAND_STYLES.map((s) => `<button class="hand-style-option${(el.handStyle || 'bar') === s ? ' selected' : ''}" data-set="handStyle" data-value="${s}"><span class="hand-style-label">${s}</span></button>`).join('')}</div>`) +
        this.section('DIMENSIONS', `<div class="inspector-row">${this.num('Length', 'length', el, { def: 80, min: 4, max: 140 })}${this.num('Tail', 'tail', el, { def: 12, min: 0, max: 60 })}</div>
          <div class="inspector-row">${this.num('Width', 'stroke', el, { def: 2, min: 1, max: 30, kind: 'float' })}${this.num('Cap', 'size', el, { def: 16, min: 0, max: 60, title: 'Centre cap diameter; 0 hides it' })}</div>
          <div style="color:var(--text-disabled);font-size:11px;margin-top:6px">Pivot is fixed at 120, 140 (the watch rotates hands around it).${b ? '' : ' Pick a hand binding.'}</div>${hint}`);
    }
    imagePanel(el) {
      const names = Object.keys(this.spec.assets);
      return this.section('IMAGE', `<div class="inspector-row"><select class="inspector-input" data-prop="src" data-kind="str" aria-label="Image asset"><option value="">— none —</option>${names.map((n) => `<option value="${esc(n)}"${el.src === n ? ' selected' : ''}>${esc(n)}</option>`).join('')}</select></div>
        <button class="glyph-option" data-action="upload-image" style="width:100%;margin-top:6px">Upload image…</button>
        <div class="inspector-row" style="margin-top:6px">${this.num('H', 'h', el, { min: 0 })}</div>`);
    }
    backgroundPanel() {
      const b = this.spec.background || {};
      const names = Object.keys(this.spec.assets);
      const pat = b.pattern || 'none';
      const problems = this.report.problems.filter((p) => !p.id);
      return (problems.length ? this.section('PROBLEMS', problems.map((p) => `<div style="color:var(--accent);font-size:12px;margin-bottom:4px">${esc(p.msg)}</div>`).join('')) : '') +
        this.section('BACKGROUND', `<div style="color:var(--text-disabled);font-size:11px;margin-bottom:8px">Nothing selected. Click an element, or edit the background.</div>`) +
        this.color('BACKGROUND COLOR', 'bg:color', b, '#000000') +
        this.section('PATTERN', this.seg('bg:pattern', [['none', 'None'], ['dots', 'Dots'], ['grid', 'Grid'], ['lines', 'Lines']], pat) +
          (pat !== 'none' ? `<div class="inspector-row" style="margin-top:8px">${this.num('Gap', 'bg:patternSpacing', { 'bg:patternSpacing': b.patternSpacing }, { def: 12, min: 4, max: 64 })}</div>` : '')) +
        (pat !== 'none' ? this.color('PATTERN COLOR', 'bg:patternColor', b, '#222222') : '') +
        this.section('BACKGROUND IMAGE', `<div class="inspector-row"><select class="inspector-input" data-prop="bg:image" data-kind="str" aria-label="Background image"><option value="">— none —</option>${names.map((n) => `<option value="${esc(n)}"${b.image === n ? ' selected' : ''}>${esc(n)}</option>`).join('')}</select></div>
          <button class="glyph-option" data-action="upload-bg" style="width:100%;margin-top:6px">Upload image…</button>`);
    }

    syncPositionInputs() {
      const el = this.selected();
      if (!el) return;
      for (const p of ['x', 'y', 'w', 'h']) {
        const input = this.inspector.querySelector(`input[data-prop="${p}"]`);
        if (input && document.activeElement !== input) input.value = el[p] == null ? '' : el[p];
      }
    }

    onInspectorInput(e) {
      const t = e.target, prop = t.dataset.prop;
      if (!prop) return;
      const kind = t.dataset.kind;
      if (kind === 'id') return; // applied on change
      this.applyProp(prop, kind, t);
    }
    onInspectorChange(e) {
      const t = e.target, prop = t.dataset.prop;
      if (!prop) return;
      if (t.dataset.kind === 'id') {
        const el = this.selected(), id = t.value.trim().slice(0, 40);
        if (!el || !id || id === el.id) { t.value = el ? el.id : ''; return; }
        if (this.byId(id)) { this.toast(`"${id}" is already used`); t.value = el.id; return; }
        this.pushUndo(); el.id = id; this.selectedId = id; this.changed(true);
      } else if (t.tagName === 'SELECT' || t.dataset.kind === 'colorpick') this.applyProp(prop, t.dataset.kind, t, true);
    }
    applyProp(prop, kind, t, structural) {
      const bgProp = prop.startsWith('bg:') ? prop.slice(3) : null;
      const el = bgProp ? this.spec.background : this.selected();
      if (!el) return;
      let val;
      if (kind === 'int' || kind === 'float') {
        if (t.value === '' || isNaN(+t.value)) { if (t.value === '') val = 0; else return; }
        else val = kind === 'int' ? Math.round(+t.value) : +t.value;
        if (t.min !== '' && val < +t.min && kind !== 'int') val = +t.min;
        if (t.max !== '' && val > +t.max) val = +t.max;
        if (kind === 'int' && t.min !== '' && val < +t.min && !(t.placeholder === 'auto' && val === 0)) val = +t.min;
      } else if (kind === 'bool') val = t.checked;
      else if (kind === 'color' || kind === 'colorpick') {
        val = kind === 'colorpick' ? t.value.toUpperCase() : t.value.trim();
        const valid = COLOR_RE.test(val);
        t.style.outline = valid ? '' : '1px solid var(--accent)';
        if (!valid) return;
        const row = t.closest('.inspector-color');
        const sw = row.querySelector('.color-swatch'), tx = row.querySelector('.color-input'), pk = row.querySelector('input[type=color]');
        sw.style.background = val; if (kind === 'colorpick') tx.value = val; else pk.value = hex6(val);
      } else val = t.value;
      this.pushUndo(`${this.selectedId}:${prop}`);
      if (kind === 'str' && prop === 'src' && !val) delete el[prop]; else if (prop === 'bg:image' && !val) delete el.image; else el[bgProp || prop] = val;
      if (prop === 'opacity') { const l = this.inspector.querySelector('[data-opacity-label]'); if (l) l.textContent = `${Math.round(val * 100)}%`; }
      this.changed(!!structural);
    }
    onInspectorClick(e) {
      const set = e.target.closest('[data-set]');
      if (set && !set.disabled) {
        const prop = set.dataset.set;
        let val = set.dataset.value;
        if (prop === 'rotation') val = +val;
        const bgProp = prop.startsWith('bg:') ? prop.slice(3) : null;
        const el = bgProp ? this.spec.background : this.selected();
        if (!el) return;
        this.pushUndo();
        el[bgProp || prop] = val;
        if (prop === 'glyph' && val !== 'font' && el.ghost === undefined) el.ghost = '#262626';
        if (prop === 'bind') this.afterBindChange(el);
        this.changed(true);
        return;
      }
      const action = e.target.closest('[data-action]');
      if (!action) return;
      const el = this.selected();
      switch (action.dataset.action) {
        case 'clear-ghost': if (el) { this.pushUndo(); delete el.ghost; this.changed(true); } break;
        case 'reset-pivot': if (el) { this.pushUndo(); el.pivotX = 120; el.pivotY = 140; this.changed(true); } break;
        case 'upload-image': this.chooseImage((name) => { const s = this.selected(); if (s) { this.pushUndo(); s.src = name; this.changed(true); } }); break;
        case 'upload-bg': this.chooseImage((name) => { this.pushUndo(); this.spec.background.image = name; this.changed(true); }); break;
        default: break;
      }
    }
    afterBindChange(el) {
      const b = FC.binding(el.bind);
      if (b && el.type === 'hand') { el.length = el.length || (el.bind === 'hand.hour' ? 55 : 80); }
    }

    // ------------------------------------------------------------ images
    chooseImage(done) {
      const input = document.createElement('input');
      input.type = 'file'; input.accept = 'image/png,image/jpeg,image/webp';
      input.addEventListener('change', async () => {
        const file = input.files[0];
        if (!file) return;
        if (file.size > 4 * 1024 * 1024) return this.toast('Image is larger than 4 MB');
        try {
          const bitmap = await createImageBitmap(file);
          const scale = Math.min(1, 480 / Math.max(bitmap.width, bitmap.height));
          const c = document.createElement('canvas');
          c.width = Math.max(1, Math.round(bitmap.width * scale)); c.height = Math.max(1, Math.round(bitmap.height * scale));
          c.getContext('2d').drawImage(bitmap, 0, 0, c.width, c.height);
          const blob = await new Promise((r) => c.toBlob(r, 'image/png'));
          const data = bytesToBase64(new Uint8Array(await blob.arrayBuffer()));
          let name = safeName(file.name.replace(/\.[^.]+$/, ''), 'image'), n = 2;
          const base = name;
          while (this.spec.assets[name]) name = `${base}-${n++}`;
          this.spec.assets[name] = data;
          await R.preloadAssets(this.spec);
          done(name);
        } catch (err) { this.toast(`Could not read that image (${err.message})`); }
      });
      input.click();
    }

    // ------------------------------------------------------------ safety / compile
    queueEvaluate(fast) {
      clearTimeout(this._eval);
      this._eval = setTimeout(() => this.evaluate(), fast ? 350 : 150);
    }

    preflight(spec) {
      const out = [], add = (msg, id) => out.push({ msg, id });
      if (spec.width !== W0 || spec.height !== H0) add(`Face is ${spec.width}×${spec.height}; this watch needs ${W0}×${H0}`);
      const owners = new Map();
      for (const e of spec.elements) {
        if (!(e.type === 'number' || e.type === 'sprite' || e.type === 'hand')) continue;
        const b = FC.binding(e.bind);
        if (!b) { add(`"${e.id}": ${e.bind ? `"${e.bind}" is not supported on this watch` : 'choose what it shows'}`, e.id); continue; }
        if (e.recordId != null || e.recordType != null || e.recordName != null) add(`"${e.id}" uses a data id that is not proven safe on this watch`, e.id);
        if (e.type === 'hand' && b.kind !== 'hand') add(`"${e.id}" is a hand but is bound to ${b.label}`, e.id);
        if (e.type !== 'hand' && b.kind === 'hand') add(`"${e.id}" shows a hand binding but is not a hand`, e.id);
        if ((e.type === 'hand') && ((e.pivotX != null && e.pivotX !== 120) || (e.pivotY != null && e.pivotY !== 140))) add(`"${e.id}": hands must turn around 120, 140`, e.id);
        const key = e.bind;
        if (owners.has(key)) add(`${b.label} is shown twice ("${owners.get(key)}" and "${e.id}")`, e.id); else owners.set(key, e.id);
      }
      for (const base of ['time.hour', 'time.minute']) {
        for (const part of ['tens', 'units']) {
          if (owners.has(base) && owners.has(`${base}.${part}`)) add(`${FC.binding(base).label} can't be combined with its single-digit fields ("${owners.get(`${base}.${part}`)}")`, owners.get(`${base}.${part}`));
        }
      }
      return out;
    }

    evaluate() {
      const spec = this.spec, rep = { problems: this.preflight(spec), notes: [], records: 0, bytes: 0 };
      if (!rep.problems.length) {
        try {
          const out = FC.compile(spec, window.FaceRasterizer);
          rep.records = out.records.length; rep.bytes = out.bytes.length; rep.notes = out.warnings; rep.compiled = out;
          const named = (m) => {
            const mm = /^Element (\d+): (.*)$/.exec(m);
            if (!mm) return { msg: m };
            const rec = out.records[+mm[1] - 1];
            return rec && rec.src ? { id: rec.src, msg: `"${rec.src}": ${mm[2]}` } : { msg: m };
          };
          FC.checkSafety(out.bytes).forEach((m) => rep.problems.push(named(m)));
        } catch (err) {
          rep.problems.push({ msg: err instanceof FC.UnsafeFaceError ? err.message : `Cannot build this face: ${err.message}` });
        }
      }
      const sig = JSON.stringify(rep.problems);
      const changed = sig !== this.problemSig;
      this.problemSig = sig;
      this.report = rep;
      this.paintStatus();
      if (changed) {
        this.renderLayers();
        if (!this.drag && !isInput(document.activeElement)) this.renderInspector();
      }
    }

    paintStatus() {
      const r = this.report;
      this.safety.className = `safety-indicator ${r.problems.length ? 'fail' : 'pass'}`;
      this.safety.style.cursor = 'pointer';
      this.safety.textContent = r.problems.length ? `[SAFETY: ${r.problems[0].msg}${r.problems.length > 1 ? ` +${r.problems.length - 1}` : ''}]` : (r.notes.length ? '[SAFETY PASSED · NOTE]' : '[SAFETY CHECK PASSED]');
      this.safety.title = r.problems.concat(r.notes.map((m) => ({ msg: m }))).map((p) => p.msg).join('\n') || 'All elements use records proven safe on the NF 2';
      this.recordDisplay.textContent = `${r.records} / ${64} records`;
      this.sizeDisplay.textContent = `${(r.bytes / 1024).toFixed(1)} / 400 KB`;
    }

    showProblems() {
      const r = this.report;
      const box = document.getElementById('problems-list');
      if (!box) return;
      const rows = [...r.problems.map((p) => `<div style="color:var(--accent);margin-bottom:8px">✕ ${esc(p.msg)}</div>`), ...r.notes.map((m) => `<div style="color:var(--text-secondary);margin-bottom:8px">• ${esc(m)}</div>`)];
      box.innerHTML = rows.join('') || '<div style="color:var(--success)">All elements use records proven safe on the NF 2.</div>';
      this.openModal('problems-modal');
    }

    // ------------------------------------------------------------ code view
    toggleCode() {
      this.showCode = !this.showCode;
      this.codeView.classList.toggle('visible', this.showCode);
      document.getElementById('btn-code').classList.toggle('active', this.showCode);
      this.refreshCode(true);
    }
    codeText() {
      const assets = {};
      Object.keys(this.spec.assets).forEach((k) => { assets[k] = ASSET_PLACEHOLDER; });
      return JSON.stringify({ ...this.spec, assets }, null, 2);
    }
    refreshCode(force) {
      if (!this.showCode) return;
      if (!force && document.activeElement === this.codeEditor) return;
      this.codeEditor.value = this.codeText(); this.codeEditor.style.outline = '';
    }
    applyCode() {
      try {
        const parsed = JSON.parse(this.codeEditor.value);
        const assets = {};
        for (const [k, v] of Object.entries(parsed.assets || {})) {
          if (v === ASSET_PLACEHOLDER) { if (this.spec.assets[k]) assets[k] = this.spec.assets[k]; } else if (typeof v === 'string') assets[k] = v;
        }
        const next = normalizeSpec({ ...parsed, assets });
        this.codeEditor.style.outline = '';
        this.pushUndo('code');
        this.spec = next;
        if (!this.byId(this.selectedId)) this.selectedId = null;
        this.nameInput.value = this.spec.name;
        this.render(); this.renderLayers(); this.renderInspector(); this.updateOverrideBar(); this.queueEvaluate(); this.saveDraft();
      } catch (err) {
        this.codeEditor.style.outline = '1px solid var(--accent)';
        this.codeEditor.title = err.message;
      }
    }

    updateOverrideBar() {
      const binds = new Set(this.spec.elements.map((e) => e.bind));
      this.overrideBar.innerHTML = OVERRIDES.filter(([b]) => binds.has(b)).map(([b, label]) => {
        const v = this.overrides[b] != null ? this.overrides[b] : (FC.binding(b) || {}).sample;
        return `<button class="value-override-chip" data-override="${b}" title="Click to cycle the preview value">${esc(label)}: ${esc(v)}</button>`;
      }).join('');
    }

    // ------------------------------------------------------------ modals
    openModal(id) { this.closeModals(); document.getElementById(id).classList.add('visible'); }
    closeModals() { document.querySelectorAll('.modal-overlay.visible').forEach((m) => m.classList.remove('visible')); }

    showPresets() {
      this.openModal('presets-modal');
      const grid = document.querySelector('#presets-modal .modal-presets-grid');
      if (grid.childElementCount) return;
      Object.keys(window.PRESETS).forEach((key) => {
        const p = window.PRESETS[key];
        const card = document.createElement('div');
        card.className = 'preset-card'; card.tabIndex = 0; card.style.cursor = 'pointer'; card.title = p.description || '';
        const canvas = document.createElement('canvas');
        canvas.width = 240; canvas.height = 280;
        const info = document.createElement('div');
        info.className = 'preset-card-info';
        const name = document.createElement('span');
        name.className = 'preset-card-name'; name.textContent = p.name;
        info.appendChild(name); card.append(canvas, info);
        const pick = () => {
          if (this.spec.elements.length && !confirm(`Replace the current face with "${p.name}"? Undo can bring it back.`)) return;
          this.pushUndo(); this.spec = this.presetSpec(key); this.selectedId = null; this.overrides = {};
          this.closeModals(); this.syncAll(); this.saveDraft();
        };
        card.addEventListener('click', pick);
        card.addEventListener('keydown', (e) => { if (e.key === 'Enter') pick(); });
        grid.appendChild(card);
        R.renderFace(canvas.getContext('2d'), this.presetSpecRaw(key));
      });
    }
    presetSpecRaw(key) { const { category, description, ...spec } = window.PRESETS[key]; return spec; }

    showExport() {
      const note = document.getElementById('export-note');
      const r = this.report;
      note.textContent = r.problems.length
        ? `This face has ${r.problems.length} safety problem${r.problems.length > 1 ? 's' : ''}; the watch file (.bin) is disabled until they are fixed. The design files still export.`
        : `Safe for the NF 2: ${r.records} records, ${(r.bytes / 1024).toFixed(1)} KB.`;
      const bin = document.getElementById('export-bin');
      if (bin) bin.style.opacity = r.problems.length ? '0.4' : '1';
      this.openModal('export-modal');
    }
    fileBase() { return safeName(this.spec.name, 'face'); }
    exportJson() {
      downloadBlob(new Blob([JSON.stringify(this.spec, null, 2)], { type: 'application/json' }), `${this.fileBase()}.sfface.json`);
      this.closeModals();
    }
    exportPackage() {
      const enc = new TextEncoder();
      const entries = [{ name: 'face.json', data: enc.encode(JSON.stringify({ ...this.spec, assets: {} }, null, 2)) }];
      for (const [name, data] of Object.entries(this.spec.assets)) {
        try { entries.push({ name: `assets/${safeName(name, 'image')}.png`, data: base64ToBytes(data) }); } catch (_) { /* skip damaged asset */ }
      }
      downloadBlob(new Blob([Z.zipStore(entries)], { type: 'application/zip' }), `${this.fileBase()}.sfface`);
      this.closeModals();
    }
    async exportBin() {
      await R.preloadAssets(this.spec);
      this.evaluate();
      const r = this.report;
      if (r.problems.length || !r.compiled) { this.toast('Fix the safety problems first'); this.showProblems(); return; }
      downloadBlob(new Blob([r.compiled.bytes], { type: 'application/octet-stream' }), `${this.fileBase()}.bin`);
      this.closeModals();
    }
    async exportPng() {
      await R.preloadAssets(this.spec);
      const c = document.createElement('canvas');
      c.width = this.spec.width * 2; c.height = this.spec.height * 2;
      const ctx = c.getContext('2d');
      ctx.beginPath(); ctx.roundRect(0, 0, c.width, c.height, 56); ctx.clip();
      R.renderFace(ctx, this.spec, { time: this.time(), values: this.values() });
      c.toBlob((b) => { downloadBlob(b, `${this.fileBase()}.png`); this.closeModals(); }, 'image/png');
    }

    // ------------------------------------------------------------ import
    importDialog() {
      const input = document.createElement('input');
      input.type = 'file'; input.accept = '.sfface,.json,application/json,application/zip';
      input.addEventListener('change', () => { if (input.files[0]) this.importFile(input.files[0]); });
      input.click();
    }
    async importFile(file) {
      try {
        if (file.size > MAX_IMPORT) throw new Error('File is larger than 8 MB');
        const bytes = new Uint8Array(await file.arrayBuffer());
        let spec;
        if (bytes.length > 4 && bytes[0] === 0x50 && bytes[1] === 0x4B) {
          const files = await Z.unzip(bytes, MAX_IMPORT);
          const json = files.find((f) => /\.json$/i.test(f.name));
          if (!json) throw new Error('face.json is missing from the archive');
          const raw = JSON.parse(new TextDecoder().decode(json.data));
          const assets = { ...(raw.assets || {}) };
          for (const f of files) {
            const n = f.name.split('/').pop();
            if (/\.(png|jpe?g)$/i.test(n)) assets[n.replace(/\.[^.]+$/, '')] = bytesToBase64(f.data);
          }
          spec = normalizeSpec({ ...raw, assets });
        } else if (bytes[0] === 0x57 && bytes[1] === 0x46) {
          throw new Error('This is a compiled watch file. Import it in the SilentFit app (Faces › Import) instead.');
        } else spec = normalizeSpec(JSON.parse(new TextDecoder().decode(bytes)));
        this.pushUndo(); this.spec = spec; this.selectedId = null; this.overrides = {};
        await R.preloadAssets(spec);
        this.syncAll(); this.saveDraft();
        this.toast(`Imported "${spec.name}"`);
      } catch (err) { this.toast(`Could not import: ${err.message}`); }
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    document.fonts.ready.then(() => { window.designer = new FaceDesigner(); });
  });
  window.FaceDesigner = FaceDesigner;
})();
