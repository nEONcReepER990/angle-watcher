package dev.anglewatcher.screen;

import dev.anglewatcher.ModIcons;
import dev.anglewatcher.config.AngleWatcherConfig;
import dev.anglewatcher.config.AngleWatcherConfig.Anchor;
import dev.anglewatcher.config.AngleWatcherConfig.StripConfig;
import dev.anglewatcher.hud.TapeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Window-like HUD editor: drag a tape to move it, drag its edges/corners to resize it,
 * cycle its anchor, all with a live preview. Changes commit to the live config when
 * switching tapes, resetting, or closing (ESC/Done).
 */
public final class HudEditScreen extends Screen {
	private static final int HANDLE_HIT = 7;
	/** Tapes may be resized down to a single pixel. */
	private static final int MIN_W = 1;
	private static final int MIN_H = 1;
	private static final int SNAP = 2;
	/** Maximum remembered undo steps. */
	private static final int MAX_UNDO = 100;

	private static final int HANDLE_W = 0, HANDLE_E = 1, HANDLE_N = 2, HANDLE_S = 3;
	private static final int HANDLE_NW = 4, HANDLE_NE = 5, HANDLE_SW = 6, HANDLE_SE = 7;

	private final AngleWatcherConfig config;
	/** Screen to return to when Done is pressed (e.g. the config screen), or null for the game. */
	private final Screen parent;
	private final TapeRenderer renderer = new TapeRenderer(TapeRenderer.Orientation.HORIZONTAL);
	private final TapeRenderer pitchRenderer = new TapeRenderer(TapeRenderer.Orientation.VERTICAL);

	/** 0 = heading tape, 1 = pitch tape. */
	private int selected;
	private StripConfig strip;

	// Live editor rectangle (GUI units), synced from the strip on select/init.
	private int editorX, editorY, editorW, editorH;

	private static final int MODE_NONE = 0, MODE_MOVE = 1, MODE_RESIZE = 2;
	private int mode = MODE_NONE;
	private int activeHandle = -1;
	private int hoveredHandle = -1;
	private int grabX, grabY;
	private int startX, startY, startW, startH;

	private Button anchorButton;
	private Button undoButton;
	private Button redoButton;

	/** One undoable editor state: a full copy of the tape config plus its on-screen rectangle. */
	private record Snapshot(StripConfig strip, int x, int y, int w, int h) {
	}

	private final Deque<Snapshot> undoStack = new ArrayDeque<>();
	private final Deque<Snapshot> redoStack = new ArrayDeque<>();

	/**
	 * Pin mode: while active, clicking the selected tape adds an angle pin at the
	 * clicked position, and Shift+clicking a pin removes it. Toggled with R.
	 */
	private boolean pinMode = false;
	/** Geometry before the current resize drag started, so snaps stay anchored to the original position. */
	private int snapBaseX, snapBaseY;
	/** Pending sub-pixel arrow-key nudge (accumulated, applied every 2 px). */
	private float nudgeAccumX, nudgeAccumY;

	public HudEditScreen(AngleWatcherConfig config) {
		this(config, null);
	}

	public HudEditScreen(AngleWatcherConfig config, Screen parent) {
		super(Component.translatable("title.anglewatcher.editor"));
		this.config = config;
		this.parent = parent;
		this.strip = config.headingTape;
	}

	@Override
	protected void init() {		addRenderableWidget(Button.builder(Component.translatable("option.anglewatcher.editor.tab_heading"),
					b -> select(0)).bounds(this.width - 156, 6, 74, 20).build());		addRenderableWidget(Button.builder(Component.translatable("option.anglewatcher.editor.tab_pitch"),
					b -> select(1)).bounds(this.width - 78, 6, 74, 20).build());		anchorButton = addRenderableWidget(Button.builder(Component.empty(), b -> cycleAnchor())
				.bounds(8, this.height - 26, 140, 20).build());
		undoButton = addRenderableWidget(Button.builder(Component.translatable("option.anglewatcher.editor.undo"),
				b -> undo()).bounds(152, this.height - 26, 60, 20).build());
		redoButton = addRenderableWidget(Button.builder(Component.translatable("option.anglewatcher.editor.redo"),
				b -> redo()).bounds(216, this.height - 26, 60, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("option.anglewatcher.editor.reset"),
						b -> resetSelected()).bounds(this.width - 138, this.height - 26, 64, 20).build());		addRenderableWidget(Button.builder(Component.translatable("option.anglewatcher.editor.done"),
						b -> onClose()).bounds(this.width - 70, this.height - 26, 62, 20).build());

		syncFromStrip();
	}

