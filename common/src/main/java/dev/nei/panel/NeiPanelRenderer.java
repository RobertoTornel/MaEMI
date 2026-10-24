package dev.nei.panel;

import java.util.List;

import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Draws the NEI item panel: frame, search field, item grid, subset tabs, page label and help button.
 * Everything is read-only and null-safe; a missing client, text renderer or EMI index just means the
 * panel draws nothing instead of crashing.
 */
public final class NeiPanelRenderer {
	private static final int BACKGROUND = 0xE0101010;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int INNER = 0xFF1E1E1E;
	private static final int INNER_BORDER = 0xFF3A3A3A;
	private static final int TEXT = 0xFFE0E0E0;
	private static final int TEXT_DIM = 0xFF8A8A8A;
	private static final int TAB_BACKGROUND = 0xFF1A1A1A;
	private static final int TAB_SELECTED = 0xFF3C3C3C;
	private static final int HOVER = 0x60FFFFFF;

	private NeiPanelRenderer() {
	}

	public static void render(DrawContext context, Screen screen, NeiPanelLayout layout, NeiPanelState state,
			List<EmiStack> stacks, int mouseX, int mouseY, float delta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.textRenderer == null || layout == null || state == null || context == null) {
			return;
		}
		TextRenderer textRenderer = client.textRenderer;

		drawFrame(context, layout);
		drawItems(context, client, textRenderer, layout, state, stacks, mouseX, mouseY);
		drawHeader(context, textRenderer, layout, state, mouseX, mouseY);
		drawTabs(context, textRenderer, layout, state);
		drawPageLabel(context, textRenderer, layout);
		drawHelp(context, textRenderer, layout, mouseX, mouseY);

