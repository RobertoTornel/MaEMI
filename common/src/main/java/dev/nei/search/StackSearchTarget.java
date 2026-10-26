package dev.nei.search;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Adapts an EMI stack to the search language. Values are computed lazily because a panel page may
 * contain hundreds of stacks and a query usually only touches one field.
 */
public final class StackSearchTarget implements SearchTarget {
	private final EmiStack stack;
	private String name;
	private String identifier;
	private List<String> tooltip;
	private List<String> tags;

	public StackSearchTarget(EmiStack stack) {
		this.stack = stack;
	}

	@Override
	public String name() {
		if (name == null) {
			Text text = stack.getName();
			name = text == null ? "" : text.getString();
		}
		return name;
	}

	@Override
	public String modId() {
		Identifier id = stack.getId();
		return id == null ? "" : id.getNamespace();
	}

	@Override
	public String modName() {
		return modId();
	}

	@Override
	public String identifier() {
		if (identifier == null) {
			Identifier id = stack.getId();
			identifier = id == null ? "" : id.toString();
		}
		return identifier;
	}

	@Override
	public List<String> tooltip() {
		if (tooltip == null) {
			tooltip = new ArrayList<>();
			for (Text line : stack.getTooltipText()) {
				tooltip.add(line.getString());
			}
		}
		return tooltip;
	}

	@Override
	public List<String> tags() {
		if (tags == null) {
			tags = new ArrayList<>();
			ItemStack item = stack.getItemStack();
			if (item != null && !item.isEmpty() && MinecraftClient.getInstance().world != null) {
				item.streamTags().forEach(tag -> tags.add(tag.id().toString()));
			}
		}
		return tags;
	}
}
