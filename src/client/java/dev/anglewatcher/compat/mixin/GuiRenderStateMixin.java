package dev.anglewatcher.compat.mixin;

import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 26.2 allows at most one screen-background blur request per frame:
 * a second call to {@link GuiRenderState#blurBeforeThisStratum} throws
 * {@code IllegalStateException: Can only blur once per frame}.
 *
 * <p>Custom Block Highlight's {@code YACLScreenMixin} re-invokes
 * {@code Screen#extractBackground} on <em>every</em> YACL screen (to draw its live
 * block preview behind the panel), producing a second blur request whenever
 * "Menu Background Blurriness" is >= 1. That hard-crashes the first render frame of
 * any foreign YACL config screen &mdash; including anglewatcher's.</p>
 *
 * <p>Swallowing duplicate requests is safe: each frame extracts GUI state into a
 * fresh {@code GuiRenderState} whose {@code firstStratumAfterBlur} starts at
 * {@link Integer#MAX_VALUE}, so the "already blurred this frame" check can never
 * leak across frames, and the first (vanilla) blur request still applies normally.
 * When Custom Block Highlight is not installed there is simply never a second
 * request in a frame, so this mixin is a no-op.</p>
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin {
	@Shadow
	private int firstStratumAfterBlur;

	@Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), cancellable = true)
	private void anglewatcher$swallowDuplicateBlur(CallbackInfo ci) {
		if (this.firstStratumAfterBlur != Integer.MAX_VALUE) {
			ci.cancel();
		}
	}
}
