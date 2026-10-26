package dev.nei.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
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
		net.minecraft.component.ComponentChanges changes = stack.getComponentChanges();
		return (id == null ? "unknown" : id.toString()) + (changes == null || changes.isEmpty() ? "" : "|" + changes);
	}

	/**
	 * NBT compounds are backed by an unordered map, so {@code NbtCompound.toString()} can order the
	 * same tags differently between two equal stacks. Sorting keys recursively keeps the identity
	 * stable and also keeps NBT/durability variants distinct.
	 */
	private static String canonical(NbtElement element) {
		if (element == null) {
			return "null";
		}
		if (element instanceof NbtCompound compound) {
			List<String> keys = new ArrayList<>(compound.getKeys());
			Collections.sort(keys);
			StringBuilder out = new StringBuilder("{");
			for (String key : keys) {
				if (out.length() > 1) {
					out.append(',');
				}
				// Prefix the key with its length so separators in a key cannot alias another key.
				out.append(key.length()).append(':').append(key).append('=');
				out.append(canonical(compound.get(key)));
			}
			return out.append('}').toString();
		}
		if (element instanceof NbtList list) {
			// The element type is part of list equality, including for empty lists.
			StringBuilder out = new StringBuilder("[").append(list.getHeldType()).append(':');
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					out.append(',');
				}
				out.append(canonical(list.get(i)));
			}
			return out.append(']').toString();
		}
		// Primitive and array tags have stable ordering; include the tag type to avoid collisions.
		return element.getType() + ":" + element;
	}
}
