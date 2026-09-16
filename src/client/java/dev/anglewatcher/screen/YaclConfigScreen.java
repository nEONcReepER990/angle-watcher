package dev.anglewatcher.screen;

import dev.anglewatcher.config.AngleWatcherConfig;
import dev.anglewatcher.config.AngleWatcherConfig.Anchor;
import dev.anglewatcher.config.AngleWatcherConfig.RangeConfig;
import dev.anglewatcher.config.AngleWatcherConfig.StripConfig;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

/**
 * YACL (YetAnotherConfigLib) config screen. Mirrors the Cloth Config screen:
 * entries mutate the live config in memory and the YACL "Save" button persists them.
 * Ranges are dynamic: add/remove buttons swap the screen (rebuild), max 10 per tape.
 * Each tape category carries a button that opens the mouse-driven HUD editor.
 */
public final class YaclConfigScreen {
	/** Remembered parent (e.g. the Mod Menu screen) so screen rebuilds keep the back-navigation intact. */
	private static Screen originalParent;

	private YaclConfigScreen() {
	}

	public static Screen create(Screen parent) {
		if (originalParent == null || (parent != null && parent != originalParent)) {
			originalParent = parent;
		}
		return build().generateScreen(originalParent);
	}

	private static YetAnotherConfigLib build() {
		AngleWatcherConfig live = AngleWatcherConfig.get();

		// ---- General -----------------------------------------------------------
		ConfigCategory general = ConfigCategory.createBuilder()
				.name(Component.translatable("category.anglewatcher.general"))
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable("option.anglewatcher.enabled"))
						.description(OptionDescription.of(Component.translatable("option.anglewatcher.enabled.tooltip")))
						.binding(true, () -> live.enabled, v -> live.enabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable("option.anglewatcher.cameraLimitEnabled"))
						.description(OptionDescription.of(Component.translatable("option.anglewatcher.cameraLimitEnabled.tooltip")))
						.binding(false, () -> live.cameraLimitEnabled, v -> live.cameraLimitEnabled = v)
						.controller(BooleanControllerBuilder::create)
						.build())
				.option(editorButton())
				.build();

		// ---- Tapes -------------------------------------------------------------
		ConfigCategory heading = tapeCategory("category.anglewatcher.heading_tape", live.headingTape, true);
		ConfigCategory pitch = tapeCategory("category.anglewatcher.pitch_tape", live.pitchTape, false);

		// ---- Angle ranges ------------------------------------------------------
		ConfigCategory headingRanges = rangesCategory("category.anglewatcher.heading_ranges", live.headingRanges);
		ConfigCategory pitchRanges = rangesCategory("category.anglewatcher.pitch_ranges", live.pitchRanges);

		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable("title.anglewatcher.config"))
				.save(live::save)
				.category(general)
				.category(heading)
				.category(pitch)
				.category(headingRanges)
				.category(pitchRanges)
				.build();
	}

	private static ConfigCategory tapeCategory(String nameKey, StripConfig strip, boolean isHeading) {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.translatable(nameKey))
				.option(editorButton());

		builder.option(Option.<Boolean>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.enabled"))
				.binding(true, () -> strip.enabled, v -> strip.enabled = v)
				.controller(BooleanControllerBuilder::create)
				.build());

		builder.option(Option.<Anchor>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.anchor"))
				.description(OptionDescription.of(Component.translatable("option.anglewatcher.tape.anchor.tooltip")))
				.binding(Anchor.TOP_CENTER, () -> strip.anchor, v -> strip.anchor = v)
				// YACL needs the enum class explicitly (and a formatter for readable labels).
				.controller(opt -> EnumControllerBuilder.create(opt)
						.enumClass(Anchor.class)
						.formatValue(v -> Component.literal(prettyAnchor(v))))
				.build());

		builder.group(dualIntGroup(Component.translatable("option.anglewatcher.tape.offsetX"),
				strip.offsetX, 0, -4000, 4000, null, v -> strip.offsetX = v));
		builder.group(dualIntGroup(Component.translatable("option.anglewatcher.tape.offsetY"),
				strip.offsetY, 20, -4000, 4000, null, v -> strip.offsetY = v));
		builder.group(dualIntGroup(Component.translatable("option.anglewatcher.tape.width"),
				strip.width, 240, 1, 2000, Component.translatable("option.anglewatcher.tape.width.tooltip"), v -> strip.width = v));
		builder.group(dualIntGroup(Component.translatable("option.anglewatcher.tape.height"),
				strip.height, 22, 1, 300, Component.translatable("option.anglewatcher.tape.height.tooltip"), v -> strip.height = v));
		builder.group(dualIntGroup(Component.translatable("option.anglewatcher.tape.opacity"),
				strip.opacity, 60, 0, 100, null, v -> strip.opacity = v));

		builder.option(Option.<java.awt.Color>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.centerMarkerColor"))
				.description(OptionDescription.of(Component.translatable("option.anglewatcher.tape.centerMarkerColor.tooltip")))
				.binding(new java.awt.Color(0xFFFF4444, true), () -> awtOf(strip.centerMarkerColor), v -> strip.centerMarkerColor = argbOf(v))
				.controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
				.build());
		builder.group(dualIntGroup(Component.translatable("option.anglewatcher.tape.centerMarkerWidth"),
				strip.centerMarkerWidth, 2, 1, 100, Component.translatable("option.anglewatcher.tape.centerMarkerWidth.tooltip"), v -> strip.centerMarkerWidth = v));

		builder.option(Option.<Boolean>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.showTicks"))
				.binding(true, () -> strip.showTicks, v -> strip.showTicks = v)
				.controller(BooleanControllerBuilder::create)
				.build());
		builder.option(Option.<Boolean>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.showLabels"))
				.binding(true, () -> strip.showLabels, v -> strip.showLabels = v)
				.controller(BooleanControllerBuilder::create)
				.build());
		builder.option(Option.<Boolean>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.showCardinals"))
				.binding(true, () -> strip.showCardinals, v -> strip.showCardinals = v)
				.controller(BooleanControllerBuilder::create)
				.build());
		builder.option(Option.<Boolean>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.showReadout"))
				.binding(true, () -> strip.showReadout, v -> strip.showReadout = v)
				.controller(BooleanControllerBuilder::create)
				.build());

		// ---- Tape display extras -------------------------------------------
		builder.option(Option.<Boolean>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.hideWithGui"))
				.description(OptionDescription.of(Component.translatable("option.anglewatcher.tape.hideWithGui.tooltip")))
				.binding(false, () -> strip.hideWithGui, v -> strip.hideWithGui = v)
				.controller(BooleanControllerBuilder::create)
				.build());

		builder.group(dualIntGroup(Component.translatable("option.anglewatcher.tape.tickInterval"),
				strip.tickInterval, 15, 5, 90, Component.translatable("option.anglewatcher.tape.tickInterval.tooltip"),
				v -> strip.tickInterval = v));

		builder.option(Option.<dev.anglewatcher.config.AngleWatcherConfig.ReadoutFormat>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.readoutFormat"))
				.binding(dev.anglewatcher.config.AngleWatcherConfig.ReadoutFormat.SIMPLE,
						() -> strip.readoutFormat, v -> strip.readoutFormat = v)
				.controller(opt -> EnumControllerBuilder.create(opt)
						.enumClass(dev.anglewatcher.config.AngleWatcherConfig.ReadoutFormat.class)
						.formatValue(v -> Component.literal(prettyEnum(v))))
				.build());

		// ---- Angle pins ------------------------------------------------------
		builder.option(Option.<Boolean>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.showPins"))
				.binding(true, () -> strip.showPins, v -> strip.showPins = v)
				.controller(BooleanControllerBuilder::create)
				.build());

		if (strip.pins == null) {
			strip.pins = new java.util.ArrayList<>();
		}
		for (int i = 0; i < strip.pins.size(); i++) {
			dev.anglewatcher.config.AngleWatcherConfig.PinConfig pin = strip.pins.get(i);
			final int index = i;
			double pMin = isHeading ? 0.0 : -90.0;
			double pMax = isHeading ? 360.0 : 90.0;

			OptionGroup.Builder pinGroup = OptionGroup.createBuilder()
					.name(Component.translatable("option.anglewatcher.pin.slot", i + 1,
							Component.literal(pin.name == null || pin.name.isEmpty()
									? String.format(Locale.ROOT, "%.0f°", pin.angle) : pin.name)))
					.collapsed(true);
			pinGroup.option(Option.<String>createBuilder()
					.name(Component.translatable("option.anglewatcher.pin.name"))
					.binding("", () -> pin.name, v -> pin.name = v)
					.controller(StringControllerBuilder::create)
					.build());
			pinGroup.option(doubleSliderOption(Component.translatable("option.anglewatcher.pin.angle"),
					pin.angle, pin.angle, pMin, pMax, v -> pin.angle = v));
			pinGroup.option(doubleFieldOption(Component.translatable("option.anglewatcher.pin.angle_exact"),
					pin.angle, pin.angle, pMin, pMax, v -> pin.angle = v));
			pinGroup.option(Option.<Color>createBuilder()
					.name(Component.translatable("option.anglewatcher.pin.color"))
					.binding(new Color(0xFFFFD050, true), () -> awtOf(pin.color), v -> pin.color = argbOf(v))
					.controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
					.build());
			pinGroup.option(ButtonOption.createBuilder()
					.name(Component.translatable("option.anglewatcher.pin.remove"))
					.action((screen, opt) -> {
						strip.pins.remove(index);
						AngleWatcherConfig.get().save();
						rebuild();
					})
					.build());
			builder.group(pinGroup.build());
		}
		if (strip.pins.size() < AngleWatcherConfig.MAX_PINS_PER_TAPE) {
			builder.option(ButtonOption.createBuilder()
					.name(Component.translatable("option.anglewatcher.pin.add"))
					.action((screen, opt) -> {
						strip.pins.add(dev.anglewatcher.config.AngleWatcherConfig.PinConfig.of(
								Component.translatable("option.anglewatcher.editor.pin_name").getString(),
								isHeading ? 0.0 : 0.0, 0xFFFFD050));
						AngleWatcherConfig.get().save();
						rebuild();
					})
					.build());
		}

		// ---- Range alerts + camera limit -------------------------------------
		builder.option(Option.<dev.anglewatcher.config.AngleWatcherConfig.AlertSound>createBuilder()
				.name(Component.translatable("option.anglewatcher.tape.alertSound"))
				.description(OptionDescription.of(Component.translatable("option.anglewatcher.tape.alertSound.tooltip")))
				.binding(dev.anglewatcher.config.AngleWatcherConfig.AlertSound.OFF,
						() -> strip.alertSound, v -> strip.alertSound = v)
				.controller(opt -> EnumControllerBuilder.create(opt)
						.enumClass(dev.anglewatcher.config.AngleWatcherConfig.AlertSound.class)
						.formatValue(v -> Component.literal(prettyEnum(v))))
				.build());
		builder.group(dualIntGroup(Component.translatable("option.anglewatcher.tape.alertCooldown"),
				strip.alertCooldownMs, 800, 100, 10_000,
				Component.translatable("option.anglewatcher.tape.alertCooldown.tooltip"),
				v -> strip.alertCooldownMs = v));

		return builder.build();
	}

	/**
	 * One numeric setting exposed as BOTH controls at once: a coarse slider and an
	 * exact numeric text field, inside a collapsible group named after the setting.
	 * Both options bind to the same backing value and cross-sync through listeners,
	 * so editing either keeps the other's pending value consistent.
	 */
	private static OptionGroup dualIntGroup(Component label, int value, int def,
			int min, int max, Component tooltip, java.util.function.IntConsumer setter) {
		Option.Builder<Integer> sliderBuilder = Option.<Integer>createBuilder()
				.name(Component.translatable("option.anglewatcher.common.slider"));
		if (tooltip != null) {
			sliderBuilder.description(OptionDescription.of(tooltip));
		}
		Option<Integer> slider = sliderBuilder.binding(def, () -> value, setter::accept)
				.controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(1))
				.build();

		Option<Integer> field = Option.<Integer>createBuilder()
				.name(Component.translatable("option.anglewatcher.common.exact"))
				.binding(def, () -> value, setter::accept)
				.controller(opt -> IntegerFieldControllerBuilder.create(opt).min(min).max(max))
				.build();

		crossSync(slider, field);

		return OptionGroup.createBuilder()
				.name(label)
				.collapsed(true)
				.option(slider)
				.option(field)
				.build();
	}

	/**
	 * Keeps two options bound to the same value consistent. Listens on
	 * {@link OptionEventListener.Event#STATE_CHANGE} only (not the initial event) and
	 * guards with an equality check so the mutual notifications cannot loop.
	 */
	private static void crossSync(Option<Integer> a, Option<Integer> b) {
		a.addEventListener((opt, event) -> {
			if (event == OptionEventListener.Event.STATE_CHANGE && !b.pendingValue().equals(opt.pendingValue())) {
				b.requestSet(opt.pendingValue());
			}
		});
		b.addEventListener((opt, event) -> {
			if (event == OptionEventListener.Event.STATE_CHANGE && !a.pendingValue().equals(opt.pendingValue())) {
				a.requestSet(opt.pendingValue());
			}
		});
	}

	/** Keeps two double-valued options bound to the same value consistent (see {@link #crossSync}). */
	private static void crossSyncDoubles(Option<Double> a, Option<Double> b) {
		a.addEventListener((opt, event) -> {
			if (event == OptionEventListener.Event.STATE_CHANGE && Double.compare(b.pendingValue(), opt.pendingValue()) != 0) {
				b.requestSet(opt.pendingValue());
			}
		});
		b.addEventListener((opt, event) -> {
			if (event == OptionEventListener.Event.STATE_CHANGE && Double.compare(a.pendingValue(), opt.pendingValue()) != 0) {
				a.requestSet(opt.pendingValue());
			}
		});
	}

	/** Double-precision variant of {@link #dualIntGroup} (angle range entries). */
	private static OptionGroup dualDoubleGroup(Component label, double value, double def,
			double min, double max, java.util.function.DoubleConsumer setter) {
		Option<Double> slider = doubleSliderOption(Component.translatable("option.anglewatcher.common.slider"), value, def, min, max, setter);
		Option<Double> field = doubleFieldOption(Component.translatable("option.anglewatcher.common.exact"), value, def, min, max, setter);

		crossSyncDoubles(slider, field);

		return OptionGroup.createBuilder()
				.name(label)
				.collapsed(true)
				.option(slider)
				.option(field)
				.build();
	}

	private static Option<Double> doubleSliderOption(Component name, double value, double def,
			double min, double max, java.util.function.DoubleConsumer setter) {
		return Option.<Double>createBuilder()
				.name(name)
				.binding(def, () -> value, setter::accept)
				.controller(opt -> DoubleSliderControllerBuilder.create(opt)
						.range(min, max).step(1.0)
						.formatValue(v -> Component.literal(String.format(Locale.ROOT, "%.0f°", v))))
				.build();
	}

	private static Option<Double> doubleFieldOption(Component name, double value, double def,
			double min, double max, java.util.function.DoubleConsumer setter) {
		return Option.<Double>createBuilder()
				.name(name)
				.binding(def, () -> value, setter::accept)
				.controller(opt -> DoubleFieldControllerBuilder.create(opt).range(min, max))
				.build();
	}

	/**
	 * Adds a slider + exact-value row pair (cross-synced) for one range angle directly
	 * into the given per-range group — YACL does not allow groups nested inside groups.
	 */
	private static void addDualDoubleRows(OptionGroup.Builder group, Component baseName, double value, double def,
			java.util.function.DoubleConsumer setter) {
		Component sliderName = Component.translatable("option.anglewatcher.common.slider").append(" — ").append(baseName);
		Component fieldName = Component.translatable("option.anglewatcher.common.exact").append(" — ").append(baseName);
		Option<Double> slider = doubleSliderOption(sliderName, value, def, -360.0, 360.0, setter);
		Option<Double> field = doubleFieldOption(fieldName, value, def, -360.0, 360.0, setter);
		crossSyncDoubles(slider, field);
		group.option(slider);
		group.option(field);
	}

	private static ConfigCategory rangesCategory(String nameKey, List<RangeConfig> ranges) {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.translatable(nameKey))
				.option(LabelOption.create(Component.translatable("option.anglewatcher.ranges.desc")));

		for (int i = 0; i < ranges.size(); i++) {
			RangeConfig range = ranges.get(i);
			final int index = i;

			OptionGroup.Builder group = OptionGroup.createBuilder()
					.name(Component.translatable("option.anglewatcher.range.slot", i + 1))
					.collapsed(true);

			group.option(Option.<Boolean>createBuilder()
					.name(Component.translatable("option.anglewatcher.range.enabled"))
					.binding(true, () -> range.enabled, v -> range.enabled = v)
					.controller(BooleanControllerBuilder::create)
					.build());
			group.option(Option.<Boolean>createBuilder()
					.name(Component.translatable("option.anglewatcher.range.cameraLimit"))
					.description(OptionDescription.of(Component.translatable("option.anglewatcher.range.cameraLimit.tooltip")))
					.binding(false, () -> range.cameraLimit, v -> range.cameraLimit = v)
					.controller(BooleanControllerBuilder::create)
					.build());
			group.option(Option.<String>createBuilder()
					.name(Component.translatable("option.anglewatcher.range.name"))
					.binding("", () -> range.name, v -> range.name = v)
					.controller(StringControllerBuilder::create)
					.build());
			// Range angles are always sliders (a field variant exists but sliders
			// are the friendlier default for a 720° dial).
			addDualDoubleRows(group, Component.translatable("option.anglewatcher.range.start"),
					range.start, 0.0, v -> range.start = v);
			addDualDoubleRows(group, Component.translatable("option.anglewatcher.range.end"),
					range.end, 45.0, v -> range.end = v);
			group.option(Option.<Color>createBuilder()
					.name(Component.translatable("option.anglewatcher.range.color"))
					.description(OptionDescription.of(Component.translatable("option.anglewatcher.range.color.tooltip")))
					.binding(new Color(0x80FFAA00, true), () -> awtOf(range.color), v -> range.color = argbOf(v))
					.controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(true))
					.build());
			group.option(ButtonOption.createBuilder()
					.name(Component.translatable("option.anglewatcher.ranges.remove"))															.action((screen, opt) -> {
																ranges.remove(index);
																AngleWatcherConfig.get().save();
																rebuild();
															})
					.build());

			builder.group(group.build());
		}

		if (ranges.size() < AngleWatcherConfig.MAX_RANGES_PER_TAPE) {
			builder.option(ButtonOption.createBuilder()
					.name(Component.translatable("option.anglewatcher.ranges.add"))
					.action((screen, opt) -> {
						ranges.add(RangeConfig.of(
								Component.translatable("option.anglewatcher.range.new").getString(),
								0.0, 45.0, 0x80FFAA00, false));
						AngleWatcherConfig.get().save();
						rebuild();
					})
					.build());
		} else {
			builder.option(LabelOption.create(
					Component.translatable("option.anglewatcher.ranges.max", AngleWatcherConfig.MAX_RANGES_PER_TAPE)));
		}

		return builder.build();
	}

	/** A list entry that opens the mouse-driven HUD editor; clicking swaps screens immediately. */
	private static ButtonOption editorButton() {
		return ButtonOption.createBuilder()
				.name(Component.translatable("option.anglewatcher.editor.open_plain"))
				.action((screen, opt) -> {
					Minecraft client = Minecraft.getInstance();
					client.gui.setScreen(new HudEditScreen(AngleWatcherConfig.get(), client.gui.screen()));
				})
				.build();
	}

	/** Replaces the currently open config screen after a structural (add/remove range) change. */
	private static void rebuild() {
		Minecraft client = Minecraft.getInstance();
		client.gui.setScreen(build().generateScreen(originalParent));
	}

	private static Color awtOf(int argb) {
		return new Color(argb, true);
	}

	private static String prettyAnchor(Anchor anchor) {
		String lower = anchor.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	/** "LEVEL_UP" → "Level Up" (used for the new enums' controller labels). */
	private static String prettyEnum(Enum<?> value) {
		String lower = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	private static int argbOf(Color color) {
		return color.getRGB();
	}
}
