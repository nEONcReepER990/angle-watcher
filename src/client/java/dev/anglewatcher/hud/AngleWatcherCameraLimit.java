package dev.anglewatcher.hud;

import dev.anglewatcher.config.AngleWatcherConfig;
import dev.anglewatcher.config.AngleWatcherConfig.RangeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Soft camera limit: while the player's view is inside a camera-limit-enabled range,
 * the view is clamped so it cannot leave the range. There is no forced snapping —
 * the camera moves freely until it touches a boundary, then holds there.
 *
 * <p>Runs once per frame right before the tapes draw (i.e. after the mouse-turn
 * handler applied its accumulated movement), so a boundary-crossing turn is pulled
 * back within the same frame. The whole feature has a master switch
 * ({@code cameraLimitEnabled}, toggleable via its keybind) and each tape opts in
 * individually.
 */
public final class AngleWatcherCameraLimit {
	/** Which tape's ranges a toggle keybind addresses. */
	public enum Tape {
		/** Heading (yaw) tape — its ranges are listed in vanilla yaw (−180 = North, 0 = South). */
		HEADING,
		/** Pitch tape — its ranges are listed in vanilla pitch (−90 = up, +90 = down). */
		PITCH
	}

	/** How far inside the boundary the view is held, in degrees. */
	private static final double CLAMP_EPSILON = 0.25;

	/** Last observed heading/pitch, to detect an exit between ticks and know which boundary to push back to. */
	private static float lastHeading;
	private static float lastPitch;
	private static boolean hadLast;

	private AngleWatcherCameraLimit() {
	}

	/** Flips the master switch (keybind). */
	public static void toggle() {
		AngleWatcherConfig config = AngleWatcherConfig.get();
		config.cameraLimitEnabled = !config.cameraLimitEnabled;
		config.save();
	}

	/** @return the camera-limit master switch of the given tape. */
	public static boolean tapeEnabled(AngleWatcherConfig config, Tape tape) {
		return tape == Tape.HEADING ? config.headingCameraLimitEnabled : config.pitchCameraLimitEnabled;
	}

	private static void setTapeEnabled(AngleWatcherConfig config, Tape tape, boolean value) {
		if (tape == Tape.HEADING) {
			config.headingCameraLimitEnabled = value;
		} else {
			config.pitchCameraLimitEnabled = value;
		}
	}

	/**
	 * Flips the camera-limit flag of the range at {@code index} (0-based) on the given
	 * tape — its keybind counterpart. Turns the tape's switch on if it was off, and the
	 * master switch off if no range on either tape still has its flag set. No-op when
	 * the slot is empty (e.g. only 3 ranges configured, keybind 7 pressed).
	 */
	public static void toggleRange(Tape tape, int index) {
		AngleWatcherConfig config = AngleWatcherConfig.get();
		List<RangeConfig> ranges = tape == Tape.HEADING ? config.headingRanges : config.pitchRanges;
		if (index < 0 || index >= ranges.size()) {
			return;
		}
		RangeConfig range = ranges.get(index);
		if (range == null) {
			return;
		}
		range.cameraLimit = !range.cameraLimit;
		if (range.cameraLimit && !tapeEnabled(config, tape)) {
			setTapeEnabled(config, tape, true);
		}
		if (!config.cameraLimitEnabled && anyRangeLimited(config)) {
			config.cameraLimitEnabled = true;
		}
		config.save();
	}

	/** @return true if any enabled range on either tape still has its camera-limit flag set. */
	private static boolean anyRangeLimited(AngleWatcherConfig config) {
		return hasLimitedRange(config.headingRanges) || hasLimitedRange(config.pitchRanges);
	}

	private static boolean hasLimitedRange(List<RangeConfig> ranges) {
		for (RangeConfig range : ranges) {
			if (range != null && range.cameraLimit) {
				return true;
			}
		}
		return false;
	}

