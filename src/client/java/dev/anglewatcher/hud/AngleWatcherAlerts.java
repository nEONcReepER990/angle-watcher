package dev.anglewatcher.hud;

import dev.anglewatcher.config.AngleWatcherConfig;
import dev.anglewatcher.config.AngleWatcherConfig.AlertSound;
import dev.anglewatcher.config.AngleWatcherConfig.RangeConfig;
import dev.anglewatcher.config.AngleWatcherConfig.StripConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

/**
 * Plays the per-tape alert sound while the player's angle sits inside an
 * alert-enabled range, throttled by the tape's cooldown. Runs once per client tick.
 */
public final class AngleWatcherAlerts {
	private static long lastHeadingAlert;
	private static long lastPitchAlert;

	private AngleWatcherAlerts() {
	}

	public static void tick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		AngleWatcherConfig config = AngleWatcherConfig.get();
		if (!config.enabled) {
			return;
		}
		long now = System.currentTimeMillis();

		if (config.headingTape.enabled && config.headingTape.alertSound != AlertSound.OFF) {
			float heading = TapeRenderer.headingFromYaw(client.player.getYRot());
			lastHeadingAlert = alert(client, config.headingTape, config.headingRanges, heading, now, lastHeadingAlert);
		}
		if (config.pitchTape.enabled && config.pitchTape.alertSound != AlertSound.OFF) {
			lastPitchAlert = alert(client, config.pitchTape, config.pitchRanges, client.player.getXRot(), now, lastPitchAlert);
		}
	}

	private static long alert(Minecraft client, StripConfig strip, List<RangeConfig> ranges,
			float angle, long now, long last) {
		if (!TapeRenderer.inAnyRange(ranges, angle)) {
			return last;
		}
		if (now - last < strip.alertCooldownMs) {
			return last;
		}
		play(client, strip.alertSound);
		return now;
	}

	/** Plays the configured vanilla sound as a UI sound (no world position). */
	private static void play(Minecraft client, AlertSound alert) {
		if (alert.sound == null) {
			return;
		}
		switch (alert.sound) {
			case SoundEvent event -> client.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f));
			case Holder<?> holder -> client.getSoundManager()
					.play(SimpleSoundInstance.forUI((Holder<SoundEvent>) holder, 1.0f));
			default -> {
			}
		}
	}
}
