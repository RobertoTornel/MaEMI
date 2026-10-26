package dev.nei.mixin;

import dev.nei.hook.NeiHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse input for the calculator window.
 *
 * <p>This is hooked on {@link Mouse} rather than on {@code Screen.mouseClicked} because those screen
 * methods are interface defaults on most screens, so an injection there silently does nothing — the
 * feature would simply not respond. {@code Mouse} is a concrete class on both loaders and is also
 * where EMI takes its own mouse input, so being here means the window sees the click first.</p>
 *
 * <p>The coordinates in this class are raw window pixels; they are converted to GUI space here, the
 * same way vanilla's own handlers do it.</p>
 */
@Mixin(Mouse.class)
public abstract class MouseMixin {
	@Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
	private void nei$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
		try {
			Screen screen = current();
			if (screen == null) {
				return;
			}
			double mouseX = scaledX();
			double mouseY = scaledY();
			boolean handled = action == 1
					? NeiHooks.mouseClicked(screen, mouseX, mouseY, button)
					: NeiHooks.mouseReleased(screen, mouseX, mouseY, button);
			if (handled) {
				ci.cancel();
			}
		} catch (Throwable ignored) {
			// Input must never be able to break the screen.
		}
	}

	@Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
	private void nei$onCursorPos(long window, double x, double y, CallbackInfo ci) {
		try {
			Screen screen = current();
			if (screen == null) {
				return;
			}
			if (NeiHooks.mouseDragged(screen, scaledX(), scaledY(), 0)) {
				ci.cancel();
			}
		} catch (Throwable ignored) {
			// A failed drag just leaves the window where it was.
		}
	}

	private static Screen current() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.currentScreen == null || client.getOverlay() != null) {
			return null;
		}
		return client.currentScreen;
	}

	private static double scaledX() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.mouse.getX() * client.getWindow().getScaledWidth() / client.getWindow().getWidth();
	}

	private static double scaledY() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.mouse.getY() * client.getWindow().getScaledHeight() / client.getWindow().getHeight();
	}
}
