# Disclaimer

Entirely vibe coded and mainly for personal use

# Angle Watcher

A client-side Fabric mod for **Minecraft 26.2** that adds a configurable compass HUD:
a scrolling **heading tape** (yaw) and an optional vertical **pitch tape**, each with
configurable position, size, and multiple configurable **angle ranges** drawn as colored bands.

## Features

- **Heading tape** — a scrolling compass strip centered on your current heading in
  vanilla yaw (−180° = North, −90° = East, 0° = South, +90° = West). Tick marks every
  5°/15°, cardinal letters, and a numeric readout.
- **Pitch tape** — the same strip vertically for your look pitch (−90° = up, +90° = down).
- **Angle ranges** — add or remove ranges freely (up to 10 per tape), each with name, start/end angle,
  ARGB color (with alpha), and an enable toggle. Ranges draw as translucent bands and
  wrap correctly across the ±180° seam (e.g. 150° → −150°).
- **Angle pins** — pin up to 20 arbitrary angles per tape, drawn as named, colored marker
  lines. Add them in the config screen or with the HUD editor's pin mode (`R`).
- **Range alerts** — optionally play a vanilla sound while the current angle is inside an
  enabled range of a tape (sound and cooldown configurable per tape).
- **Position & size** — 9-position anchor, X/Y offsets, explicit width/height, and
  background opacity per tape.
- **Mouse-driven HUD editor** — an in-game editor (like moving desktop windows): drag a
  tape to move it, grab its edge/corner handles to resize it freely, and cycle its anchor.
  Live preview; changes persist to `config/anglewatcher.json` on close.
  Open it via the **HUD Editor keybind** (unbound by default, under *Angle Watcher* in the
  Controls screen) or the **Open HUD Editor** button in the YACL config screen.
- **YACL config screen** — accessible from **Mod Menu → Angle Watcher → Configure**.
  Changes apply live and persist to `config/anglewatcher.json`.
- **Camera limit** — while the view is inside an enabled range whose camera-limit flag is
  set, the camera cannot leave that range (no snapping; it holds at the boundary). Needs
  the *Range Camera Limit* master switch and the tape's own camera-limit switch.
- **Per-range camera-limit keybinds** — 20 keybinds in the *Angle Watcher* category
  (*Yaw Range 1–10 Camera Limit Toggle*, *Pitch Range 1–10 Camera Limit Toggle*), each
  flipping the camera-limit flag of the range at that position in the corresponding
  ranges list (the order shown in the config screen). Turning a range on also enables
  that tape's camera-limit switch and the master switch if they are off. All keybinds are
  unbound by default — assign them in **Options → Controls → Key Binds**.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | ≥ 0.19.5 |
| Fabric API | 0.160.0+26.2 (any 26.2 build) |
| YACL (YetAnotherConfigLib) | 3.9.6+26.2-fabric (optional — only needed for the config screen) |
| Mod Menu | 20.0.2 (optional — only needed for the config screen entry) |
| Java | 25+ |

The HUD itself (tapes, editor, keybinds, commands) works with just Fabric API.

## Assets

- `assets/anglewatcher/icon.png` (128×128) — the mod icon, referenced from
  `fabric.mod.json` and shown by Mod Menu. Regenerate it with `tools/IconGen.java`
  (`java tools/IconGen.java <output-dir>`).
- `assets/anglewatcher/textures/gui/icon16.png` (16×16) — small icon drawn in-game
  next to the HUD editor title.

## Building

```bash
./gradlew build
```

The jar is produced at `build/libs/anglewatcher-1.1.0.jar`. A Java 25 toolchain
is resolved automatically (via the Gradle Foojay resolver), so no specific local
JDK install is required to build.

## Usage

1. Drop the jar in your `mods/` folder together with **Fabric API** (required). Add
   **YACL** and **Mod Menu** as well if you want the in-game config screen.
2. **Config screen** (needs Mod Menu + YACL): open **Mods → Angle Watcher → Configure**.
   - *General* — the master switch, the *Range Camera Limit* master switch, and an
     **Open HUD Editor** button.
   - *Heading Tape (Yaw)* / *Pitch Tape (Vertical)* — position, size, appearance, angle
     pins, and the range alert sound for that tape.
   - *Heading Angle Ranges* / *Pitch Angle Ranges* — **Add Angle Range** (up to 10 per
     tape); each range is a collapsible group with name, start/end angle, color, enable
     and camera-limit toggles, and a **Remove This Range** button.
   - Numeric settings are shown as both a slider and an exact-value box inside a
     collapsible group — edit whichever you prefer.
3. **HUD editor**: assign the *Open HUD Editor* keybind under *Angle Watcher* in
   **Options → Controls → Key Binds** (or use the editor buttons in the config screen).
   Drag a tape to move it, grab an edge/corner handle to resize, click *Anchor* to cycle
   the anchor, press `R` for pin mode (click to drop a pin, Shift+click a pin to remove
   it), arrow keys nudge, `Ctrl+Z` / `Ctrl+Y` undo and redo. Closing the editor saves to
   `config/anglewatcher.json`.
4. **Camera limit**: switch on *Range Camera Limit* (General tab or its keybind), enable a
   tape's camera-limit switch, and flag the ranges that should hold the view. The
   *Yaw Range 1–10* / *Pitch Range 1–10* keybinds toggle individual ranges by their
   position in the config screen's list. There are also unbound keybinds for
   *Toggle Angle Watcher Rendering* and *Toggle Range Camera Limit*.

## License

MIT — see [LICENSE](LICENSE).
