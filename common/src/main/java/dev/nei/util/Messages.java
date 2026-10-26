package dev.nei.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.text.LiteralText;

/** Feedback that is readable while a container screen covers the HUD. */
public final class Messages {
	private Messages() {
	}

	/**
	 * Chat entry (kept in history) plus the short-lived overlay line.
	 *
	 * <p>The overlay line is what survives the inventory being open; the chat entry is what survives
	 * looking away. Both are cheap, so both are always written.</p>
	 */
	public static void status(Text text) {
		if (text == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.inGameHud != null) {
			try {
				client.inGameHud.getChatHud().addMessage(text);
			} catch (Throwable ignored) {
				// A chat entry must never break the action that produced it.
			}
		}
		try {
			StatusLine.set(text.getString());
		} catch (Throwable ignored) {
			// The chat line still carries the information.
		}
	}

	public static void status(String key, Object... args) {
		status(new TranslatableText(key, args));
	}
}
