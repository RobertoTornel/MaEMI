package dev.nei.util;

import com.google.gson.JsonElement;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/** Converts EMI stacks to and from the JSON used by subsets, bookmarks and hidden items. */
public final class StackIO {
	private StackIO() {
	}

	public static JsonElement write(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		try {
			return EmiIngredientSerializer.getSerialized(stack);
		} catch (Throwable t) {
			return null;
		}
	}

	public static EmiStack read(JsonElement element) {
		if (element == null) {
			return EmiStack.EMPTY;
		}
		try {
			EmiIngredient ingredient = EmiIngredientSerializer.getDeserialized(element);
			if (ingredient instanceof EmiStack stack) {
				return stack;
			}
			if (ingredient != null && !ingredient.getEmiStacks().isEmpty()) {
				return ingredient.getEmiStacks().get(0);
			}
		} catch (Throwable t) {
			// unknown stack type: ignore
		}
		return EmiStack.EMPTY;
	}

	/** A stable identity for hiding/deduplicating, independent of amount and of EMI serialization. */
	public static String key(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		Identifier id = stack.getId();
		NbtCompound nbt = stack.getNbt();
		return (id == null ? "unknown" : id.toString()) + (nbt == null ? "" : "|" + nbt);
	}
}
