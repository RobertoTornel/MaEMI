package dev.nei.mixin;

import dev.nei.hook.NeiHooks;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code Screen} inherits scrolling and character input from {@link ParentElement}'s default
 * methods, and {@code HandledScreen} does not override them, so this is the only place where the
 * NEI panel can see those events without touching every screen class.
 */
@Mixin(ParentElement.class)
public interface ParentElementMixin {
	@Inject(method = "mouseScrolled(DDD)Z", at = @At("HEAD"), cancellable = true)
	private void nei$mouseScrolled(double mouseX, double mouseY, double amount, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Screen screen && NeiHooks.mouseScrolled(screen, mouseX, mouseY, amount)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "charTyped(CI)Z", at = @At("HEAD"), cancellable = true)
	private void nei$charTyped(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Screen screen && NeiHooks.charTyped(screen, chr, modifiers)) {
			cir.setReturnValue(true);
		}
	}
}
