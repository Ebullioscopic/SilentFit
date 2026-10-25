# Designing Watch Faces

SilentFit's face studio builds faces for the Noise NF 2 (240 × 280) two ways: by
**drag and drop** in the app, or as a **code file** you write and import. Both produce
the same `FaceSpec`, so you can switch between them at any time.

Every face passes the safety check before it reaches the watch (see
[WATCHFACE_FORMAT.md §6](WATCHFACE_FORMAT.md#6-safety-check-facesafety)). The studio
only offers data the watch is proven to render.

## 1. Drag and drop

**Faces** tab → **+** (or tap a preset) opens the editor.

| Panel | What it does |
|-------|--------------|
| **Layers** | Every element in drawing order: select, bring forward / send backward, lock |
| **Style** | The selected element: position, font, size, weight, colour, opacity, alignment, tracking, shape and hand options, delete |
| **Add** | Live data (hands, digital hour and minute, AM/PM, day, month, weekday, steps, heart rate, calories, battery), text, shapes (dot ring, ticks, arc, ring, rectangle, segments, dot matrix, red dot) and your own images |
| **Face** | Background colour, pattern (`none`, `dots`, `grid`, `lines`), background photo, name, export, delete |

On the canvas: tap to select, drag to move. Elements snap to the horizontal and vertical
centre. Undo/redo sit in the header; the code button opens the same face as JSON.

Below the canvas the editor shows the **safety check** result as you work. *Install on
watch* is enabled only when it passes. Installing asks for confirmation:

* **Install & wear** — installs and selects the face as a 3-minute trial. If the watch
  disconnects during the trial, SilentFit switches back to a built-in face and removes
  the new one. Tap *It works — keep it* to end the trial early.
* **Install only** — installs; select it later from the watch or the *On your watch* list.

The NF 2 holds one downloaded face. If it is full, SilentFit asks before replacing the
old one; built-in faces are never touched.

### Presets

| Preset | Look |
|--------|------|
| **Dot Matrix** | Nothing's dotted dial: Doto numerals, dot hands, red accent, weekday + day |
| **Dot Matrix Digital** | Large Doto hours over minutes on a dotted field, weekday, day and heart rate |
| **LED Matrix** | 5×7 LED digits with unlit dots showing, red colon, BPM and battery |
| **Segment** | Red seven-segment time over ghost segments, steps, date and battery |
| **Stack** | Light Grotesk hours over red minutes, vertical weekday on the edge |
| **Glyph Data** | Compact Doto time above steps, kcal, BPM and battery rows, like Nothing widgets |
| **Instrument** | 60-tick ring, needle minute hand, BPM and steps readouts |
| **Paper** | Light, printed-manual face with black hands and the date |
| **Glyph Rings** | Segmented arcs around line hands, step count |

## 2. Code files

A face is a JSON document with `"format": "silentfit.face"`. Save it as
`name.sfface.json` and import it from **Faces → import**, or open the code view of any
design to edit it in place.

```json
{
  "format": "silentfit.face",
  "version": 1,
  "name": "Minimal Dots",
  "author": "you",
  "background": { "color": "#000000", "pattern": "dots", "patternColor": "#1A1A1A", "patternSpacing": 12 },
  "elements": [
    { "id": "ring", "type": "shape", "x": 10, "y": 30, "w": 220, "h": 220,
      "shape": "dot-ring", "count": 60, "stroke": 1.6, "color": "#666666" },
    { "id": "label", "type": "text", "x": 0, "y": 196, "w": 240, "text": "silentfit",
      "font": "space-mono", "size": 10, "align": "center", "uppercase": true, "tracking": 0.12, "color": "#999999" },
    { "id": "weekday", "type": "sprite", "x": 74, "y": 214, "bind": "date.weekday",
      "font": "space-mono", "size": 13, "color": "#999999" },
    { "id": "day", "type": "number", "x": 112, "y": 211, "bind": "date.day",
      "font": "doto", "size": 18, "weight": 700 },
    { "id": "hr", "type": "number", "x": 30, "y": 250, "bind": "health.heartRate", "color": "#D71921" },
    { "id": "hour", "type": "hand", "bind": "hand.hour", "handStyle": "dots", "length": 58, "stroke": 5 },
    { "id": "minute", "type": "hand", "bind": "hand.minute", "handStyle": "dots", "length": 92, "stroke": 4 },
    { "id": "pin", "type": "shape", "x": 116, "y": 136, "w": 8, "h": 8, "shape": "circle", "color": "#D71921" }
  ]
}
```

Elements are drawn in list order. Unknown keys are ignored, so files stay forward
compatible.

### Top level

| Key | Default | Notes |
|-----|---------|-------|
| `format` | — | must be `silentfit.face` |
| `version` | 1 | |
| `name`, `author` | | shown in the app |
| `width`, `height` | 240, 280 | the NF 2 only accepts 240 × 280 |
| `background` | black | `color`, `image` (asset name), `pattern`, `patternColor`, `patternSpacing` |
| `elements` | `[]` | layers, see below |
| `assets` | `{}` | name → base64 PNG/JPEG, referenced by `image` elements and `background.image` |

### Element types

| `type` | Becomes | Uses |
|--------|---------|------|
| `number` | live digits | `bind`, `font`, `size`, `weight`, `color`, `align`, `w` |
| `sprite` | live indexed text | `bind` (`date.weekday`, `time.ampm`), `font`, `size`, `uppercase` |
| `hand` | rotating hand | `bind`, `handStyle`, `length`, `tail`, `stroke`, `color`, `size` (centre cap) |
| `text` | static text | `text`, `font`, `size`, `weight`, `align` (`left`/`center`/`right`), `tracking`, `uppercase` |
| `shape` | static drawing | `shape`, `x`, `y`, `w`, `h`, `stroke`, `fill`, `count`, `startAngle`, `sweep`, `size` |
| `image` | static image | `src` (asset name), `x`, `y`, `w`, `h` |

Common keys: `id` (unique), `x`, `y`, `w`, `h`, `color` (`#RGB`, `#RRGGBB`,
`#AARRGGBB`), `opacity` (0–1), `locked`.

Text, numbers and sprites also take:

| Key | Values | Notes |
|-----|--------|-------|
| `glyph` | `font` (default), `dot-matrix`, `segment` | `dot-matrix` is a 5×7 LED font (`size` = character height); `segment` draws 7-segment digits (letters fall back to LED dots) |
| `ghost` | colour or omitted | unlit dots/segments, e.g. `#1C1C1C` |
| `rotation` | `0`, `90`, `180`, `270` | text and sprites only (official faces rotate the weekday this way) |

Single-glyph values (weekday, AM/PM, `time.*`) are placed in a box: set `w` and
`align` (`left`, `center`, `right`) to position them inside it.

Static layers (text, shapes, images, background) are merged into one background image,
so you can use as many as you like without cost on the watch.

### Live data (`bind`)

| Key | Element | Shows |
|-----|---------|-------|
| `hand.hour`, `hand.minute` | hand | analogue time |
| `time.hour`, `time.minute` | number | digital time, two digits; `align: "center"` with `w` centres them |
| `time.hour.tens`, `time.hour.units`, `time.minute.tens`, `time.minute.units` | number | one digit each, for free layouts (e.g. a 2 × 2 grid); don't combine with the two-digit version of the same value |
| `time.ampm` | sprite | AM / PM |
| `date.month`, `date.day` | number | date |
| `date.weekday` | sprite | MON … SUN |
| `activity.steps` | number | steps (up to 5 digits) |
| `activity.calories` | number | kcal |
| `health.heartRate` | number | BPM; shows `--` until there is a reading |
| `device.battery` | number | battery % |

Seconds, year, distance and SpO₂ are **not available**: no official face for this watch
uses them yet, so their data ids are unproven.

### Shapes

`rect` (corner radius = `size`), `circle`, `ring`, `arc` (`startAngle` from 12 o'clock,
`sweep` degrees), `line` (box diagonal), `ticks` (`count`, length = `size`, every
`count/12`th is a major tick), `dot-ring` (`count` dots of radius `stroke`), `segments`
(`count` bars, `sweep/360` of them lit), `dot-matrix` (grid of dots of radius `stroke`).

### Hands

`handStyle`: `bar` (default), `needle`, `line`, `dots`, `dot`, `outline`. Hands always
turn around the screen centre (120, 140) — the only pivot proven on the watch.

### Fonts

`doto` (dot-matrix), `space-mono`, `space-grotesk` — the Nothing design typefaces,
bundled under the SIL Open Font License.

### Limits

* live characters ≤ 120 px wide and ≤ 87 px tall (Doto ≈ 70 px font size); live values
  must stay on screen
* each binding at most once per face
* compiled file ≤ 400 KB (typical faces are 15–60 KB)

## 3. File types

| File | Contents | Import | Export |
|------|----------|:------:|:------:|
| `*.sfface.json` | the JSON above | ✅ | via code view |
| `*.sfface` | zip: `face.json` + `assets/*.png` | ✅ | ✅ Face › Export › *.sfface* |
| `*.bin` (WF) | compiled watch file | ✅ only if it passes the safety check | ✅ Face › Export › *Watch file* |

Imported WF files from other sources are rejected unless they match the official layout
exactly; this protects the watch from faces built for other models.
