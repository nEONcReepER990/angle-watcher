package dev.anglewatcher;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The mod's icon assets (PNGs under {@code assets/anglewatcher/}) and a helper
 * for drawing them in GUIs.
 *
 * <ul>
 *   <li>{@link #MOD_ICON} &mdash; 128&times;128 master icon, referenced from
 *       {@code fabric.mod.json} so Mod Menu (and other launchers) show it.</li>
 *   <li>{@link #GUI_ICON} &mdash; 16&times;16 pixel icon for in-game screens.</li>
 * </ul>
 */
public final class ModIcons {
	/** 128&times;128 master icon ({@code assets/anglewatcher/icon.png}). */
	public static final Identifier MOD_ICON =
			Identifier.fromNamespaceAndPath(AngleWatcherClient.MOD_ID, "icon.png");

	/** 16&times;16 GUI icon ({@code assets/anglewatcher/textures/gui/icon16.png}). */
	public static final Identifier GUI_ICON =
			Identifier.fromNamespaceAndPath(AngleWatcherClient.MOD_ID, "textures/gui/icon16.png");

	private ModIcons() {
	}

	/** Draws the whole {@code size}&times;{@code size} texture at ({@code x}, {@code y}), untinted. */
	public static void draw(GuiGraphicsExtractor graphics, Identifier icon, int x, int y, int size) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0.0f, 0.0f, size, size, size, size, -1);
	}

	/** Draws the 16&times;16 GUI icon at ({@code x}, {@code y}). */
	public static void drawGuiIcon(GuiGraphicsExtractor graphics, int x, int y) {
		draw(graphics, GUI_ICON, x, y, 16);
	}
}