	/** Clamps the player's view inside camera-limit ranges. Runs once per frame. */
	public static void tick(Minecraft client) {
		if (client.player == null || client.gui.screen() != null) {
			hadLast = false;
			return;
		}
		AngleWatcherConfig config = AngleWatcherConfig.get();
		if (!config.enabled || !config.cameraLimitEnabled) {
			hadLast = false;
			return;
		}
		Player player = client.player;

		float heading = Mth.wrapDegrees(player.getYRot());
		float pitch = player.getXRot();

		if (config.headingTape.enabled && tapeEnabled(config, Tape.HEADING)) {
			heading = limitHeading(config.headingRanges, player, heading);
		}
		if (config.pitchTape.enabled && tapeEnabled(config, Tape.PITCH)) {
			pitch = limitPitch(config.pitchRanges, player, pitch);
		}

		lastHeading = heading;
		lastPitch = pitch;
		hadLast = true;
	}

	private static float limitHeading(List<RangeConfig> ranges, Player player, float heading) {
		// Each range opts in individually; overlapping camera-limit ranges keep the first match.
		for (RangeConfig range : ranges) {
			if (range == null || !range.enabled || !range.cameraLimit) {
				continue;
			}
			if (!TapeRenderer.inRange(heading, range.start, range.end)) {
				continue;
			}
			float clamped = clampCircular(heading, (float) range.start, (float) range.end);
			if (clamped != heading) {
				player.setYRot(clamped);
			}
			return clamped;
		}
		// Outside every range: if we were inside a limit range last check, the player
		// slipped out — push back to the nearer boundary.
		if (hadLast) {
			for (RangeConfig range : ranges) {
				if (range == null || !range.enabled || !range.cameraLimit) {
					continue;
				}
				if (TapeRenderer.inRange(lastHeading, range.start, range.end)
						&& !TapeRenderer.inRange(heading, range.start, range.end)) {
					float target = nearerBoundary(heading, (float) range.start, (float) range.end);
					player.setYRot(target);
					return target;
				}
			}
		}
		return heading;
	}

	private static float limitPitch(List<RangeConfig> ranges, Player player, float pitch) {
		for (RangeConfig range : ranges) {
			if (range == null || !range.enabled || !range.cameraLimit) {
				continue;
			}
			// Pitch is linear (-90..90), no wrap: plain interval containment.
			double lo = Math.min(range.start, range.end);
			double hi = Math.max(range.start, range.end);
			if (pitch < lo || pitch > hi) {
				continue;
			}
			float clamped = (float) Mth.clamp(pitch, lo + CLAMP_EPSILON, hi - CLAMP_EPSILON);
			if (clamped != pitch) {
				player.setXRot(clamped);
			}
			return clamped;
		}
		return pitch;
	}

	/**
	 * Holds {@code heading} inside the circular interval [start, end] by clamping it
	 * against whichever end boundary it is crossing. The start boundary cannot be
	 * crossed in a single tick from strictly inside (the exit detection above covers it).
	 */
	private static float clampCircular(float heading, float start, float end) {
		float span = end - start;
		while (span < 0) span += 360.0f;
		while (span >= 360.0f) span -= 360.0f;
		if (span == 0) {
			return heading;
		}
		double delta = ((heading - start) % 360.0 + 360.0) % 360.0;
		if (delta > span - CLAMP_EPSILON) {
			return start + (float) (span - CLAMP_EPSILON);
		}
		return heading;
	}

	/** @return the boundary of [start, end] circularly nearest to {@code heading}. */
	private static float nearerBoundary(float heading, float start, float end) {
		double dStart = Math.abs(normalizedDelta(heading - start));
		double dEnd = Math.abs(normalizedDelta(end - heading));
		return dStart <= dEnd ? start + (float) CLAMP_EPSILON : end - (float) CLAMP_EPSILON;
	}

	private static double normalizedDelta(double delta) {
		delta = (delta % 360.0 + 360.0) % 360.0;
		if (delta > 180.0) delta -= 360.0;
		return delta;
	}
}
