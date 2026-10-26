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
 *
 * <p>Other mods (EMI among them) also hook this method and cancel it when they consume the character.
 * Our label is checked at the same point, so a rename can only receive characters while it is the
 * focused thing on screen; everything else falls through to EMI's search field untouched.</p>
 */
@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
	@Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
	private void nei$onChar(long window, int codepoint, int modifiers, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.currentScreen == null) {
			return;
		}
		// Only steal characters while the rename label is open; otherwise EMI's field owns typing.
		if (!NeiHooks.editingLabel()) {
			return;
		}
		boolean consumed = false;
		if (Character.charCount(codepoint) == 1) {
			consumed = NeiHooks.charTyped(client.currentScreen, (char) codepoint, modifiers);
		} else {
			for (char chr : Character.toChars(codepoint)) {
				consumed = NeiHooks.charTyped(client.currentScreen, chr, modifiers) || consumed;
			}
		}
		if (consumed) {
			ci.cancel();
		}
	}
}
