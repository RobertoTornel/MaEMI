package dev.nei.mixin;

import dev.nei.hook.NeiHooks;
import dev.nei.render.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional panel support for screens that are not container screens. Container screens are handled
 * by HandledScreenMixin, which knows the GUI bounds and draws last.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void nei$render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY,
			float delta, CallbackInfo ci) {
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