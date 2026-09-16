package dev.anglewatcher.compat;

import dev.anglewatcher.config.AngleWatcherConfig;
import dev.anglewatcher.config.AngleWatcherConfig.PinConfig;
import dev.anglewatcher.config.AngleWatcherConfig.RangeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Preset sharing: exports both tapes' angle ranges and pins as one portable code,
 * delivered either as a clickable chat message (copy-to-clipboard or pre-filled
 * import command) or as a plain code pasted into {@code /anglewatcher import}.
 *
 * <p>The code is the JSON of the shared payload, Base64 (URL-safe) encoded — no
 * mod-to-mod network protocol involved, everything stays client-side.
 */
public final class AngleWatcherShare {
	private static final String PREFIX = "AW1";

	private AngleWatcherShare() {
	}

	/** What gets shared: ranges + pins of both tapes. Sounds/camera flags stay personal. */
	private static final class Payload {
		public List<RangeConfig> headingRanges;
		public List<PinConfig> headingPins;
		public List<RangeConfig> pitchRanges;
		public List<PinConfig> pitchPins;
	}

	/** Encodes the current ranges + pins into a share code. */
	public static String exportCode(AngleWatcherConfig config) {
		Payload payload = new Payload();
		payload.headingRanges = config.headingRanges;
		payload.headingPins = config.headingTape.pins == null ? List.of() : config.headingTape.pins;
		payload.pitchRanges = config.pitchRanges;
		payload.pitchPins = config.pitchTape.pins == null ? List.of() : config.pitchTape.pins;
		String json = AngleWatcherConfig.GSON.toJson(payload);
		return PREFIX + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

	/** Decodes a share code; @return null if invalid. */
	private static Payload decode(String code) {
		code = code.trim();
		if (!code.startsWith(PREFIX)) {
			return null;
		}
		try {
			String json = new String(Base64.getUrlDecoder().decode(code.substring(PREFIX.length())),
					StandardCharsets.UTF_8);
			Payload payload = AngleWatcherConfig.GSON.fromJson(json, Payload.class);
			if (payload == null) {
				return null;
			}
			if (payload.headingRanges == null) payload.headingRanges = new ArrayList<>();
			if (payload.pitchRanges == null) payload.pitchRanges = new ArrayList<>();
			if (payload.headingPins == null) payload.headingPins = new ArrayList<>();
			if (payload.pitchPins == null) payload.pitchPins = new ArrayList<>();
			payload.headingRanges.removeIf(r -> r == null || !r.repair());
			payload.pitchRanges.removeIf(r -> r == null || !r.repair());
			payload.headingPins.removeIf(p -> p == null || !p.repair());
			payload.pitchPins.removeIf(p -> p == null || !p.repair());
			return payload;
		} catch (IllegalArgumentException | com.google.gson.JsonParseException e) {
			return null;
		}
	}

	/**
	 * Sends the share code to chat with two clickable choices: [Copy code] puts it
	 * on the clipboard, [Import] pre-fills the import command in the chat box.
	 */
	public static void exportToChat(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
		String code = exportCode(AngleWatcherConfig.get());

		MutableComponent copy = Component.translatable("command.anglewatcher.export.copy")
				.withStyle(Style.EMPTY
						.withColor(ChatFormatting.AQUA)
						.withClickEvent(new ClickEvent.CopyToClipboard(code))
						.withUnderlined(true));
		MutableComponent chat = Component.translatable("command.anglewatcher.export.chat")
				.withStyle(Style.EMPTY
						.withColor(ChatFormatting.GREEN)
						.withClickEvent(new ClickEvent.SuggestCommand("/anglewatcher import " + code))
						.withUnderlined(true));

		source.sendFeedback(Component.translatable("command.anglewatcher.export.header"));
		source.sendFeedback(Component.empty().append(copy).append(Component.literal("  ")).append(chat));
		source.sendFeedback(Component.translatable("command.anglewatcher.export.size", code.length()));
	}

	/** Imports from the current clipboard text. */
	public static void importFromClipboard(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
		String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
		if (clipboard == null || clipboard.isBlank()) {
			source.sendError(Component.translatable("command.anglewatcher.import.clipboard_empty"));
			return;
		}
		applyImport(clipboard, source.getPlayer());
	}

	/** Imports a raw code (from the command argument). */
	public static void importCode(String code, LocalPlayer player) {
		applyImport(code, player);
	}

	private static void applyImport(String code, LocalPlayer player) {
		AngleWatcherConfig config = AngleWatcherConfig.get();
		Payload payload = decode(code);
		if (payload == null) {
			if (player != null) {
				player.sendSystemMessage(Component.translatable("command.anglewatcher.import.invalid")
						.withStyle(ChatFormatting.RED));
			}
			return;
		}
		// Clamp to the same caps the config loader enforces.
		while (payload.headingRanges.size() > AngleWatcherConfig.MAX_RANGES_PER_TAPE) {
			payload.headingRanges.remove(payload.headingRanges.size() - 1);
		}
		while (payload.pitchRanges.size() > AngleWatcherConfig.MAX_RANGES_PER_TAPE) {
			payload.pitchRanges.remove(payload.pitchRanges.size() - 1);
		}
		while (payload.headingPins.size() > AngleWatcherConfig.MAX_PINS_PER_TAPE) {
			payload.headingPins.remove(payload.headingPins.size() - 1);
		}
		while (payload.pitchPins.size() > AngleWatcherConfig.MAX_PINS_PER_TAPE) {
			payload.pitchPins.remove(payload.pitchPins.size() - 1);
		}

		config.headingRanges = payload.headingRanges;
		config.pitchRanges = payload.pitchRanges;
		config.headingTape.pins = payload.headingPins;
		config.pitchTape.pins = payload.pitchPins;
		config.save();

		if (player != null) {
			player.sendSystemMessage(Component.translatable("command.anglewatcher.import.done",
					config.headingRanges.size(), config.pitchRanges.size(),
					config.headingTape.pins.size(), config.pitchTape.pins.size())
					.withStyle(ChatFormatting.GREEN));
		}
	}
}
