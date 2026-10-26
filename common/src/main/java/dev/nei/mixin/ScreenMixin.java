package dev.nei.mixin;

import dev.nei.hook.NeiHooks;
import dev.nei.render.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws and feeds the calculator overlay on screens that are not container screens. Container screens
 * are handled by {@link HandledScreenMixin}, which hooks the point after the item grid; the
 * {@code instanceof} guard here is what keeps a single screen from being drawn twice.
 *
 * <p>The render hook is {@code Screen.render} on 1.19.2, because this version has no
 * {@code renderWithTooltip}: that method only appears in 1.19.3. Its tail is still the last point
 * inside the screen this branch can inject into, so the window is above the screen's own content;
 * only tooltips, which the game draws after {@code Screen.render} returns, land on top of it.</p>
 *
 * <p>Mouse input is not handled here: those methods are interface defaults, so {@link MouseMixin}
 * takes them instead. Only keys are routed from this mixin, which {@code Screen} does declare.</p>
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void nei$renderOverlay(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!((Object) this instanceof HandledScreen<?>)) {
			NeiHooks.render(DrawContext.of(matrices), (Screen) (Object) this, mouseX, mouseY, delta);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void nei$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof HandledScreen<?>)
				&& NeiHooks.keyPressed((Screen) (Object) this, keyCode, scanCode, modifiers)) {
			cir.setReturnValue(true);
		}
	}
}
