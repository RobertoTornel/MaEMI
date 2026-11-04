package dev.nei.mixin;

import dev.nei.command.RCommand;
import dev.nei.util.Messages;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catches {@code /r} before it leaves the client.
 *
 * <p>Registering a real command needs the server's dispatcher on Forge and the integrated server's
 * on Fabric, and a multiplayer server knows nothing about either -- so {@code /r} simply did not
 * exist where it was wanted. Intercepting the outgoing command makes it a client-side feature that
 * works on every server, which is the only place a reply helper belongs.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ChatCommandMixin {
	@Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
	private void nei$sendChatCommand(String command, CallbackInfo ci) {
		if (command == null) {
			return;
		}
		String trimmed = command.trim();
		if (!trimmed.equals("r") && !trimmed.startsWith("r ")) {
			return;
		}
		String message = trimmed.length() > 1 ? trimmed.substring(2).trim() : "";
		String[] args = message.isEmpty() ? new String[0] : new String[] { message };
		String key = RCommand.handle(args);
		if (key != null) {
			Messages.status(Text.translatable(key,
				RCommand.lastTarget() == null ? "?" : RCommand.lastTarget()));
		}
		ci.cancel();
	}
}
