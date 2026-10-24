package dev.nei.mixin;

import dev.nei.hook.NeiHooks;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Character input reaches the screen through {@link Keyboard#onChar}, which is private and is the
 * last point before {@code Screen.charTyped} is called. Hook here instead of on {@code Screen}:
 * its {@code charTyped} is an interface default that Mixin cannot inject into.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
	@Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
	private void nei$onChar(long window, int codepoint, int modifiers, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.currentScreen != null
				&& NeiHooks.charTyped(client.currentScreen, (char) codepoint, modifiers)) {
			ci.cancel();
		}
	}
}
