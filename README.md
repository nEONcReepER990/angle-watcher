# Angle Watcher

A client-side Fabric mod for **Minecraft 26.2** that adds a configurable compass HUD:
a scrolling **heading tape** (yaw) and an optional vertical **pitch tape**, each with
configurable position, size, and multiple configurable **angle ranges** drawn as colored bands.

## Features

- **Heading tape** — a scrolling compass strip centered on your current heading
  (0° = North, clockwise). Tick marks every 5°/15°, cardinal letters, and a numeric readout.
- **Pitch tape** — the same strip vertically for your look pitch (−90° = up, +90° = down).
- **Angle ranges** — add or remove ranges freely (up to 10 per tape), each with name, start/end angle,
  ARGB color (with alpha), and an enable toggle. Ranges draw as translucent bands and
  wrap correctly across the 0° seam (e.g. 330° → 30°).
- **Position & size** — 9-position anchor, X/Y offsets, explicit width/height, and
  background opacity per tape.
- **Mouse-driven HUD editor** — an in-game editor (like moving desktop windows): drag a
  tape to move it, grab its edge/corner handles to resize it freely, and cycle its anchor.
  Live preview; changes persist to `config/anglewatcher.json` on close.
  Open it via the **HUD Editor keybind** (unbound by default, under *Angle Watcher* in the
  Controls screen) or the **Open HUD Editor** button in the Cloth Config screen.
- **YACL config screen** — accessible from **Mod Menu → Angle Watcher → Configure**.
  Changes apply live and persist to `config/anglewatcher.json`.
- **Per-range camera-limit keybinds** — 20 keybinds in the *Angle Watcher* category
  (*Yaw Range 1–10 Camera Limit Toggle*, *Pitch Range 1–10 Camera Limit Toggle*), each
  flipping the camera-limit flag of the range at that position in the corresponding
  ranges list (the order shown in the config screen). Turning a range on also enables
  its tape's camera-limit switch; turning the last one off disables the master switch.
  All keybinds are unbound by default — assign them in **Options → Controls → Key Binds**.

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | 26.2.x |
| Fabric Loader | ≥ 0.19.5 |
| Fabric API | 0.160.0+26.2 (any 26.2.x build) |
| YACL (YetAnotherConfigLib) | 3.9.6+26.2 (optional, needed for the config screen) |
| Mod Menu | 20.0.2 (optional, needed for the config screen entry) |
| Java | 25+ |

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

The jar is produced at `build/libs/anglewatcher-1.0.0.jar`. Java 25 is required to build.

## Usage

1. Drop the jar in your `mods/` folder together with Fabric API, YACL and Mod Menu.
2. In-game, open **Mods → Angle Watcher → Configure**.
3. **Heading Tape (Yaw)** and **Pitch Tape (Vertical)** categories control position and size.
4. The two ranges categories each have an **+ Add Angle Range** button (up to 10 per tape); remove ranges with the button inside each range's sub-category. Numeric options can be switched between sliders and plain number boxes with **Use Sliders** in General.

## License

MIT — see [LICENSE](LICENSE).
