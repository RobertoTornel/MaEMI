package dev.nei.util;

import dev.nei.panel.NeiPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Feedback that is readable while a container screen covers the HUD. */
public final class Messages {
	private Messages() {
	}

	/** Chat entry (kept in history) plus a short overlay line drawn by the panel. */
	public static void status(Text text) {
		if (text == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null && client.inGameHud != null) {
			client.inGameHud.getChatHud().addMessage(text);
		}
		try {
			NeiPanel.get().state().setStatus(text.getString());
		} catch (Throwable ignored) {
			// panel not ready: the chat line still carries the information
		}
	}

	public static void status(String key, Object... args) {
		status(new net.minecraft.text.TranslatableText(key, args));
	}
}
