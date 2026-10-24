package dev.nei.mixin;

import dev.nei.hook.NeiHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scroll events reach the screen through {@link Mouse#onMouseScroll}. {@code Screen.mouseScrolled}
 * is an interface default, so the input layer is the hook point that works on both loaders.
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void nei$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.currentScreen != null
				&& NeiHooks.mouseScrolled(client.currentScreen, client.mouse.getX(), client.mouse.getY(), vertical)) {
			ci.cancel();
		}
	}
}