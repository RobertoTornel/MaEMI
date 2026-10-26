package dev.nei.mixin;

import dev.nei.hook.NeiHooks;
import dev.nei.render.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws the calculator overlay after a container screen has finished drawing, and routes the keys
 * that belong to it.
 *
 * <p>Mouse input is not handled here: {@code HandledScreen.mouseClicked} exists but its position is
 * raw pixels and EMI already hooks it, so {@link MouseMixin} owns all pointer input uniformly. Keys
 * are routed from here because container screens declare {@code keyPressed}.</p>
 *
 * <p>{@code HandledScreen.render} is the right tail: it draws the background, then the slot grid
 * through {@code Screen.render}, then the items, {@code drawForeground}, the cursor stack, and the
 * hovered tooltip. EMI's Fabric-side hook injects immediately after the {@code drawForeground} call,
 * which is before the cursor stack and tooltip, so {@code TAIL} here is above both the item grid and
 * EMI's sidebars. On Forge, EMI draws from {@code ScreenEvent.Render.Post}, which fires outside this
 * method entirely, so the overlay is below EMI's sidebar there; the ModMenu-visible behaviour is
 * otherwise identical and the container case — the one the feature is for — is covered on both.</p>
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void nei$render(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		NeiHooks.renderHandled(DrawContext.of(matrices), (HandledScreen<?>) (Object) this, mouseX, mouseY);
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void nei$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (NeiHooks.keyPressed((HandledScreen<?>) (Object) this, keyCode, scanCode, modifiers)) {
			cir.setReturnValue(true);
		}
	}
}