		if (layout.helpInside(mouseX, mouseY)) {
			context.drawTooltip(textRenderer, List.of(Text.translatable("nei.panel.help")), mouseX, mouseY);
		}
		drawHoveredTooltip(context, textRenderer, layout, stacks, mouseX, mouseY);
	}

	private static void drawHoveredTooltip(DrawContext context, TextRenderer textRenderer, NeiPanelLayout layout,
			List<EmiStack> stacks, int mouseX, int mouseY) {
		if (stacks == null || stacks.isEmpty()) {
			return;
		}
		int hovered = layout.slotAt(mouseX, mouseY);
		if (hovered < 0) {
			return;
		}
		int index = layout.firstItemIndex(NeiPanel.get().state()) + hovered;
		if (index < 0 || index >= stacks.size()) {
			return;
		}
		NeiPanel.get().state().setHoveredStack(stacks.get(index));
		List<Text> lines = stacks.get(index).getTooltipText();
		if (lines != null && !lines.isEmpty()) {
			context.drawTooltip(textRenderer, lines, mouseX, mouseY);
		}
	}

	private static void drawFrame(DrawContext context, NeiPanelLayout layout) {
		context.fill(layout.panelX, layout.panelY,
				layout.panelX + layout.panelWidth, layout.panelY + layout.panelHeight, BACKGROUND);
		context.fill(layout.panelX, layout.panelY,
				layout.panelX + layout.panelWidth, layout.panelY + 1, BORDER);
		context.fill(layout.panelX, layout.panelY + layout.panelHeight - 1,
				layout.panelX + layout.panelWidth, layout.panelY + layout.panelHeight, BORDER);
		context.fill(layout.panelX, layout.panelY,
				layout.panelX + 1, layout.panelY + layout.panelHeight, BORDER);
		context.fill(layout.panelX + layout.panelWidth - 1, layout.panelY,
				layout.panelX + layout.panelWidth, layout.panelY + layout.panelHeight, BORDER);
		context.fill(layout.panelX + 1, layout.gridY - 1,
				layout.panelX + layout.panelWidth - 1, layout.gridY, INNER_BORDER);
		if (layout.showTabs) {
			int footerTop = layout.panelY + layout.panelHeight - NeiPanelLayout.FOOTER;
			context.fill(layout.panelX + 1, footerTop,
					layout.panelX + layout.panelWidth - 1, footerTop + 1, INNER_BORDER);
		}
	}

	private static void drawItems(DrawContext context, MinecraftClient client, TextRenderer textRenderer,
			NeiPanelLayout layout, NeiPanelState state, List<EmiStack> stacks, int mouseX, int mouseY) {
		if (stacks == null || stacks.isEmpty()) {
			String message = Text.translatable("nei.panel.empty").getString();
			int width = textRenderer.getWidth(message);
			context.drawText(textRenderer, message,
					layout.gridX + (layout.columns * NeiPanelLayout.SLOT - width) / 2,
					layout.gridY + (layout.rows * NeiPanelLayout.SLOT) / 2 - 4, TEXT_DIM, false);
			return;
		}

		int firstIndex = layout.firstItemIndex(state);
		boolean finished = false;
		for (int row = 0; row < layout.rows && !finished; row++) {
			for (int column = 0; column < layout.columns; column++) {
				int index = firstIndex + row * layout.columns + column;
				if (index >= stacks.size()) {
					finished = true;
					break;
				}
				EmiStack stack = stacks.get(index);
				ItemStack itemStack = toItemStack(stack);
				if (itemStack.isEmpty()) {
					continue;
				}
				int x = layout.slotX(column);
				int y = layout.slotY(row);
				if (layout.showCounts) {
					context.drawItemInSlot(textRenderer, itemStack, x, y);
				} else {
					context.drawItem(itemStack, x, y);
				}
			}
		}

		int hovered = layout.slotAt(mouseX, mouseY);
		if (hovered >= 0) {
			int index = firstIndex + hovered;
			if (index >= 0 && index < stacks.size()) {
				int column = hovered % layout.columns;
				int row = hovered / layout.columns;
				int x = layout.slotX(column);
				int y = layout.slotY(row);
				context.fill(x, y, x + 16, y + 16, HOVER);
			}
		}
	}

	private static void drawHeader(DrawContext context, TextRenderer textRenderer, NeiPanelLayout layout,
			NeiPanelState state, int mouseX, int mouseY) {
		if (!layout.showSearch) {
			return;
		}
		context.fill(layout.searchX, layout.searchY,
				layout.searchX + layout.searchWidth, layout.searchY + layout.searchHeight, INNER);
		context.fill(layout.searchX, layout.searchY,
				layout.searchX + layout.searchWidth, layout.searchY + 1, INNER_BORDER);
		context.fill(layout.searchX, layout.searchY + layout.searchHeight - 1,
				layout.searchX + layout.searchWidth, layout.searchY + layout.searchHeight, INNER_BORDER);

		String query = state.search();
		int textX = layout.searchX + 3;
		int textY = layout.searchY + 3;
		if (query.isEmpty()) {
			context.drawText(textRenderer, Text.translatable("nei.panel.search").getString(),
					textX, textY, TEXT_DIM, false);
		} else {
			context.drawText(textRenderer, query, textX, textY, TEXT, false);
		}

		if (state.isSearchFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
			int caretX = textX + (query.isEmpty() ? 0 : textRenderer.getWidth(query)) + 1;
			int limit = layout.searchX + layout.searchWidth - 3;
			if (caretX > limit) {
				caretX = limit;
			}
			context.fill(caretX, layout.searchY + 3, caretX + 1, layout.searchY + 12, TEXT);
		}
	}

	private static void drawTabs(DrawContext context, TextRenderer textRenderer, NeiPanelLayout layout,
			NeiPanelState state) {
		if (layout.tabs.isEmpty()) {
			return;
		}
		String selected = state.selectedSubset();
		for (NeiPanelLayout.Tab tab : layout.tabs) {
			boolean active = java.util.Objects.equals(tab.subset, selected);
			context.fill(tab.x, tab.y, tab.x + tab.width, tab.y + tab.height,
					active ? TAB_SELECTED : TAB_BACKGROUND);
			context.fill(tab.x, tab.y + tab.height - 1, tab.x + tab.width, tab.y + tab.height,
					active ? TEXT : INNER_BORDER);
			int textWidth = textRenderer.getWidth(tab.label);
			context.drawText(textRenderer, tab.label,
					tab.x + (tab.width - textWidth) / 2, tab.y + 3, active ? TEXT : TEXT_DIM, false);
		}
	}

	private static void drawPageLabel(DrawContext context, TextRenderer textRenderer, NeiPanelLayout layout) {
		if (layout.pageX < layout.panelX + 2 || layout.pageX + layout.pageWidth > layout.panelX + layout.panelWidth - 1) {
			return;
		}
		context.drawText(textRenderer, layout.pageLabel, layout.pageX, layout.pageY, TEXT_DIM, false);
	}

	private static void drawHelp(DrawContext context, TextRenderer textRenderer, NeiPanelLayout layout,
			int mouseX, int mouseY) {
		boolean hovered = layout.helpInside(mouseX, mouseY);
		int x = layout.helpX;
		int y = layout.helpY;
		context.fill(x, y, x + NeiPanelLayout.HELP_SIZE, y + NeiPanelLayout.HELP_SIZE,
				hovered ? TAB_SELECTED : TAB_BACKGROUND);
		context.fill(x, y, x + NeiPanelLayout.HELP_SIZE, y + 1, INNER_BORDER);
		context.drawText(textRenderer, "?", x + 3, y + 1, hovered ? TEXT : TEXT_DIM, false);
	}

	private static ItemStack toItemStack(EmiStack stack) {
		try {
			if (stack == null || stack.isEmpty()) {
				return ItemStack.EMPTY;
			}
			ItemStack itemStack = stack.getItemStack();
			if (itemStack == null || itemStack.isEmpty()) {
				return ItemStack.EMPTY;
			}
			itemStack = itemStack.copy();
			long amount = stack.getAmount();
			if (amount > 1L) {
				itemStack.setCount((int) Math.min(Integer.MAX_VALUE, amount));
			}
			return itemStack;
		} catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}
}