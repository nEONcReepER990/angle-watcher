package dev.anglewatcher.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.anglewatcher.screen.YaclConfigScreen;

/** Exposes the YACL config screen through Mod Menu's config button. */
public final class AngleWatcherModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return YaclConfigScreen::create;
	}
}
