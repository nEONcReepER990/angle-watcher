package dev.anglewatcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

/**
 * Root config object. Persisted as pretty-printed JSON in {@code config/anglewatcher.json}.
 * Plain Gson POJO — the Cloth Config screen and the HUD editor screen mutate it and call {@link #save()}.
 */
public final class AngleWatcherConfig {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Hard cap on angle ranges per tape. */
	public static final int MAX_RANGES_PER_TAPE = 10;
	/** Hard cap on angle pins per tape. */
	public static final int MAX_PINS_PER_TAPE = 20;

	public boolean enabled = true;
	/** Master switch for the range camera limit; each tape can additionally opt in. */
	public boolean cameraLimitEnabled = false;
	/** Per-tape switches for the range camera limit (in addition to the master switch). */
	public boolean headingCameraLimitEnabled = false;
	public boolean pitchCameraLimitEnabled = false;

	public StripConfig headingTape = StripConfig.headingDefaults();
	public StripConfig pitchTape = StripConfig.pitchDefaults();

	public List<RangeConfig> headingRanges = new ArrayList<>(List.of(
			RangeConfig.of("North", 330.0, 30.0, 0x80FFAA00, true),
			RangeConfig.of("East", 60.0, 120.0, 0x8055FFFF, true),
			RangeConfig.of("South", 150.0, 210.0, 0x80FF5555, true),
			RangeConfig.of("West", 240.0, 300.0, 0x8055FF55, true)
	));

	public List<RangeConfig> pitchRanges = new ArrayList<>(List.of(
			RangeConfig.of("Up", -90.0, -30.0, 0x8055AAFF, true),
			RangeConfig.of("Down", 30.0, 90.0, 0x80FF8855, true)
	));

	private static final AngleWatcherConfig INSTANCE = load();

	public static AngleWatcherConfig get() {
		return INSTANCE;
	}

	public List<RangeConfig> rangesFor(StripConfig strip) {
		return strip == headingTape ? headingRanges : pitchRanges;
	}

	/** clamps both range lists to {@link #MAX_RANGES_PER_TAPE} (oldest entries are kept). */
	private void clampRangeCounts() {
		while (headingRanges.size() > MAX_RANGES_PER_TAPE) {
			headingRanges.remove(headingRanges.size() - 1);
		}
		while (pitchRanges.size() > MAX_RANGES_PER_TAPE) {
			pitchRanges.remove(pitchRanges.size() - 1);
		}
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("anglewatcher.json");
	}

