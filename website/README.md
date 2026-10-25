# SilentFit website and Studio

Static site (no build step). Run locally: `cd website && python3 -m http.server 8765`, then open `/designer/index.html`.
Deploy: publish the `website/` folder with GitHub Pages.

- `js/core.js`: port of the app's binding table, PAR codec, WF writer, compiler and FaceSafety.
- `js/renderer.js`: port of `AndroidFaceRasterizer`, so the preview matches the watch.
- `js/presets.js`: generated, see `tools/make-presets.js`.
- Exports: `.sfface` (zip), `.sfface.json`, compiled `.bin` (importable in the app), `.png`.
- Web Bluetooth cannot do Classic SPP, so installing on the watch goes through the Android app.
