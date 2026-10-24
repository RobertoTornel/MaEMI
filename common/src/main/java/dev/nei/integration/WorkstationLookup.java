package dev.nei.integration;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * NEI's "?" button for machine GUIs, generalised: EMI already knows which workstation stacks belong
 * to which recipe category, so we can answer "what does this block make?" for every mod that
 * registers a workstation, without any per-mod code.
 */
public final class WorkstationLookup {
	private WorkstationLookup() {
	}

	/** @return every category that lists {@code stack} as a workstation. */
	public static List<EmiRecipeCategory> categoriesFor(EmiStack stack) {
		List<EmiRecipeCategory> found = new ArrayList<>();
		if (stack == null || stack.isEmpty()) {
			return found;
		}
		for (EmiRecipeCategory category : EmiApi.getRecipeManager().getCategories()) {
			for (EmiIngredient station : EmiApi.getRecipeManager().getWorkstations(category)) {
				for (EmiStack candidate : station.getEmiStacks()) {
					if (candidate.isEqual(stack)) {
						found.add(category);
						break;
					}
				}
			}
		}
		return found;
	}

	@Nullable
	public static EmiRecipeCategory categoryFor(EmiStack stack) {
		List<EmiRecipeCategory> categories = categoriesFor(stack);
		return categories.isEmpty() ? null : categories.get(0);
	}

	/** Opens the recipe category of the block the player is currently looking at, if any. */
	public static boolean openForLookedAtBlock() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockHitResult hit = (BlockHitResult) client.crosshairTarget;
		ItemStack stack = new ItemStack(client.world.getBlockState(hit.getBlockPos()).getBlock().asItem());
		return openFor(EmiStack.of(stack));
	}

	/** Opens the recipes of the workstation the player is holding (NEI's "?" on a machine item). */
	public static boolean openForHeldItem() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return false;
		}
		ItemStack held = client.player.getMainHandStack();
		if (held.isEmpty()) {
			held = client.player.getOffHandStack();
		}
		return openFor(EmiStack.of(held));
	}

	public static boolean openFor(EmiStack stack) {
		EmiRecipeCategory category = categoryFor(stack);
		if (category == null) {
			return false;
		}
		EmiApi.displayRecipeCategory(category);
		return true;
	}

	public static String describe(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		return Registries.ITEM.getId(stack.getItemStack().getItem()).toString();
	}
}
