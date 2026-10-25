package dev.nei.util;

import java.util.List;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Recognises tools, including GT tools.
 *
 * <p>GT tools are not vanilla damageable items: their durability, stats and behaviour live in the
 * {@code GT.Tool} NBT tag, so {@link ItemStack#isDamageable()} is false for them. Treating them as
 * ordinary ingredients makes the planner try to craft one hammer per recipe step and makes EMI
 * unable to match the damaged tool in your inventory against the plain tool a recipe asks for.
 */
public final class ToolIdentity {
	private static final List<String> TOOL_SUFFIXES = List.of(
		"_hammer", "_file", "_wrench", "_wire_cutter", "_screwdriver", "_saw", "_knife", "_mortar",
		"_plunger", "_crowbar", "_drill", "_chainsaw", "_scythe", "_hoe", "_spade", "_pickaxe",
		"_axe", "_sword", "_shovel", "_shears", "_tongs", "_rolling_pin", "_mallet", "_soft_mallet",
		"_buzzsaw", "_scoop", "_screwdriver", "_wire_cutter", "_saw", "_hammer"
	);

	private ToolIdentity() {
	}

	public static boolean isTool(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		ItemStack itemStack = stack.getItemStack();
		if (itemStack == null || itemStack.isEmpty()) {
			return false;
		}
		return isToolItem(itemStack.getItem()) || hasToolNbt(stack);
	}

	public static boolean isToolItem(Item item) {
		if (item == null) {
			return false;
		}
		ItemStack stack = new ItemStack(item);
		// Armour is damageable too, but two different chestplates must not become interchangeable.
		if (!stack.isEmpty() && stack.isDamageable()
				&& !(item instanceof net.minecraft.item.ArmorItem)
				&& !(item instanceof net.minecraft.item.ElytraItem)) {
			return true;
		}
		Identifier id = Registries.ITEM.getId(item);
		if (id == null || !"gtceu".equals(id.getNamespace())) {
			return false;
		}
		String path = id.getPath();
		for (String suffix : TOOL_SUFFIXES) {
			if (path.endsWith(suffix)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasToolNbt(EmiStack stack) {
		NbtCompound nbt = stack.getNbt();
		return nbt != null && nbt.contains("GT.Tool");
	}
}