package dev.nei.cheat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.Nei;
import dev.nei.NeiClient;
import dev.nei.config.NeiConfig;
import dev.nei.config.NeiConfigData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Server-visible actions NEI exposes. All of them are best-effort: they simply do nothing when the
 * player lacks the permission the action needs, or when the relevant part of the config is off.
 *
 * <p>Creative item creation is handled fully client-side through
 * {@link ClientPlayerInteractionManager#clickCreativeStack(ItemStack, int)}. Everything else is a
 * vanilla command, which means it only works when the player actually has permission level 2 - a
 * creative-mode player on a server without op permissions can still create items, but cannot run
 * {@code /time}, {@code /give} and friends.
 */
public final class Cheats {
	private static final int MAX_COMMAND_COUNT = 6400;

	/** The stack most recently hovered in the NEI panel, used by {@link #deleteHovered()}. */
	private static EmiStack hovered = EmiStack.EMPTY;

	private Cheats() {
	}

	public static boolean canCheat() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.player != null
				&& (client.player.isCreative() || client.player.hasPermissionLevel(2));
	}

	/** Remembers the stack under the cursor so the no-argument actions can act on it. */
	public static void setHovered(EmiStack stack) {
		hovered = stack == null ? EmiStack.EMPTY : stack;
	}

	/** Gives {@code count} of {@code stack}: creative slot click when possible, otherwise {@code /give}. */
	public static boolean give(EmiStack stack, int count) {
		if (stack == null || stack.isEmpty() || count <= 0) {
			return false;
		}
		NeiConfigData.Cheat cfg = NeiConfig.get().cheat;
		if (cfg == null || !cfg.enabled || !cfg.giveStacks) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null || !canCheat()) {
			return false;
		}
		ItemStack item;
		try {
			item = stack.getItemStack();
		} catch (Throwable t) {
			return false;
		}
		if (item == null || item.isEmpty()) {
			return false;
		}
		ItemStack copy = item.copy();
		int max = Math.max(1, copy.getMaxCount());
		copy.setCount(Math.max(1, Math.min(count, max)));

		if (client.player.isCreative() && client.interactionManager != null) {
			int slot = 36 + client.player.getInventory().selectedSlot;
			try {
				client.interactionManager.clickCreativeStack(copy, slot);
				return true;
			} catch (Throwable t) {
				Nei.LOG.warn("[NEI] creative give failed", t);
			}
		}
		if (!client.player.hasPermissionLevel(2)) {
			return false;
		}
		Identifier id = Registries.ITEM.getId(copy.getItem());
		if (id == null) {
			return false;
		}
		// Commands cannot carry NBT, so custom data is lost on this path by design.
		return send("give @s " + id + " " + Math.min(count, MAX_COMMAND_COUNT));
	}

	/** Backwards-compatible entry point used by the panel: a full stack or a single item. */
	public static boolean give(EmiStack stack, boolean fullStack) {
		if (fullStack) {
			int max = 1;
			try {
				ItemStack item = stack == null ? ItemStack.EMPTY : stack.getItemStack();
				if (item != null && !item.isEmpty()) {
					max = Math.max(1, item.getMaxCount());
				}
			} catch (Throwable ignored) {
				// fall through with a single item
			}
			return give(stack, max);
		}
		return give(stack, 1);
	}

	/**
	 * Deletes one of the hovered item. Vanilla has no client-side deletion packet, so this always
	 * goes through {@code /clear}; it is therefore refused without permission level 2 even in
	 * creative mode.
	 */
	public static boolean deleteHovered(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		NeiConfigData.Cheat cfg = NeiConfig.get().cheat;
		if (cfg == null || !cfg.enabled || !cfg.deleteItems || !canUseCommands()) {
			return false;
		}
		Identifier id = stack.getId();
		if (id == null) {
			return false;
		}
		return send("clear @s " + id + " 1");
	}

	public static boolean deleteHovered() {
		if (!hovered.isEmpty()) {
			return deleteHovered(hovered);
		}
		ClientPlayerEntity player = player();
		if (player == null) {
			return false;
		}
		NeiConfigData.Cheat cfg = NeiConfig.get().cheat;
		if (cfg == null || !cfg.deleteItems || !canUseCommands()) {
			return false;
		}
		ItemStack held = player.getMainHandStack();
		if (held == null || held.isEmpty()) {
			return false;
		}
		Identifier id = Registries.ITEM.getId(held.getItem());
		return id != null && send("clear @s " + id + " 1");
	}

	public static void heal() {
		if (!utilityAllowed()) {
			return;
		}
		send("effect give @s minecraft:instant_health 1 10");
		send("effect give @s minecraft:saturation 1 10");
	}

	public static void setGamemode(String mode) {
		if (!utilityAllowed() || mode == null) {
			return;
		}
		String value = mode.trim().toLowerCase(Locale.ROOT);
		if (!value.equals("survival") && !value.equals("creative")
				&& !value.equals("adventure") && !value.equals("spectator")) {
			return;
		}
		send("gamemode " + value);
	}

	public static void setTime(String time) {
		if (!utilityAllowed() || time == null) {
			return;
		}
		String value = time.trim().toLowerCase(Locale.ROOT);
		if (!isTime(value)) {
			return;
		}
		send("time set " + value);
	}

	public static void setWeather(String weather) {
		if (!utilityAllowed() || weather == null) {
			return;
		}
		String value = weather.trim().toLowerCase(Locale.ROOT);
		if (!value.equals("clear") && !value.equals("rain") && !value.equals("thunder")) {
			return;
		}
		send("weather " + value);
	}

	/** Applies an enchantment that suits the item currently held in the main hand. */
	public static void enchantHeld() {
		if (!utilityAllowed()) {
			return;
		}
		ClientPlayerEntity player = player();
		if (player == null) {
			return;
		}
		ItemStack held = player.getMainHandStack();
		if (held == null || held.isEmpty()) {
			return;
		}
		Identifier id = Registries.ITEM.getId(held.getItem());
		if (id == null) {
			return;
		}
		String path = id.getPath();
		String enchantment;
		if (path.contains("sword") || path.contains("axe") || path.contains("trident")) {
			enchantment = "sharpness";
		} else if (path.contains("pickaxe") || path.contains("shovel") || path.contains("hoe")
				|| path.contains("shears") || path.contains("drill")) {
			enchantment = "efficiency";
		} else if (path.contains("bow") || path.contains("crossbow")) {
			enchantment = "power";
		} else if (path.contains("helmet") || path.contains("chestplate")
				|| path.contains("leggings") || path.contains("boots")) {
			enchantment = "protection";
		} else if (path.contains("fishing_rod")) {
			enchantment = "luck_of_the_sea";
		} else {
			enchantment = "unbreaking";
		}
		send("enchant @s minecraft:" + enchantment + " 5");
	}

	/** Spawns one entity of {@code entityId} ({@code minecraft:zombie}, {@code nametag} namespace required). */
	public static void spawnMob(String entityId) {
		if (!utilityAllowed() || entityId == null) {
			return;
		}
		String value = entityId.trim().toLowerCase(Locale.ROOT);
		if (!isNamespacedId(value)) {
			return;
		}
		send("summon " + value + " ~ ~1 ~");
	}

	/** The utility buttons rendered by the panel, in display order. */
	public static List<CheatAction> actions() {
		List<CheatAction> actions = new ArrayList<>();
		actions.add(new CheatAction("heal", Text.translatable("nei.utility.heal"), true, screen -> heal()));
		actions.add(new CheatAction("time", Text.translatable("nei.utility.time"), true, screen -> setTime(nextTime())));
		actions.add(new CheatAction("weather", Text.translatable("nei.utility.weather"), true, screen -> setWeather(nextWeather())));
		actions.add(new CheatAction("gamemode", Text.translatable("nei.utility.gamemode"), true, screen -> setGamemode(nextGamemode())));
		actions.add(new CheatAction("trash", Text.translatable("nei.utility.trash"), true, screen -> deleteHovered()));
		actions.add(new CheatAction("enchant", Text.translatable("nei.cheat.enchant"), true, screen -> enchantHeld()));
		actions.add(new CheatAction("spawn_mob", Text.translatable("nei.cheat.spawn_mob"), true, screen -> spawnMob("minecraft:pig")));
		return List.copyOf(actions);
	}

	public record CheatAction(String id, Text label, boolean requiresCheat, Consumer<Screen> action) {
	}

	private static String nextTime() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) {
			return "day";
		}
		long time = Math.floorMod(client.world.getTimeOfDay(), 24000L);
		if (time < 6000L) {
			return "noon";
		}
		if (time < 12000L) {
			return "night";
		}
		if (time < 18000L) {
			return "midnight";
		}
		return "day";
	}

	private static String nextWeather() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) {
			return "clear";
		}
		if (client.world.isThundering()) {
			return "clear";
		}
		if (client.world.isRaining()) {
			return "thunder";
		}
		return "rain";
	}

	private static String nextGamemode() {
		ClientPlayerEntity player = player();
		if (player == null) {
			return "creative";
		}
		if (player.isSpectator()) {
			return "creative";
		}
		if (player.isCreative()) {
			return "survival";
		}
		return "creative";
	}

	private static boolean isTime(String value) {
		return value.equals("day") || value.equals("noon") || value.equals("night")
				|| value.equals("midnight") || value.matches("-?\\d{1,9}");
	}

	private static boolean isNamespacedId(String value) {
		return value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
	}

	private static boolean cheatEnabled() {
		NeiConfigData.Cheat cfg = NeiConfig.get().cheat;
		return cfg != null && cfg.enabled;
	}

	private static boolean canUseCommands() {
		ClientPlayerEntity player = player();
		return cheatEnabled() && player != null && player.hasPermissionLevel(2);
	}

	private static boolean utilityAllowed() {
		NeiConfigData.Cheat cfg = NeiConfig.get().cheat;
		return cfg != null && cfg.enabled && cfg.utilityButtons && canUseCommands();
	}

	private static ClientPlayerEntity player() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client == null ? null : client.player;
	}

	private static boolean send(String command) {
		if (command == null || command.isEmpty() || !NeiClient.isInitialised()) {
			return false;
		}
		try {
			NeiClient.platform().sendCommand(command);
			return true;
		} catch (Throwable t) {
			Nei.LOG.warn("[NEI] could not send command {}", command, t);
			return false;
		}
	}
}
