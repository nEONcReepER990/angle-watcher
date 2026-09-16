package dev.anglewatcher.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.tab.TabExt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Compatibility with Custom Block Highlight ("CBH").
 *
 * <p>CBH's {@code YACLScreenMixin} wraps the whole of
 * {@link YACLScreen#extractBackground} with MixinExtras' {@code @WrapMethod} and only
 * takes its dedicated path when the screen's config title is
 * {@code cbh.config.title}. On every <em>foreign</em> YACL screen &mdash; ours, and
 * any other YACL-based mod &mdash; it additionally:</p>
 * <ul>
 *   <li>re-draws the tab background (duplicating YACL's own),</li>
 *   <li>tracks mouse angles into its static fields,</li>
 *   <li>draws its live 3D block preview behind the panel
 *       (twice, as {@code PictureInPictureRenderState}), and</li>
 *   <li>re-invokes {@code Screen#extractBackground}, producing a second background
 *       blur request that hard-crashes on MC 26.2 ("Can only blur once per frame")
 *       whenever Menu Background Blurriness is >= 1.</li>
 * </ul>
 *
 * <p>This handler uses the same whole-method wrap at a higher priority, so it runs
 * <em>outside</em> CBH's. On CBH's own screen it calls through unchanged. On any
 * other YACL screen (and only when CBH is actually installed) it skips the rest of
 * the wrap chain and reproduces exactly what an unmodified
 * {@code YACLScreen.extractBackground} does &mdash; the vanilla screen background
 * followed by the active tab's background &mdash; so the screen renders normally
 * with no CBH overlay, no duplicate background and no duplicate blur. Without CBH
 * installed the behaviour is byte-for-byte equivalent to vanilla YACL.</p>
 */
@Mixin(value = YACLScreen.class, priority = 2000)
public abstract class YACLScreenMixin extends Screen {
	@Shadow(aliases = "config")
	public YetAnotherConfigLib config;

	@Shadow(aliases = "tabManager")
	public TabManager tabManager;

	private YACLScreenMixin(Component title) {
		super(title);
	}

	@WrapMethod(method = "extractBackground")
	private void anglewatcher$ownBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, Operation<Void> original) {
		if (!FabricLoader.getInstance().isModLoaded("custom-block-highlight")
			|| this.config.title().equals(Component.translatable("cbh.config.title"))) {
			// No CBH, or CBH's own screen: run the chain unchanged.
			original.call(graphics, mouseX, mouseY, partialTick);
			return;
		}

		// Foreign YACL screen with CBH installed: reproduce YACL's unmodified
		// background (vanilla screen background, then the active tab's background)
		// and skip the rest of the wrap chain entirely.
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		Tab currentTab = this.tabManager.getCurrentTab();
		if (currentTab instanceof TabExt tabExt) {
			tabExt.renderBackground(graphics);
		}
	}
}
