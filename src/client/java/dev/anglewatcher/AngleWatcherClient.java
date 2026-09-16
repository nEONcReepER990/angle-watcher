package dev.anglewatcher;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.blaze3d.platform.InputConstants;
import dev.anglewatcher.compat.AngleWatcherShare;
import dev.anglewatcher.config.AngleWatcherConfig;
import dev.anglewatcher.hud.AngleWatcherAlerts;
import dev.anglewatcher.hud.AngleWatcherCameraLimit;
import dev.anglewatcher.hud.TapeRenderer;
import dev.anglewatcher.screen.HudEditScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.resources.Identifier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Client entrypoint: loads the config, registers the HUD elements, keybinds
 * and the {@code /anglewatcher} client command, and drives the per-frame
 * camera limit and per-tick alert sounds.
 */
public final class AngleWatcherClient implements ClientModInitializer {
	public static final String MOD_ID = "anglewatcher";

	private static final TapeRenderer HEADING_RENDERER = new TapeRenderer(TapeRenderer.Orientation.HORIZONTAL);
	private static final TapeRenderer PITCH_RENDERER = new TapeRenderer(TapeRenderer.Orientation.VERTICAL);

	/** Custom keybind category so the binding groups under its own header. */
	public static final KeyMapping.Category KEY_CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "anglewatcher"));

	/**
	 * Unbound by default; opens the mouse-driven HUD editor. The explicit order keeps
	 * the non-limit entries pinned at the top of the mod's keybind category (vanilla
	 * sorts same-category entries by this int first, alphabetically only on ties).
	 */
	public static final KeyMapping OPEN_EDITOR = new KeyMapping(
			"key.anglewatcher.open_editor",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY,
			1);

	/** Unbound by default; flips the master switch in the mod config. */
	public static final KeyMapping TOGGLE_RENDERING = new KeyMapping(
			"key.anglewatcher.toggle_rendering",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY,
			2);

	/** Unbound by default; toggles the range camera limit master switch. */
	public static final KeyMapping TOGGLE_CAMERA_LIMIT = new KeyMapping(
			"key.anglewatcher.toggle_camera_limit",
			InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(),
			KEY_CATEGORY,
			3);

	/**
	 * Unbound by default; flips the camera-limit flag of the heading (yaw) range at
	 * this position (1–10, the order shown in the config screen). No-op for empty slots.
	 */
	public static final KeyMapping[] TOGGLE_YAW_RANGE_LIMIT = makeRangeToggles("yaw", 10, 11);
	/** Unbound by default; flips the camera-limit flag of the pitch range at this position (1–10). */
	public static final KeyMapping[] TOGGLE_PITCH_RANGE_LIMIT = makeRangeToggles("pitch", 10, 21);

	/**
	 * Creates the 10 per-position toggle keybinds for one tape, all in the mod's keybind
	 * category. Consecutive orders keep the entries sorted 1, 2, … 10 in the Key Binds
	 * screen instead of the alphabetical 1, 10, 2, … order.
	 */
	private static KeyMapping[] makeRangeToggles(String tape, int count, int firstOrder) {
		KeyMapping[] bindings = new KeyMapping[count];
		for (int i = 0; i < count; i++) {
			bindings[i] = new KeyMapping(
					"key.anglewatcher.toggle_" + tape + "_range_" + (i + 1),
					InputConstants.Type.KEYSYM,
					InputConstants.UNKNOWN.getValue(),
					KEY_CATEGORY,
					firstOrder + i);
		}
		return bindings;
	}

	@Override
	public void onInitializeClient() {
		// Force config load before first render.
		AngleWatcherConfig.get();

		KeyMappingHelper.registerKeyMapping(OPEN_EDITOR);
		KeyMappingHelper.registerKeyMapping(TOGGLE_RENDERING);
		KeyMappingHelper.registerKeyMapping(TOGGLE_CAMERA_LIMIT);
		for (KeyMapping binding : TOGGLE_YAW_RANGE_LIMIT) {
			KeyMappingHelper.registerKeyMapping(binding);
		}
		for (KeyMapping binding : TOGGLE_PITCH_RANGE_LIMIT) {
			KeyMappingHelper.registerKeyMapping(binding);
		}

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("heading_tape"),
				(graphics, deltaTracker) -> render(graphics, deltaTracker));

		ClientCommandRegistrationCallback.EVENT.register(AngleWatcherClient::registerCommands);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_EDITOR.consumeClick()) {
				if (client.player != null && client.gui.screen() == null) {
					client.gui.setScreen(new HudEditScreen(AngleWatcherConfig.get()));
				}
			}
			while (TOGGLE_RENDERING.consumeClick()) {
				AngleWatcherConfig config = AngleWatcherConfig.get();
				config.enabled = !config.enabled;
				config.save();
			}
			while (TOGGLE_CAMERA_LIMIT.consumeClick()) {
				AngleWatcherCameraLimit.toggle();
			}
			for (int i = 0; i < TOGGLE_YAW_RANGE_LIMIT.length; i++) {
				while (TOGGLE_YAW_RANGE_LIMIT[i].consumeClick()) {
					AngleWatcherCameraLimit.toggleRange(AngleWatcherCameraLimit.Tape.HEADING, i);
				}
			}
			for (int i = 0; i < TOGGLE_PITCH_RANGE_LIMIT.length; i++) {
				while (TOGGLE_PITCH_RANGE_LIMIT[i].consumeClick()) {
					AngleWatcherCameraLimit.toggleRange(AngleWatcherCameraLimit.Tape.PITCH, i);
				}
			}
			AngleWatcherAlerts.tick(client);
		});
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher,
			CommandBuildContext buildContext) {
		dispatcher.register(literal("anglewatcher")
				.then(literal("export").executes(ctx -> {
					AngleWatcherShare.exportToChat(ctx.getSource());
					return 1;
				}))
				.then(literal("import")
						.then(literal("clipboard").executes(ctx -> {
							AngleWatcherShare.importFromClipboard(ctx.getSource());
							return 1;
						}))
						.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
								.<FabricClientCommandSource, String>argument("code", StringArgumentType.greedyString())
								.executes(ctx -> {
									AngleWatcherShare.importCode(
											StringArgumentType.getString(ctx, "code"),
											ctx.getSource().getPlayer());
									return 1;
								}))));
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.gui.hud.isHidden()) {
			return;
		}

		AngleWatcherConfig config = AngleWatcherConfig.get();
		if (!config.enabled) {
			return;
		}

		// Soft camera limit: runs here so it reacts within the same frame the mouse
		// turned, right before the tapes (and any alert) are drawn.
		AngleWatcherCameraLimit.tick(client);

		Font font = client.font;
		int screenWidth = graphics.guiWidth();
		int screenHeight = graphics.guiHeight();
		// True while a chat/inventory/etc. screen is open.
		boolean guiOpen = client.gui.screen() != null;

		if (config.headingTape.enabled && !(guiOpen && config.headingTape.hideWithGui)) {
			float heading = TapeRenderer.headingFromYaw(client.player.getYRot());
			HEADING_RENDERER.render(graphics, font, config.headingTape, heading, config.headingRanges,
					screenWidth, screenHeight);
		}

		if (config.pitchTape.enabled && !(guiOpen && config.pitchTape.hideWithGui)) {
			// Vanilla pitch: -90 = straight up, +90 = straight down.
			float pitch = client.player.getXRot();
			PITCH_RENDERER.render(graphics, font, config.pitchTape, pitch, config.pitchRanges,
					screenWidth, screenHeight);
		}
	}
}
