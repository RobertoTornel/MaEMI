package dev.nei.cheat;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.MinecraftClient;

/**
 * Server-visible actions NEI exposes. All of them are best-effort: they simply do nothing when the
 * player lacks the permission the action needs.
 */
public final class Cheats {
	private Cheats() {
	}

	public static boolean canCheat() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && (client.player.isCreative() || client.player.hasPermissionLevel(2));
	}

	public static boolean give(EmiStack stack, boolean fullStack) {
		return false;
	}

	public static void deleteHovered() {
	}

	public static void heal() {
	}

	public static void setGamemode(String mode) {
	}

	public static void setTime(String time) {
	}

	public static void setWeather(String weather) {
	}
}
