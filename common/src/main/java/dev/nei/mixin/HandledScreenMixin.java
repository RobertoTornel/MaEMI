package dev.nei.mixin;

import dev.nei.hook.NeiHooks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Renders the MaEMI panel after the container screen has finished drawing (so it sits above slots and
 * tooltips) and gives it the first shot at mouse/keyboard input.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void nei$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		NeiHooks.render(context, (HandledScreen<?>) (Object) this, mouseX, mouseY, delta);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void nei$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (NeiHooks.mouseClicked((HandledScreen<?>) (Object) this, mouseX, mouseY, button)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void nei$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (NeiHooks.keyPressed((HandledScreen<?>) (Object) this, keyCode, scanCode, modifiers)) {
			cir.setReturnValue(true);
		}
	}
}