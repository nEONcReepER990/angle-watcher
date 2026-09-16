package dev.anglewatcher.hud;

import dev.anglewatcher.config.AngleWatcherConfig.PinConfig;
import dev.anglewatcher.config.AngleWatcherConfig.RangeConfig;
import dev.anglewatcher.config.AngleWatcherConfig.ReadoutFormat;
import dev.anglewatcher.config.AngleWatcherConfig.StripConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * Renders a scrolling angle tape (horizontal for heading/yaw, vertical for pitch).
 * All drawing is plain {@link GuiGraphicsExtractor} shapes and text; no textures, no mixins.
 *
 * <p>Heading convention: 0&deg; = North (yaw 180), increasing clockwise through East.
 * Pitch convention: -90&deg; = looking straight up, +90&deg; = straight down (vanilla pitch).
 *
 * <p>The tape has an explicit, user-resizable pixel rectangle (see
 * {@link #layout(StripConfig, int, int)}). The 22px base design height scales text and
 * tick metrics; the visible window shrinks below the base size so any size is legal.
 */
public final class TapeRenderer {
	/** Degrees of angle visible across the tape at the base design size. */
	public static final float WINDOW_DEGREES = 120.0f;
	private static final float HALF_WINDOW = WINDOW_DEGREES / 2.0f;
	/** Base thickness the internal metrics were designed for. */
	public static final int BASE_THICKNESS = 22;

	private final Orientation orientation;

	public TapeRenderer(Orientation orientation) {
		this.orientation = orientation;
	}

	public enum Orientation {
		HORIZONTAL, VERTICAL
	}

	/**
	 * @return true if {@code angle} lies inside the circular interval [start, end]
	 * (going from start to end in increasing-angle direction, wrapping through ±180°).
	 */
	public static boolean inRange(double angle, double start, double end) {
		double span = end - start;
		if (span == 0) return false;
		while (span < 0) span += 360.0;
		while (span >= 360.0) span -= 360.0;
		if (span == 0) return true;
		double delta = angle - start;
		while (delta < 0) delta += 360.0;
		while (delta >= 360.0) delta -= 360.0;
		return delta <= span;
	}

	/** @return the compass heading (0..360, 0 = North) for a vanilla yaw. */
	public static float headingFromYaw(float yaw) {
		return Mth.wrapDegrees(yaw + 180.0f) + 180.0f;
	}

	/**
	 * Computes the on-screen rectangle for a tape.
	 *
	 * @return {x1, y1, x2, y2} in real screen pixels
	 */
	public int[] layout(StripConfig strip, int screenWidth, int screenHeight) {
		int width = Mth.clamp(strip.width, 1, screenWidth);
		int height = Mth.clamp(strip.height, 1, screenHeight);

		int anchorX = 0;
		int anchorY = 0;
		switch (strip.anchor) {
			case TOP_LEFT -> { anchorX = 0; anchorY = 0; }
			case TOP_CENTER -> { anchorX = (screenWidth - width) / 2; anchorY = 0; }
			case TOP_RIGHT -> { anchorX = screenWidth - width; anchorY = 0; }
			case CENTER_LEFT -> { anchorX = 0; anchorY = (screenHeight - height) / 2; }
			case CENTER -> { anchorX = (screenWidth - width) / 2; anchorY = (screenHeight - height) / 2; }
			case CENTER_RIGHT -> { anchorX = screenWidth - width; anchorY = (screenHeight - height) / 2; }
			case BOTTOM_LEFT -> { anchorX = 0; anchorY = screenHeight - height; }
			case BOTTOM_CENTER -> { anchorX = (screenWidth - width) / 2; anchorY = screenHeight - height; }
			case BOTTOM_RIGHT -> { anchorX = screenWidth - width; anchorY = screenHeight - height; }
		}

		int x1 = Mth.clamp(anchorX + strip.offsetX, 0, Math.max(0, screenWidth - width));
		int y1 = Mth.clamp(anchorY + strip.offsetY, 0, Math.max(0, screenHeight - height));
		return new int[]{x1, y1, x1 + width, y1 + height};
	}

	public void render(GuiGraphicsExtractor graphics, Font font, StripConfig strip,
			float centerAngle, List<RangeConfig> ranges, int screenWidth, int screenHeight) {
		if (strip == null || !strip.enabled || strip.width < 1 || strip.height < 1) {
			return;
		}

		int[] box = layout(strip, screenWidth, screenHeight);
		int x1 = box[0];
		int y1 = box[1];
		int x2 = box[2];
		int y2 = box[3];
		int length = (orientation == Orientation.HORIZONTAL ? x2 - x1 : y2 - y1);

		// Text/tick metric scale relative to the base design height. Clamp so very thin
		// tapes still show content (the angle window shrinks instead).
		float s = Math.min(strip.height / (float) BASE_THICKNESS, 2.0f);

		int bgAlpha = Math.round((strip.opacity / 100.0f) * 255.0f);
		int background = (bgAlpha << 24) | 0x101010;

		// Backdrop
		graphics.fill(x1, y1, x2, y2, background);
		graphics.outline(x1, y1, x2 - x1, y2 - y1, 0x90FFFFFF);

		// Everything drawn inside the tape is clipped to it, so tiny tapes stay clean.
		graphics.enableScissor(x1, y1, x2, y2);
		for (RangeConfig range : ranges) {
			if (range == null || !range.enabled) {
				continue;
			}
			drawRange(graphics, range, strip, x1, y1, x2, y2, length, centerAngle);
		}

		if (strip.showPins) {
			drawPins(graphics, font, strip, x1, y1, x2, y2, length, centerAngle, s);
		}

		if (strip.showTicks) {
			drawTicks(graphics, font, strip, x1, y1, x2, y2, length, centerAngle, s);
		}

		// Alert flash: bright border while the current angle is inside an alert range.
		if (alertActive(strip, ranges, centerAngle)) {
			graphics.outline(x1, y1, x2 - x1, y2 - y1, 0xFFFFFFFF);
		}

		// Center marker ("you are here" line), width and color user-configurable.
		int mw = Math.max(1, strip.centerMarkerWidth);
		if (orientation == Orientation.HORIZONTAL) {
			int cx = x1 + length / 2 - mw / 2;
			graphics.fill(cx, y1 + 1, cx + mw, y2 - 1, strip.centerMarkerColor);
		} else {
			int cy = y1 + length / 2 - mw / 2;
			graphics.fill(x1 + 1, cy, x2 - 1, cy + mw, strip.centerMarkerColor);
		}

		if (strip.showReadout) {
			drawReadout(graphics, font, strip, x1, y1, x2, y2, centerAngle, s);
		}
		graphics.disableScissor();

		// Softly fade the tape into screen edges when anchored flush (like vanilla HUD bars).
		int edgeFade = Math.round(12 * s);
		if (orientation == Orientation.HORIZONTAL) {
			if (x1 <= 0) {
				graphics.fillGradient(x1, y1 + 1, x1 + edgeFade, y2 - 1, background, 0);
			}
			if (x2 >= screenWidth) {
				graphics.fillGradient(x2 - edgeFade, y1 + 1, x2, y2 - 1, 0, background);
			}
		} else {
			if (y1 <= 0) {
				graphics.fillGradient(x1 + 1, y1, x2 - 1, y1 + edgeFade, background, 0);
			}
			if (y2 >= screenHeight) {
				graphics.fillGradient(x1 + 1, y2 - edgeFade, x2 - 1, y2, 0, background);
			}
		}
	}

	/**
	 * Draws one range as translucent band(s), handling wrap-around ranges and out-of-window
	 * positions by intersecting the band's angular interval(s) with the visible window.
	 */
	private void drawRange(GuiGraphicsExtractor graphics, RangeConfig range, StripConfig strip,
			int x1, int y1, int x2, int y2, int length, float centerAngle) {
		float span = (float) (range.end - range.start);
		if (span == 0) {
			return;
		}
		// Normalize span into (0, 360].
		while (span < 0) span += 360.0f;
		while (span > 360.0f) span -= 360.0f;

		float window = visibleWindow(strip);
		float half = window / 2.0f;

		if (span >= 360.0f) {
			if (orientation == Orientation.HORIZONTAL) {
				graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, range.color);
			} else {
				graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, range.color);
			}
			return;
		}

		float startOffset = angleDelta((float) range.start, centerAngle);

		// The band covers the circle interval [startOffset, startOffset + span].
		// The visible window is [-half, +half]. A band may also peek in through the
		// wrap seam at ±180°, so test the interval shifted by -360° too.
		intersectAndFill(graphics, strip, x1, y1, x2, y2, length, startOffset, span, range.color, half);
		intersectAndFill(graphics, strip, x1, y1, x2, y2, length, startOffset - 360.0f, span, range.color, half);
	}

	/** Clips the interval [startOffset, startOffset+span] to the visible window and fills the result. */
	private void intersectAndFill(GuiGraphicsExtractor graphics, StripConfig strip,
			int x1, int y1, int x2, int y2, int length, float startOffset, float span, int color, float half) {
		float lo = Math.max(startOffset, -half);
		float hi = Math.min(startOffset + span, half);
		if (hi <= lo) {
			return;
		}
		fillBand(graphics, strip, x1, y1, x2, y2, length, lo, hi - lo, color);
	}

	/** Shortest signed distance from {@code centerAngle} to {@code angle}, in (-180, 180]. */
	private static float angleDelta(float angle, float centerAngle) {
		float delta = angle - centerAngle;
		while (delta > 180.0f) delta -= 360.0f;
		while (delta <= -180.0f) delta += 360.0f;
		return delta;
	}

	/** Fills a band from {@code startDeg} spanning {@code spanDeg} relative to the tape center. */
	private void fillBand(GuiGraphicsExtractor graphics, StripConfig strip,
			int x1, int y1, int x2, int y2, int length, float startDeg, float spanDeg, int color) {
		float pxPerDeg = pixelsPerDegree(strip, length);
		if (orientation == Orientation.HORIZONTAL) {
			int centerX = x1 + length / 2;
			int bx1 = Math.round(centerX + startDeg * pxPerDeg);
			int bx2 = Math.round(centerX + (startDeg + spanDeg) * pxPerDeg);
			if (bx2 <= bx1) return;
			graphics.fill(bx1, y1 + 1, bx2, y2 - 1, color);
		} else {
			int centerY = y1 + length / 2;
			int by1 = Math.round(centerY + startDeg * pxPerDeg);
			int by2 = Math.round(centerY + (startDeg + spanDeg) * pxPerDeg);
			if (by2 <= by1) return;
			graphics.fill(x1 + 1, by1, x2 - 1, by2, color);
		}
	}

	/** @return true if {@code centerAngle} is inside any enabled range of this tape. */
	public static boolean inAnyRange(List<RangeConfig> ranges, double centerAngle) {
		for (RangeConfig range : ranges) {
			if (range != null && range.enabled && inRange(centerAngle, range.start, range.end)) {
				return true;
			}
		}
		return false;
	}

	/** Alert is active when a sound is selected and the angle sits inside an enabled range. */
	private static boolean alertActive(StripConfig strip, List<RangeConfig> ranges, float centerAngle) {
		return strip.alertSound != dev.anglewatcher.config.AngleWatcherConfig.AlertSound.OFF
				&& inAnyRange(ranges, centerAngle);
	}

	/** Draws the user's pinned angles as thin marker lines with a letter flag. */
	private void drawPins(GuiGraphicsExtractor graphics, Font font, StripConfig strip,
			int x1, int y1, int x2, int y2, int length, float centerAngle, float s) {
		if (strip.pins == null || strip.pins.isEmpty()) {
			return;
		}
		float pxPerDeg = pixelsPerDegree(strip, length);
		float window = visibleWindow(strip);
		int centerY = y1 + length / 2;
		int centerX = x1 + length / 2;
		int pinSize = Math.round(7 * s);

		for (PinConfig pin : strip.pins) {
			if (pin == null) {
				continue;
			}
			float delta = angleDelta((float) pin.angle, centerAngle);
			if (Math.abs(delta) > window / 2.0f + 2.0f) {
				continue;
			}
			int offset = Math.round(pxPerDeg * delta);
			String label = pin.name == null || pin.name.isEmpty()
					? String.format(Locale.ROOT, "%.0f", pin.angle)
					: pin.name;

			if (orientation == Orientation.HORIZONTAL) {
				int x = centerX + offset;
				graphics.fill(x, y1 + 1, x + 1, y1 + 1 + pinSize, pin.color);
				int tx = x - Math.round(font.width(label) * s / 2.0f);
				drawScaledText(graphics, font, label, tx, y1 + 1 + pinSize + 1, pin.color, Math.min(s, 1.0f));
			} else {
				int y = centerY + offset;
				graphics.fill(x1 + 1, y, x1 + 1 + pinSize, y + 1, pin.color);
				int ty = y - Math.round(5 * Math.min(s, 1.0f));
				drawScaledText(graphics, font, label, x1 + 1 + pinSize + 1, ty, pin.color, Math.min(s, 1.0f));
			}
		}
	}

	private float visibleWindow(StripConfig strip) {
		// Below the base design height, shrink the window so content never overflows the tape.
		return WINDOW_DEGREES * Math.min(1.0f, strip.height / (float) BASE_THICKNESS);
	}

	private float pixelsPerDegree(StripConfig strip, int length) {
		return length / visibleWindow(strip);
	}

	private void drawTicks(GuiGraphicsExtractor graphics, Font font, StripConfig strip,
			int x1, int y1, int x2, int y2, int length, float centerAngle, float s) {
		float pxPerDeg = pixelsPerDegree(strip, length);
		float window = visibleWindow(strip);
		int centerX = x1 + length / 2;
		int centerY = y1 + length / 2;
		int majorSize = Math.round(6 * s);
		int minorSize = Math.round(3 * s);

		// Major ticks at the configured interval, minor at a third of it. Full 360°
		// loop for correct wrap handling (intervals need not divide 360 evenly).
		int major = Math.max(5, strip.tickInterval);
		int minor = Math.max(1, major / 3);
		for (int deg = 0; deg < 360; deg += minor) {
			float delta = angleDelta(deg, centerAngle);
			if (Math.abs(delta) > window / 2.0f + 2.0f) {
				continue;
			}
			boolean isMajor = deg % major == 0;
			int tickPx = Math.round(pxPerDeg * delta);
			int color = isMajor ? 0xFFF0F0F0 : 0xFF909090;
			int size = isMajor ? majorSize : minorSize;

			if (orientation == Orientation.HORIZONTAL) {
				int x = centerX + tickPx;
				graphics.fill(x, y2 - size, x + 1, y2, color);
			} else {
				int y = centerY + tickPx;
				graphics.fill(x2 - size, y, x2, y + 1, color);
			}

			if (isMajor && strip.showLabels) {
				String label = cardinalOrDegree(deg);
				if (label != null) {
					if (orientation == Orientation.HORIZONTAL) {
						int x = centerX + tickPx - Math.round(font.width(label) * s / 2.0f);
						drawScaledText(graphics, font, label, x, y1 + Math.round(2 * s), 0xFFE0E0E0, s);
					} else {
						int y = centerY + tickPx - Math.round(4 * s);
						drawScaledText(graphics, font, label, x1 + Math.round(2 * s), y, 0xFFE0E0E0, s);
					}
				}
			}
		}

		if (strip.showCardinals) {
			drawCardinalNeedle(graphics, strip, x1, y1, x2, y2, length, centerAngle, s);
		}
	}

	/**
	 * Marks where North (0°) currently sits on the tape — the classic compass needle —
	 * so the tape reads as a compass even when North is off-window.
	 */
	private void drawCardinalNeedle(GuiGraphicsExtractor graphics, StripConfig strip,
			int x1, int y1, int x2, int y2, int length, float centerAngle, float s) {
		float pxPerDeg = pixelsPerDegree(strip, length);
		float window = visibleWindow(strip);
		float delta = angleDelta(0, centerAngle);
		if (Math.abs(delta) > window / 2.0f + 2.0f) {
			return;
		}
		int offset = Math.round(pxPerDeg * delta);
		int needleHeight = Math.round(6 * s);
		if (orientation == Orientation.HORIZONTAL) {
			int x = x1 + length / 2 + offset;
			graphics.fill(x, y1 + 1, x + 1, y1 + 1 + needleHeight, 0xFFAA5555);
		} else {
			int y = y1 + length / 2 + offset;
			graphics.fill(x1 + 1, y, x1 + 1 + needleHeight, y + 1, 0xFFAA5555);
		}
	}

	/** Cardinal letter if this degree is one, otherwise null (labels every 15° major tick). */
	private String cardinalOrDegree(int deg) {
		return switch (deg) {
			case 0 -> "N";
			case 45 -> "NE";
			case 90 -> "E";
			case 135 -> "SE";
			case 180 -> "S";
			case 225 -> "SW";
			case 270 -> "W";
			case 315 -> "NW";
			default -> null;
		};
	}

	private void drawReadout(GuiGraphicsExtractor graphics, Font font, StripConfig strip,
			int x1, int y1, int x2, int y2, float centerAngle, float s) {
		String text;
		switch (strip.readoutFormat == null ? ReadoutFormat.SIMPLE : strip.readoutFormat) {
			case PRECISE -> text = String.format(Locale.ROOT, "%.1f°", centerAngle);
			case DECIMAL -> text = String.format(Locale.ROOT, "%03d°", Math.round(centerAngle) % 360);
			default -> text = String.format(Locale.ROOT, "%d°", Math.round(centerAngle));
		}
		int width = Math.round(font.width(text) * s);
		int x = x1 + ((x2 - x1) - width) / 2;
		// Top-center inside the tape.
		int y = y1 + 1 + Math.round(s > 1.0f ? 10 * s : 9 * s);
		drawScaledText(graphics, font, text, x, y, 0xFFFFFF80, s);
	}

	/** Draws text scaled by {@code scale} around the top-left point ({@code x}, {@code y}). */
	private void drawScaledText(GuiGraphicsExtractor graphics, Font font, String text,
			float x, float y, int argb, float scale) {
		if (scale == 1.0f) {
			graphics.text(font, text, (int) x, (int) y, argb, true);
			return;
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y);
		pose.scale(scale, scale);
		graphics.text(font, text, 0, 0, argb, true);
		pose.popMatrix();
	}
}