	private void select(int index) {
		if (index == selected) {
			return;
		}
		commitToStrip();
		// Undo history is per-tape; a snapshot taken for one tape must never be
		// restored onto the other.
		undoStack.clear();
		redoStack.clear();
		selected = index;
		strip = index == 0 ? config.headingTape : config.pitchTape;
		syncFromStrip();
	}

	/** Remembers the current editor state so it can be restored by undo. */
	private void pushUndo() {
		undoStack.push(snapshot());
	}

	private Snapshot snapshot() {
		if (undoStack.size() >= MAX_UNDO) {
			undoStack.removeLast();
		}
		return new Snapshot(copyOf(strip), editorX, editorY, editorW, editorH);
	}

	/** Restores the most recent editor state (Ctrl+Z / Undo button). */
	private void undo() {
		if (undoStack.isEmpty()) {
			return;
		}
		Snapshot state = undoStack.pop();
		// The state being replaced becomes the redo state.
		redoStack.push(new Snapshot(copyOf(strip), editorX, editorY, editorW, editorH));
		restoreFrom(state);
	}

	/** Re-applies the most recently undone change (Ctrl+Y / Redo button). */
	private void redo() {
		if (redoStack.isEmpty()) {
			return;
		}
		Snapshot state = redoStack.pop();
		undoStack.push(new Snapshot(copyOf(strip), editorX, editorY, editorW, editorH));
		restoreFrom(state);
	}

	private void restoreFrom(Snapshot state) {
		copyInto(state.strip(), strip);
		editorX = state.x();
		editorY = state.y();
		editorW = state.w();
		editorH = state.h();
		commitToStrip();
	}

	private void resetSelected() {
		redoStack.clear();
		pushUndo();
		StripConfig def = selected == 0 ? StripConfig.headingDefaults() : StripConfig.pitchDefaults();
		strip.enabled = def.enabled;
		strip.anchor = def.anchor;
		strip.offsetX = def.offsetX;
		strip.offsetY = def.offsetY;
		strip.width = def.width;
		strip.height = def.height;
		strip.opacity = def.opacity;
		strip.showTicks = def.showTicks;
		strip.showLabels = def.showLabels;
		strip.showCardinals = def.showCardinals;
		strip.showReadout = def.showReadout;
		syncFromStrip();
	}

	private void cycleAnchor() {
		redoStack.clear();
		pushUndo();
		commitToStrip();
		Anchor[] values = Anchor.values();
		strip.anchor = values[(strip.anchor.ordinal() + 1) % values.length];
		syncFromStrip();
	}

	/** Loads the selected strip's current geometry into the editor rectangle. */
	private void syncFromStrip() {
		int[] box = (selected == 0 ? renderer : pitchRenderer).layout(strip, this.width, this.height);
		editorX = box[0];
		editorY = box[1];
		editorW = box[2] - box[0];
		editorH = box[3] - box[1];
	}

	/** Writes the editor rectangle back into the selected strip as anchor + offset. */
	private void commitToStrip() {
		if (strip == null) {
			return;
		}
		int[] base = anchoredBase(strip.anchor, editorW, editorH, this.width, this.height);
		strip.offsetX = snap(editorX - base[0]);
		strip.offsetY = snap(editorY - base[1]);
		strip.width = editorW;
		strip.height = editorH;
		config.save();
	}

