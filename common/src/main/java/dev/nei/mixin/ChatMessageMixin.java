package dev.nei.mixin;

import java.util.UUID;

import dev.nei.command.RCommand;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tracks incoming private messages so {@code /r} knows who to reply to.
 *
 * <p>Private messages arrive as {@link ChatMessageS2CPacket} with a sender UUID. We resolve the
 * UUID to a player name via {@link ClientPlayNetworkHandler#getPlayerListEntry(UUID)} and record it
 * through {@link RCommand#notePrivateMessage(String)}.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ChatMessageMixin {
	@Inject(method = "onChatMessage", at = @At("HEAD"))
	private void nei$onChatMessage(ChatMessageS2CPacket packet, CallbackInfo ci) {
		UUID sender = packet.sender();
		if (sender == null) {
			return;
		}
		PlayerListEntry entry = ((ClientPlayNetworkHandler) (Object) this).getPlayerListEntry(sender);
		if (entry == null) {
			return;
		}
		String name = entry.getProfile().getName();
		if (name != null && !name.isEmpty()) {
			RCommand.notePrivateMessage(name);
		}
	}
}
