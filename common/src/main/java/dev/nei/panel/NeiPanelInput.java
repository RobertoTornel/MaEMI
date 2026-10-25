package dev.nei.panel;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.emi.emi.api.stack.EmiStack;
import dev.nei.bookmark.Bookmarks;
import dev.nei.cheat.Cheats;
import dev.nei.config.NeiConfig;
import dev.nei.item.HideList;
import dev.nei.item.ItemSubset;
import dev.nei.item.ItemSubsetList;
import dev.nei.integration.WorkstationLookup;
import dev.nei.recipe.RecipeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Mouse and keyboard handling for the MaEMI item panel. Left click gives an item (in cheat mode), right
 * click bookmarks it, middle click asks EMI to show its recipes and shift+left click hides it.
 * Recipe and use lookups on R/U are left to EMI.
 */
public final class NeiPanelInput {
	private static final long DOUBLE_CLICK_MS = 300L;

	private NeiPanelInput() {
	}

	public static boolean mouseClicked(Screen screen, NeiPanelLayout layout, NeiPanelState state,
			List<EmiStack> stacks, double mouseX, double mouseY, int button) {
		if (layout == null || state == null || !layout.inside(mouseX, mouseY)) {
			return false;
		}

		if (layout.searchInside(mouseX, mouseY)) {
			handleSearchClick(state);
			return true;
		}

		if (layout.helpInside(mouseX, mouseY)) {
			if (button == 1) {
				MinecraftClient client = MinecraftClient.getInstance();
				client.setScreen(new dev.nei.cheat.UtilityScreen(screen));
			} else if (!WorkstationLookup.openForHeldItem() && !WorkstationLookup.openForLookedAtBlock()) {
				status("nei.panel.help");
			}
			return true;
		}

		NeiPanelLayout.Tab tab = layout.tabAt(mouseX, mouseY);
		if (tab != null) {
			state.setSearchFocused(false);
			if (button == 1) {
				if (Screen.hasShiftDown() && tab.subset != null
						&& !NeiPanelState.BOOKMARKS.equals(tab.subset)) {
					// Shift+right-click edits a user subset instead of selecting or creating one.
					MinecraftClient client = MinecraftClient.getInstance();
					client.setScreen(new SubsetEditScreen(screen, tab.subset));
					return true;
				}
				// Right-clicking a tab creates a new subset, like MaEMI's "..." button.
				ItemSubset created = ItemSubsetList.get().create("Subset");
				state.setSelectedSubset(created.name());
				status("nei.panel.subset_created", created.name());
			} else {
				state.setSelectedSubset(tab.subset);
			}
			state.resetPage();
			ItemPanelSource.get().invalidate();
			return true;
		}

		int local = layout.slotAt(mouseX, mouseY);
		if (local >= 0 && stacks != null) {
			int index = layout.firstItemIndex(state) + local;
			if (index >= 0 && index < stacks.size()) {
				EmiStack stack = stacks.get(index);
				if (stack != null && !stack.isEmpty()) {
					handleSlotClick(state, stack, button);
				}
			}
		}
		// Any click on the panel is consumed so it cannot fall through and drop the held stack.
		return true;
	}

	private static void handleSearchClick(NeiPanelState state) {
		long now = System.currentTimeMillis();
		if (state.isSearchFocused() && now - state.lastSearchClick() < DOUBLE_CLICK_MS) {
			state.setSearch("");
			state.resetPage();
			ItemPanelSource.get().invalidate();
			state.markSearchClick(0L);
			return;
		}
		state.setSearchFocused(true);
		state.markSearchClick(now);
	}

	private static void handleSlotClick(NeiPanelState state, EmiStack stack, int button) {
		if (button == 0) {
			if (Screen.hasShiftDown()) {
				toggleHidden(stack);
				return;
			}
			give(stack);
			return;
		}
		if (button == 1) {
			addToBookmarksOrSubset(state, stack);
			return;
		}
		if (button == 2) {
			displayRecipes(stack);
		}
	}