	@Override
	public void onClose() {
		commitToStrip();
		if (parent != null) {
			Minecraft.getInstance().gui.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static int snap(int value) {
		return Math.round(value / (float) SNAP) * SNAP;
	}

	/** Anchor position for a rectangle of the given size (same rules as the renderer). */
	public static int[] anchoredBase(Anchor anchor, int width, int height, int screenWidth, int screenHeight) {
		return switch (anchor) {
			case TOP_LEFT -> new int[]{0, 0};
			case TOP_CENTER -> new int[]{(screenWidth - width) / 2, 0};
			case TOP_RIGHT -> new int[]{screenWidth - width, 0};
			case CENTER_LEFT -> new int[]{0, (screenHeight - height) / 2};
			case CENTER -> new int[]{(screenWidth - width) / 2, (screenHeight - height) / 2};
			case CENTER_RIGHT -> new int[]{screenWidth - width, (screenHeight - height) / 2};
			case BOTTOM_LEFT -> new int[]{0, screenHeight - height};
			case BOTTOM_CENTER -> new int[]{(screenWidth - width) / 2, screenHeight - height};
			case BOTTOM_RIGHT -> new int[]{screenWidth - width, screenHeight - height};
		};
	}

	// ---- Input --------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
		int mx = (int) event.x();
		int my = (int) event.y();
		// A mouse action starts a fresh undo context; drop any pending keyboard nudge.
		nudgeAccumX = 0;
		nudgeAccumY = 0;

		if (super.mouseClicked(event, doubled)) {
			return true;
		}

		int handle = handleAt(mx, my);
		if (handle != -1) {
			redoStack.clear();
			pushUndo();
			mode = MODE_RESIZE;
			activeHandle = handle;
			startX = editorX;
			startY = editorY;
			startW = editorW;
			startH = editorH;
			snapBaseX = editorX;
			snapBaseY = editorY;
			grabX = mx;
			grabY = my;
			return true;
		}

		if (mx >= editorX && mx <= editorX + editorW && my >= editorY && my <= editorY + editorH) {
			if (pinMode) {
				// Shift+click removes the pin under the cursor; plain click adds one.
				int hit = pinAt(mx, my);
				if (event.hasShiftDown() && hit != -1) {
					removePin(hit);
					return true;
				}
				if (!event.hasShiftDown()) {
					TapeRenderer.Orientation orient = selected == 0
							? TapeRenderer.Orientation.HORIZONTAL : TapeRenderer.Orientation.VERTICAL;
					int center = orient == TapeRenderer.Orientation.HORIZONTAL ? editorX + editorW / 2 : editorY + editorH / 2;
					int length = orient == TapeRenderer.Orientation.HORIZONTAL ? editorW : editorH;
					int pos = orient == TapeRenderer.Orientation.HORIZONTAL ? mx : my;
					float angle = selected == 0 ? currentHeading() : currentPitch();
					double pinAngle = (pos - center) / (length / (double) TapeRenderer.WINDOW_DEGREES) + angle;
					addPinAt(pinAngle);
					return true;
				}
			}
			redoStack.clear();
			pushUndo();
			mode = MODE_MOVE;
			grabX = mx - editorX;
			grabY = my - editorY;
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		int mx = (int) event.x();
		int my = (int) event.y();

		if (mode == MODE_MOVE) {
			editorX = Mth_clamp(snap(mx - grabX), 0, Math.max(0, this.width - editorW));
			editorY = Mth_clamp(snap(my - grabY), 0, Math.max(0, this.height - editorH));
			return true;
		}
		if (mode == MODE_RESIZE) {
			int dx = mx - grabX;
			int dy = my - grabY;
			applyResize(dx, dy);
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	private void applyResize(int dx, int dy) {
		// Snapping is computed against where the resize started so the anchor edge
		// does not drift away from the grid while dragging.
		int dxs = snap(dx + snapBaseX) - snapBaseX;
		int dys = snap(dy + snapBaseY) - snapBaseY;
		int x1 = startX;
		int y1 = startY;
		int x2 = startX + startW;
		int y2 = startY + startH;
		int h = activeHandle;

		boolean west = h == HANDLE_W || h == HANDLE_NW || h == HANDLE_SW;
		boolean east = h == HANDLE_E || h == HANDLE_NE || h == HANDLE_SE;
		boolean north = h == HANDLE_N || h == HANDLE_NW || h == HANDLE_NE;
		boolean south = h == HANDLE_S || h == HANDLE_SW || h == HANDLE_SE;

		if (east) {
			x2 = Mth_clamp(snap(x2 + dxs), x1 + MIN_W, this.width);
		} else if (west) {
			x1 = Mth_clamp(snap(x1 + dxs), 0, x2 - MIN_W);
		}
		if (south) {
			y2 = Mth_clamp(snap(y2 + dys), y1 + MIN_H, this.height);
		} else if (north) {
			y1 = Mth_clamp(snap(y1 + dys), 0, y2 - MIN_H);
		}

		editorX = x1;
		editorY = y1;
		editorW = x2 - x1;
		editorH = y2 - y1;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_Z) {
			undo();
			return true;
		}
		if (event.hasControlDown() && (event.key() == GLFW.GLFW_KEY_Y
				|| (event.hasShiftDown() && event.key() == GLFW.GLFW_KEY_Z))) {
			redo();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_R) {
			pinMode = !pinMode;
			return true;
		}
		// Arrow keys nudge the tape (Shift = 10 px steps).
		if (event.isLeft() || event.isRight() || event.isUp() || event.isDown()) {
			if (nudgeAccumX == 0 && nudgeAccumY == 0) {
				redoStack.clear();
				pushUndo();
			}
			int step = event.hasShiftDown() ? 10 : 2;
			if (event.isLeft()) nudgeAccumX -= step;
			if (event.isRight()) nudgeAccumX += step;
			if (event.isUp()) nudgeAccumY -= step;
			if (event.isDown()) nudgeAccumY += step;
			int appliedX = Math.round(nudgeAccumX / 2) * 2;
			int appliedY = Math.round(nudgeAccumY / 2) * 2;
			if (appliedX != 0) {
				editorX = Mth_clamp(editorX + appliedX, 0, Math.max(0, this.width - editorW));
				nudgeAccumX -= appliedX;
			}
			if (appliedY != 0) {
				editorY = Mth_clamp(editorY + appliedY, 0, Math.max(0, this.height - editorH));
				nudgeAccumY -= appliedY;
			}
			commitToStrip();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (mode != MODE_NONE) {
			mode = MODE_NONE;
			activeHandle = -1;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		super.mouseMoved(mouseX, mouseY);
		hoveredHandle = handleAt((int) mouseX, (int) mouseY);
	}

	private int handleAt(int mx, int my) {
		int cx = editorX + editorW / 2;
		int cy = editorY + editorH / 2;
		if (near(mx, my, editorX, editorY)) return HANDLE_NW;
		if (near(mx, my, editorX + editorW, editorY)) return HANDLE_NE;
		if (near(mx, my, editorX, editorY + editorH)) return HANDLE_SW;
		if (near(mx, my, editorX + editorW, editorY + editorH)) return HANDLE_SE;
		if (near(mx, my, cx, editorY)) return HANDLE_N;
		if (near(mx, my, cx, editorY + editorH)) return HANDLE_S;
		if (near(mx, my, editorX, cy)) return HANDLE_W;
		if (near(mx, my, editorX + editorW, cy)) return HANDLE_E;
		return -1;
	}

	/** Distance test against the corner/edge point with HANDLE_HIT radius. */
	private static boolean near(int mx, int my, int px, int py) {
		return Math.abs(mx - px) <= HANDLE_HIT && Math.abs(my - py) <= HANDLE_HIT;
	}

	// ---- Rendering ----------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		// Dim backdrop + faint grid.
		graphics.fill(0, 0, this.width, this.height, 0x70000000);
		int grid = 0x18FFFFFF;
		for (int gx = 0; gx < this.width; gx += 32) {
			graphics.fill(gx, 0, gx + 1, this.height, grid);
		}
		for (int gy = 0; gy < this.height; gy += 32) {
			graphics.fill(0, gy, this.width, gy + 1, grid);
		}

		// Live preview of the selected tape at the editor rectangle.
		StripConfig preview = copyOf(strip);
		int[] base = anchoredBase(preview.anchor, editorW, editorH, this.width, this.height);
		preview.offsetX = editorX - base[0];
		preview.offsetY = editorY - base[1];
		preview.width = editorW;
		preview.height = editorH;
		if (selected == 0) {
			renderer.render(graphics, this.font, preview, currentHeading(), config.headingRanges, this.width, this.height);
		} else {
			pitchRenderer.render(graphics, this.font, preview, currentPitch(), config.pitchRanges, this.width, this.height);
		}

		drawHandles(graphics);

		// Texts (drawn before widgets so buttons stay on top).
		ModIcons.drawGuiIcon(graphics, 8, 6);
		graphics.text(this.font, Component.translatable("title.anglewatcher.editor.editing",
						Component.translatable(selected == 0 ? "category.anglewatcher.heading_tape" : "category.anglewatcher.pitch_tape")),
				28, 11, 0xFFFFFFFF, true);
		graphics.text(this.font, Component.translatable("option.anglewatcher.editor.instructions"),
				28, 25, 0xFFB0B0B0, false);

		String sizeText = String.format(Locale.ROOT, "%d × %d px", editorW, editorH);
		graphics.text(this.font, sizeText, 8, this.height - 34, 0xFFFFFF80, true);

		String status = switch (mode) {
			case MODE_MOVE -> Component.translatable("option.anglewatcher.editor.status_moving").getString();
			case MODE_RESIZE -> Component.translatable("option.anglewatcher.editor.status_resizing").getString();
			default -> hoveredHandle != -1
					? Component.translatable("option.anglewatcher.editor.status_handle").getString()
					: Component.translatable("option.anglewatcher.editor.status_idle").getString();
		};
		if (pinMode && mode == MODE_NONE) {
			status = Component.translatable("option.anglewatcher.editor.status_pin").getString();
			// Highlight the pin under the cursor.
			drawPinHighlight(graphics, (int) mouseX, (int) mouseY);
		}
		graphics.text(this.font, status, 8, this.height - 46, 0xFFE0E0E0, false);

		anchorButton.setMessage(Component.translatable("option.anglewatcher.editor.anchor",
				Component.literal(prettyAnchor(strip.anchor))));
		undoButton.active = !undoStack.isEmpty();
		redoButton.active = !redoStack.isEmpty();

		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private void drawHandles(GuiGraphicsExtractor graphics) {
		int cx = editorX + editorW / 2;
		int cy = editorY + editorH / 2;
		int[][] points = {
				{editorX, editorY}, {editorX + editorW, editorY},
				{editorX, editorY + editorH}, {editorX + editorW, editorY + editorH},
				{cx, editorY}, {cx, editorY + editorH},
				{editorX, cy}, {editorX + editorW, cy}
		};

		// Selection outline
		int outline = mode == MODE_NONE ? 0xFF50C8FF : 0xFFFFD050;
		graphics.outline(editorX - 1, editorY - 1, editorW + 2, editorH + 2, outline);

		for (int i = 0; i < points.length; i++) {
			int[] p = points[i];
			boolean hot = i == activeHandle || (mode == MODE_NONE && i == hoveredHandle);
			int color = hot ? 0xFFFFD050 : 0xFF50C8FF;
			graphics.fill(p[0] - 3, p[1] - 3, p[0] + 4, p[1] + 4, 0xFF101010);
			graphics.outline(p[0] - 3, p[1] - 3, 7, 7, color);
		}
	}

	/** @return the clicked pin's index, or -1 if none is hit (selection-space hit test). */
	private int pinAt(int mx, int my) {
		if (strip.pins == null) {
			return -1;
		}
		float angle = selected == 0 ? currentHeading() : currentPitch();
		for (int i = 0; i < strip.pins.size(); i++) {
			dev.anglewatcher.config.AngleWatcherConfig.PinConfig pin = strip.pins.get(i);
			float delta = (float) pin.angle - angle;
			while (delta > 180.0f) delta -= 360.0f;
			while (delta <= -180.0f) delta += 360.0f;
			int length = selected == 0 ? editorW : editorH;
			int center = selected == 0 ? editorX + editorW / 2 : editorY + editorH / 2;
			int pos = Math.round(center + delta * (length / (float) TapeRenderer.WINDOW_DEGREES));
			int px = selected == 0 ? pos : editorX + 4;
			int py = selected == 0 ? editorY + 4 : pos;
			if (Math.abs(mx - px) <= 6 && Math.abs(my - py) <= 6) {
				return i;
			}
		}
		return -1;
	}

	/** Adds a pin at the given angle in the selected tape's convention, clamped to its sane domain. */
	private void addPinAt(double angle) {
		if (strip.pins == null) {
			strip.pins = new java.util.ArrayList<>();
		}
		if (strip.pins.size() >= AngleWatcherConfig.MAX_PINS_PER_TAPE) {
			return;
		}
		redoStack.clear();
		pushUndo();
		double clamped = selected == 0
				? Math.floorMod((long) Math.round(angle), 360L)
				: Math.max(-90, Math.min(90, Math.round(angle)));
		strip.pins.add(dev.anglewatcher.config.AngleWatcherConfig.PinConfig.of(
				Component.translatable("option.anglewatcher.editor.pin_name").getString(), clamped,
				selected == 0 ? 0xFFFFD050 : 0xFF50C8FF));
		config.save();
	}

	/** Shift+click on a pin removes it. */
	private void removePin(int index) {
		if (strip.pins == null || index < 0 || index >= strip.pins.size()) {
			return;
		}
		redoStack.clear();
		pushUndo();
		strip.pins.remove(index);
		config.save();
	}

	private float currentHeading() {
		return TapeRenderer.headingFromYaw(
				this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getYRot() : 0.0f);
	}

	private float currentPitch() {
		return this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getXRot() : 0.0f;
	}

	/** Draws a small crosshair over the pin currently under the cursor in pin mode. */
	private void drawPinHighlight(GuiGraphicsExtractor graphics, int mx, int my) {
		int index = pinAt(mx, my);
		if (index == -1) {
			return;
		}
		dev.anglewatcher.config.AngleWatcherConfig.PinConfig pin = strip.pins.get(index);
		float angle = selected == 0 ? currentHeading() : currentPitch();
		float delta = (float) pin.angle - angle;
		while (delta > 180.0f) delta -= 360.0f;
		while (delta <= -180.0f) delta += 360.0f;
		int length = selected == 0 ? editorW : editorH;
		int center = selected == 0 ? editorX + editorW / 2 : editorY + editorH / 2;
		int pos = Math.round(center + delta * (length / (float) TapeRenderer.WINDOW_DEGREES));
		int px = selected == 0 ? pos : editorX + 4;
		int py = selected == 0 ? editorY + 4 : pos;
		graphics.outline(px - 5, py - 5, 11, 11, 0xFFFF5555);
	}

	private static StripConfig copyOf(StripConfig s) {
		StripConfig c = new StripConfig();
		copyInto(s, c);
		return c;
	}

	/** Copies every user-facing field of {@code from} into {@code to}. */
	private static void copyInto(StripConfig from, StripConfig to) {
		to.enabled = from.enabled;
		to.anchor = from.anchor;
		to.offsetX = from.offsetX;
		to.offsetY = from.offsetY;
		to.width = from.width;
		to.height = from.height;
		to.opacity = from.opacity;
		to.showTicks = from.showTicks;
		to.showLabels = from.showLabels;
		to.showCardinals = from.showCardinals;
		to.showReadout = from.showReadout;
		to.centerMarkerWidth = from.centerMarkerWidth;
		to.centerMarkerColor = from.centerMarkerColor;
		// Share pin elements (they are never mutated in place) so undo/preview see the same list.
		to.pins = new java.util.ArrayList<>(from.pins == null ? java.util.List.of() : from.pins);
	}

	private static String prettyAnchor(Anchor anchor) {
		String lower = anchor.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static int Mth_clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