	private static AngleWatcherConfig load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				String json = Files.readString(path);
				AngleWatcherConfig read = GSON.fromJson(json, AngleWatcherConfig.class);
				if (read != null) {
					read.repair();
					return read;
				}
			} catch (IOException | com.google.gson.JsonParseException e) {
				// fall through to defaults
			}
		}
		AngleWatcherConfig config = new AngleWatcherConfig();
		config.save();
		return config;
	}

	/** Fill in anything missing after a partial/older config file is read. */
	public void repair() {
		if (headingTape == null) headingTape = StripConfig.headingDefaults();
		headingTape.repair();
		if (pitchTape == null) pitchTape = StripConfig.pitchDefaults();
		pitchTape.repair();
		if (headingRanges == null) headingRanges = new ArrayList<>();
		headingRanges.removeIf(r -> r == null || !r.repair());
		if (pitchRanges == null) pitchRanges = new ArrayList<>();
		pitchRanges.removeIf(r -> r == null || !r.repair());
		clampRangeCounts();
	}

	public void save() {
		try {
			Path path = path();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException e) {
			// Non-fatal; HUD keeps running with in-memory values.
		}
	}

	/** One screen position for a tape. */
	public enum Anchor {
		TOP_LEFT, TOP_CENTER, TOP_RIGHT,
		CENTER_LEFT, CENTER, CENTER_RIGHT,
		BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
	}

	/** Position/size/appearance settings for one tape. Shared by both tapes. */
	public static final class StripConfig {
		public boolean enabled = true;

		public Anchor anchor = Anchor.TOP_CENTER;
		public int offsetX = 0;
		public int offsetY = 20;

		/** Explicit tape size in screen pixels (before any internal scaling of text). */
		public int width = 240;
		public int height = 22;
		/** Background opacity, 0..100. */
		public int opacity = 60;

		public boolean showTicks = true;
		public boolean showLabels = true;
		public boolean showCardinals = true;
		public boolean showReadout = true;

		/** Width of the center marker line in pixels (the red "you are here" line). */
		public int centerMarkerWidth = 2;
		/** ARGB color of the center marker line. */
		public int centerMarkerColor = 0xFFFF4444;

		/** Degrees between major ticks (5..90). Minor ticks are always {@code tickInterval / 3}. */
		public int tickInterval = 15;
		/** How the numeric readout is formatted. */
		public ReadoutFormat readoutFormat = ReadoutFormat.SIMPLE;

		/** When a chat/inventory/etc. screen is open, hide this tape. */
		public boolean hideWithGui = false;
		/** Whether this tape draws its angle pins. */
		public boolean showPins = true;
		/** The pinned angles for this tape. */
		public List<PinConfig> pins = new ArrayList<>();

		/** Sound played when the current angle enters one of this tape's alert-enabled ranges. */
		public AlertSound alertSound = AlertSound.OFF;
		/** Minimum ms between alert sounds for this tape. */
		public int alertCooldownMs = 800;

		/*
		// --- Legacy fields (pre editor). Read during migration, then cleared. ---
	 transient Integer legacyLength;
	 transient Float legacyScale;
*/

		public static StripConfig headingDefaults() {
			return new StripConfig();
		}

		public static StripConfig pitchDefaults() {
			StripConfig strip = new StripConfig();
			strip.anchor = Anchor.CENTER_LEFT;
			strip.offsetX = 8;
			strip.offsetY = 0;
			strip.width = 160;
			return strip;
		}

		void repair() {
			anchor = anchor == null ? Anchor.TOP_CENTER : anchor;
			offsetX = Mth_clamp(offsetX, -4000, 4000);
			offsetY = Mth_clamp(offsetY, -4000, 4000);
			width = Mth_clamp(width, 1, 8000);
			height = Mth_clamp(height, 1, 4000);
			opacity = Mth_clamp(opacity, 0, 100);
			centerMarkerWidth = Mth_clamp(centerMarkerWidth, 1, 100);
			tickInterval = Mth_clamp(tickInterval, 5, 90);
			readoutFormat = readoutFormat == null ? ReadoutFormat.SIMPLE : readoutFormat;
			pins = pins == null ? new ArrayList<>() : pins;
			pins.removeIf(p -> p == null || !p.repair());
			while (pins.size() > MAX_PINS_PER_TAPE) {
				pins.remove(pins.size() - 1);
			}
			alertSound = alertSound == null ? AlertSound.OFF : alertSound;
			alertCooldownMs = Mth_clamp(alertCooldownMs, 100, 10_000);
		}

		private static int Mth_clamp(int value, int min, int max) {
			return Math.max(min, Math.min(max, value));
		}
	}

	/** How the tape's numeric readout is formatted. */
	public enum ReadoutFormat {
		/** Rounds to whole degrees, e.g. "37°". */
		SIMPLE,
		/** Rounds to a tenth of a degree, e.g. "37.4°". */
		PRECISE,
		/** Same as SIMPLE but formatted as a 3-digit compass code, e.g. "037°". */
		DECIMAL
	}

	/** Vanilla sounds selectable for range alerts. */
	public enum AlertSound {
		OFF(null),
		LEVEL_UP(SoundEvents.PLAYER_LEVELUP),
		XP_PICKUP(SoundEvents.EXPERIENCE_ORB_PICKUP),
		NOTE_PLING(SoundEvents.NOTE_BLOCK_PLING),
		ARROW_HIT(SoundEvents.ARROW_HIT_PLAYER),
		TOAST(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE);

		/** {@link SoundEvent} or {@code Holder<SoundEvent>} depending on the vanilla constant. */
		public final Object sound;

		AlertSound(Object sound) {
			this.sound = sound;
		}
	}

	/** One pinned angle drawn as a marker line on a tape. */
	public static final class PinConfig {
		public String name = "";
		/** The pinned angle in the tape's own convention (degrees). */
		public double angle = 0.0;
		/** ARGB color, including alpha byte. */
		public int color = 0xFFFFD050;

		public static PinConfig of(String name, double angle, int color) {
			PinConfig pin = new PinConfig();
			pin.name = name;
			pin.angle = angle;
			pin.color = color;
			return pin;
		}

		/** @return false if the pin is unusable and should be dropped. */
		public boolean repair() {
			if (name == null) name = "";
			angle = Math.max(-180.0, Math.min(180.0, angle));
			return true;
		}
	}

	/** One configurable angle range drawn as a colored band on a tape. */
	public static final class RangeConfig {
		public String name = "";
		/** Inclusive start angle in degrees. */
		public double start = 0.0;
		/** Inclusive end angle in degrees. */
		public double end = 360.0;
		/** ARGB color, including alpha byte. */
		public int color = 0x80FFAA00;
		public boolean enabled = true;
		/** When the camera-limit master switch is on, the view is held inside this range. */
		public boolean cameraLimit = false;

		public static RangeConfig of(String name, double start, double end, int color, boolean enabled) {
			RangeConfig range = new RangeConfig();
			range.name = name;
			range.start = start;
			range.end = end;
			range.color = color;
			range.enabled = enabled;
			return range;
		}

		/** @return false if the range is unusable and should be dropped. */
		public boolean repair() {
			if (name == null) name = "";
			start = Math.max(-360.0, Math.min(360.0, start));
			end = Math.max(-360.0, Math.min(360.0, end));
			return true;
		}
	}
}
