package dev.nei.autocraft;

import java.util.ArrayDeque;
import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.nei.Nei;
import dev.nei.util.StackIO;
import dev.nei.util.HoveredStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * Runs a {@link CraftPlan} against the open screen. MaEMI's autocrafting is deliberately
 * screen-driven: it uses the same fill path as EMI's own craft button, so it obeys the screen
 * handler's rules instead of injecting items, and it stops as soon as a step fails.
 */
public final class AutoCraftService {
	private static final int MAX_FILL_FAILURES = 3;

	private static CraftPlan plan;
	private static int stepIndex;
	private static int delay;
	private static int failures;
	// AE2's EMI handler only transfers a recipe into the terminal's grid; it does not take the
	// output. These fields drive one transfer and one output click per craft for AE2 screens.
	private static int ae2Step = -1;
	private static int ae2Remaining;
	private static boolean ae2TakeOutput;
	private static int ae2OutputWaits;

	private AutoCraftService() {
	}

	public static void requestCraftHovered() {
		requestCraftHovered(1);
	}

	public static void requestCraftHovered(int amount) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		if (isBoMScreen(client) && craftTree()) {
			return;
		}
		EmiStack hovered = HoveredStack.get();
		Nei.LOG.info("[MaEMI] autocraft key: hovered={}", hovered.isEmpty() ? "none" : dev.nei.util.StackIO.key(hovered));
		if (hovered.isEmpty()) {
			message(Text.translatable("nei.autocraft.nothing"));
			return;
		}
		requestCraft(hovered, amount);
	}

	public static void requestCraft(EmiStack target, int amount) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		long startedAt = System.nanoTime();
		List<dev.emi.emi.api.recipe.EmiRecipe> recipes = dev.nei.recipe.RecipeRegistry.recipesFor(target);
		HandledScreen<?> screen = client.currentScreen instanceof HandledScreen<?> hs ? hs : null;
		java.util.function.Predicate<dev.emi.emi.api.recipe.EmiRecipe> fillable =
			recipe -> screen != null && canFillIn(recipe, screen);
		Nei.LOG.info("[MaEMI] autocraft lookup {}: {} EMI recipe(s), {} usable in {}",
			dev.nei.util.StackIO.key(target), recipes.size(),
			recipes.stream().filter(fillable).count(),
			screen == null ? "no screen" : screen.getClass().getSimpleName());
		if (recipes.isEmpty()) {
			message(Text.translatable("nei.autocraft.no_recipe"));
			return;
		}
		if (recipes.stream().noneMatch(fillable)) {
			message(Text.translatable("nei.autocraft.wrong_screen"));
			return;
		}
		java.util.Map<String, Long> stock = CraftPlanner.stockForScreen(screen, recipes);
		CraftPlan candidate;
		if (amount <= 0) {
			int cap = Math.min(512, Math.max(1, dev.nei.config.NeiConfig.get().autocraft.maxCrafts));
			candidate = CraftPlanner.planAll(target, cap, fillable, stock);
		} else {
			candidate = CraftPlanner.planAdditional(target, amount, fillable, stock);
		}
		Nei.LOG.info("[MaEMI] autocraft planning took {} ms", (System.nanoTime() - startedAt) / 1_000_000);
		Nei.LOG.info("[MaEMI] autocraft {} x{}: {} step(s), missing {}",
			dev.nei.util.StackIO.key(target), amount, candidate.steps().size(), candidate.missing());
		for (CraftStep planned : candidate.steps()) {
			Nei.LOG.info("[MaEMI] plan step {} x{}", planned.recipe().getId(), planned.batches());
		}
		if (!candidate.missing().isEmpty()) {
			// The planner reports the target itself when it has candidate recipes but none can
			// actually be used here; that is not a missing ingredient, so say what it really is.
			String targetId = target.getId() == null ? "" : target.getId().toString();
			if (candidate.missing().size() == 1 && candidate.missing().get(0).equals(targetId)) {
				message(Text.translatable("nei.autocraft.no_recipe"));
				return;
			}
			message(Text.translatable("nei.autocraft.missing", String.join(", ", candidate.missing())));
			return;
		}
		if (candidate.isEmpty()) {
			// Nothing to do; do not scold the player with "you already have enough".
			Nei.LOG.info("[MaEMI] autocraft {}: already satisfied, no steps", dev.nei.util.StackIO.key(target));
			return;
		}
		if (plan != null) {
			returnCraftingItems();
		}
		resetAe2();
		plan = candidate;
		stepIndex = 0;
		delay = 2;
		failures = 0;
		message(Text.translatable("nei.autocraft.start", candidate.steps().size()));
		if (!(client.currentScreen instanceof HandledScreen)) {
			client.setScreen(new InventoryScreen(client.player));
		}
	}

	public static void tick() {
		if (plan == null) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.currentScreen == null) {
			cancel();
			return;
		}
		if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
			return;
		}
		if (delay > 0) {
			delay--;
			return;
		}
		if (stepIndex >= plan.steps().size()) {
			finish();
			return;
		}
		CraftStep step = plan.steps().get(stepIndex);
		// Only hand the recipe to a handler that actually accepts it for this screen. Otherwise
		// performFill would fail and the old code would count the step as done anyway.
		Object handler = EmiRecipeFiller.getFirstValidHandler(step.recipe(), screen);
		if (handler == null) {
			onFillFailure();
			return;
		}
		Nei.LOG.info("[MaEMI] step {} recipe {} x{} handler {}", stepIndex + 1, step.recipe().getId(), step.batches(),
			handler.getClass().getSimpleName());
		if (isAe2Handler(handler)) {
			tickAe2(step, screen, handler);
			return;
		}
		boolean crafted = false;
		try {
			crafted = EmiRecipeFiller.performFill(step.recipe(), screen, dev.emi.emi.api.EmiFillAction.QUICK_MOVE, step.batches());
		} catch (Throwable t) {
			Nei.LOG.error("[MaEMI] autocraft step failed", t);
		}
		if (!crafted) {
			onFillFailure();
			return;
		}
		failures = 0;
		stepIndex++;
		delay = 2;
	}

	private static boolean isAe2Handler(Object handler) {
		return handler.getClass().getName().startsWith("appeng.");
	}

	/**
	 * AE2's EMI handler transfers one recipe into the terminal grid and reports whether that
	 * transfer worked, but it does not take the output. Transfer, wait for the server to fill the
	 * grid, quick-move the output, then repeat for the remaining batches.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void tickAe2(CraftStep step, HandledScreen<?> screen, Object handler) {
		if (ae2Step != stepIndex) {
			ae2Step = stepIndex;
			ae2Remaining = Math.max(1, step.batches());
			ae2TakeOutput = false;
			ae2OutputWaits = 0;
		}
		if (!ae2TakeOutput) {
			boolean transferred;
			try {
				EmiRecipeHandler emiHandler = (EmiRecipeHandler) handler;
				EmiPlayerInventory inventory = emiHandler.getInventory(screen);
				EmiCraftContext context = new EmiCraftContext(screen, inventory,
					EmiCraftContext.Type.CRAFTABLE, EmiCraftContext.Destination.INVENTORY, 1);
				transferred = emiHandler.craft(step.recipe(), context);
			} catch (Throwable t) {
				Nei.LOG.error("[MaEMI] AE2 recipe transfer failed", t);
				transferred = false;
			}
			if (!transferred) {
				onFillFailure();
				return;
			}
			ae2TakeOutput = true;
			ae2OutputWaits = 0;
			// The transfer is a client-to-server packet; give the server a few ticks to update the
			// output slot before trying to take it.
			delay = 4;
			return;
		}
		Slot output = ((StandardRecipeHandler) handler).getOutputSlot(screen.getScreenHandler());
		if (output == null || !output.hasStack()) {
			ae2OutputWaits++;
			if (ae2OutputWaits > 20) {
				onFillFailure();
				return;
			}
			delay = 2;
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.interactionManager == null || client.player == null) {
			onFillFailure();
			return;
		}
		client.interactionManager.clickSlot(screen.getScreenHandler().syncId, output.id, 0,
			SlotActionType.QUICK_MOVE, client.player);
		ae2OutputWaits = 0;
		ae2Remaining--;
		delay = 2;
		if (ae2Remaining <= 0) {
			ae2Step = -1;
			ae2TakeOutput = false;
			stepIndex++;
		}
	}

	private static void resetAe2() {
		ae2Step = -1;
		ae2Remaining = 0;
		ae2TakeOutput = false;
		ae2OutputWaits = 0;
	}

	/** A failed fill leaves the step in place; a few consecutive failures abort the request. */
	private static void onFillFailure() {
		failures++;
		delay = 4;
		if (failures >= MAX_FILL_FAILURES) {
			message(Text.translatable("nei.autocraft.stuck", stepIndex + 1));
			cancel();
		}
	}

	private static void finish() {
		returnCraftingItems();
		resetAe2();
		message(Text.translatable("nei.autocraft.done", plan.steps().size()));
		plan = null;
		failures = 0;
	}

	public static void cancel() {
		returnCraftingItems();
		resetAe2();
		plan = null;
		stepIndex = 0;
		delay = 0;
		failures = 0;
	}

	/**
	 * Moves everything the filler left behind (crafting grid, cursor) back to the player inventory,
	 * so an autocraft no longer ends with a table full of leftovers.
	 */
	private static void returnCraftingItems() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.interactionManager == null
				|| !(client.currentScreen instanceof HandledScreen<?> screen)) {
			return;
		}
		net.minecraft.screen.ScreenHandler handler = screen.getScreenHandler();
		net.minecraft.item.ItemStack cursor = handler.getCursorStack();
		if (!cursor.isEmpty()) {
			for (net.minecraft.screen.slot.Slot slot : handler.slots) {
				if (slot.inventory == client.player.getInventory() && slot.getStack().isEmpty()) {
					client.interactionManager.clickSlot(handler.syncId, slot.id, 0,
						net.minecraft.screen.slot.SlotActionType.PICKUP, client.player);
					break;
				}
			}
		}
		for (net.minecraft.screen.slot.Slot slot : handler.slots) {
			if (!CraftPlanner.isCraftingSlot(slot) || slot.getStack().isEmpty()) {
				continue;
			}
			client.interactionManager.clickSlot(handler.syncId, slot.id, 0,
				net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, client.player);
		}
	}

	/**
	 * A generic inventory/crafting-table screen must only receive vanilla crafting recipes, and
	 * only as many inputs as it has slots. EMI's fallback handler otherwise claims recipes like
	 * grindstone disenchanting, which then cannot actually be filled.
	 */
	private static boolean canFillIn(dev.emi.emi.api.recipe.EmiRecipe recipe, HandledScreen<?> screen) {
		if (screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen
				|| screen instanceof net.minecraft.client.gui.screen.ingame.CraftingScreen) {
			if (!CraftPlanner.isVanillaCrafting(recipe)) {
				return false;
			}
			int slots = screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen ? 4 : 9;
			if (recipe.getInputs().size() > slots) {
				return false;
			}
		}
		return EmiRecipeFiller.getFirstValidHandler(recipe, screen) != null;
	}

	public static boolean isCrafting() {
		return plan != null;
	}

	public static void requestCraftTree() {
		craftTree();
	}

	private static boolean craftTree() {
		if (BoM.tree == null || BoM.tree.goal == null) {
			return false;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
			client.setScreen(client.player == null ? null : new InventoryScreen(client.player));
			return false;
		}
		ArrayDeque<MaterialNode> queue = new ArrayDeque<>();
		queue.push(BoM.tree.goal);
		boolean any = false;
		while (!queue.isEmpty()) {
			MaterialNode node = queue.pop();
			if (node.children != null) {
				node.children.forEach(queue::push);
			}
			if (node.recipe == null || EmiRecipeFiller.getFirstValidHandler(node.recipe, screen) == null) {
				continue;
			}
			try {
				any |= EmiRecipeFiller.performFill(node.recipe, screen, dev.emi.emi.api.EmiFillAction.QUICK_MOVE, (int) node.neededBatches);
			} catch (Throwable t) {
				Nei.LOG.error("[MaEMI] autocraft tree step failed", t);
			}
		}
		if (!any) {
			message(Text.translatable("nei.autocraft.tree_failed"));
		}
		return any;
	}

	private static boolean isBoMScreen(MinecraftClient client) {
		return client.currentScreen != null && client.currentScreen.getClass().getName().contains("BoMScreen");
	}

	private static void message(Text text) {
		dev.nei.util.Messages.status(text);
	}
}