	private static void give(EmiStack stack) {
		if (!NeiConfig.get().cheat.enabled) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) {
			return;
		}
		if (!Cheats.canCheat()) {
			return;
		}
		boolean fullStack = NeiConfig.get().cheat.giveStacks;
		try {
			Cheats.give(stack, fullStack);
		} catch (Throwable ignored) {
			// Cheating is best effort: a refused give must not take the screen down.
		}
	}

	private static void addToBookmarksOrSubset(NeiPanelState state, EmiStack stack) {
		try {
			String subset = state.selectedSubset();
			if (subset != null && !NeiPanelState.BOOKMARKS.equals(subset)) {
				ItemSubset target = ItemSubsetList.get().byName(subset);
				if (target != null && !target.builtin()) {
					boolean added = target.add(stack);
					if (added) {
						ItemSubsetList.get().saveIfDirty();
					}
					status(added ? "nei.panel.subset_added_to" : "nei.panel.already_in_subset", target.name());
					ItemPanelSource.get().invalidate();
					return;
				}
			}
			boolean added = false;
			try {
				added = Bookmarks.get().active() != null && !Bookmarks.get().active().contains(stack);
			} catch (Throwable ignored) {
			}
			Bookmarks.get().add(stack);
			status(added ? "nei.status.bookmarked" : "nei.panel.already_bookmarked");
			ItemPanelSource.get().invalidate();
		} catch (Throwable ignored) {
		}
	}

	private static void toggleHidden(EmiStack stack) {
		try {
			boolean hidden = HideList.get().toggle(stack);
			status(hidden ? "nei.status.hidden" : "nei.status.unhidden");
			ItemPanelSource.get().invalidate();
		} catch (Throwable ignored) {
		}
	}

	/** EMI owns recipe display; this is the same wrapper MaEMI's own tooltips use. */
	private static void displayRecipes(EmiStack stack) {
		try {
			RecipeRegistry.displayRecipes(stack);
		} catch (Throwable ignored) {
		}
	}

	public static boolean mouseScrolled(Screen screen, NeiPanelLayout layout, NeiPanelState state,
			List<EmiStack> stacks, double mouseX, double mouseY, double amount) {
		if (layout == null || state == null || !layout.inside(mouseX, mouseY) || amount == 0.0D) {
			return false;
		}
		int total = stacks == null ? 0 : stacks.size();
		int direction = amount > 0.0D ? -1 : 1;
		int target = state.scrollRow() + direction;
		int max = layout.maxScrollRow(total);
		state.setScrollRow(Math.max(0, Math.min(max, target)));
		return true;
	}

	public static boolean keyPressed(Screen screen, NeiPanelLayout layout, NeiPanelState state,
			int keyCode, int scanCode, int modifiers) {
		if (state == null) {
			return false;
		}
		if (!state.isSearchFocused()) {
			EmiStack hovered = state.hoveredStack();
			if (keyCode == GLFW.GLFW_KEY_R && !hovered.isEmpty()) {
				RecipeRegistry.displayRecipes(hovered);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_U && !hovered.isEmpty()) {
				RecipeRegistry.displayUses(hovered);
				return true;
			}
			return false;
		}
		switch (keyCode) {
			case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				state.setSearchFocused(false);
				return true;
			}
			case GLFW.GLFW_KEY_BACKSPACE -> {
				state.backspaceSearch();
				ItemPanelSource.get().invalidate();
				return true;
			}
			case GLFW.GLFW_KEY_DELETE -> {
				state.setSearch("");
				ItemPanelSource.get().invalidate();
				return true;
			}
			default -> {
				// While the search field owns the keyboard, nothing else may trigger a key bind or
				// close the screen; the character itself arrives through charTyped.
				return true;
			}
		}
	}

	public static boolean charTyped(Screen screen, NeiPanelState state, char chr, int modifiers) {
		if (state == null || !state.isSearchFocused()) {
			return false;
		}
		if (Character.isISOControl(chr)) {
			return false;
		}
		state.appendSearch(chr);
		ItemPanelSource.get().invalidate();
		return true;
	}

	private static void status(String key, Object... args) {
		dev.nei.util.Messages.status(key, args);
	}
}