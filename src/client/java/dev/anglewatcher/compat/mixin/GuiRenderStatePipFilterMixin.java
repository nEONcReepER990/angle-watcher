package dev.anglewatcher.compat.mixin;

import dev.isxander.yacl3.gui.YACLScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes Custom Block Highlight's live 3D block preview from foreign YACL config
 * screens (ours, and any other YACL-based mod).
 *
 * <p>CBH enqueues its preview as a {@link PictureInPictureRenderState} while wrapping
 * {@code YACLScreen.extractBackground} &mdash; outside YACL's own method body &mdash;
 * so it cannot be prevented at the wrapper level. It is filtered at the point where
 * all picture-in-picture states are collected for rendering. CBH's own config screen
 * keeps its preview: the filter only applies while a foreign YACL screen is open
 * (identified by the screen's title not being a plain {@code cbh.config.title}
 * translation key).</p>
 *
 * <p>The {@code Minecraft.screen()} check makes the filter transient: the very frame
 * another screen (including CBH's own) opens, its states pass through untouched. The
 * filter is additionally gated on CBH being installed, so it can never interfere with
 * picture-in-picture content from other sources.</p>
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStatePipFilterMixin {
	@Inject(
		method = "addPicturesInPictureState(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void anglewatcher$filterForeignBlockPreview(PictureInPictureRenderState state, CallbackInfo ci) {
		if (!FabricLoader.getInstance().isModLoaded("custom-block-highlight")) {
			return;
		}
		Gui gui = Minecraft.getInstance().gui;
		Screen screen = gui == null ? null : gui.screen();
		if (!(screen instanceof YACLScreen yaclScreen)) {
			return;
		}
		Component title = yaclScreen.config.title();
		if (title.getContents() instanceof TranslatableContents contents
			&& !contents.getKey().equals("cbh.config.title")) {
			ci.cancel();
		}
	}
}
