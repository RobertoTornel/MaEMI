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
	// EMI's fill reports success as soon as the request is sent, not when the server has placed
	// the output. A step that silently did nothing was therefore counted as done and the whole
	// plan announced success -- "finished" with nothing crafted. Each fill is now checked by
	// re-counting the expected output before the step is allowed to advance.
	private static dev.emi.emi.api.stack.EmiStack verifyStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
	private static long verifyBefore;
	private static int verifyWaits;
	private static boolean verifying;
	// A plan being computed across ticks. Planning is a search over the recipe graph, and a big
	// modpack makes it large; doing it in one call is what froze the window.
	private static CraftPlanner.Job planJob;
	private static EmiStack jobTarget = EmiStack.EMPTY;
	private static int jobAmount;
	private static long jobStartedAt;
	/**
	 * Work allowed per client tick. A tick is 50 ms and a frame 16; five milliseconds cannot freeze
	 * the game, and it is enough that a normal plan (tens of milliseconds of work) lands in about
	 * a second instead of making the player sit through a progress bar.
	 */
	private static final long PLAN_SLICE_NANOS = 5_000_000L;
	// EMI's fill places the ingredients for every batch but clicks the output exactly once, so a
	// step of "craft nine" produces one item and still reports success. MaEMI therefore runs the
	// fill once per batch and checks each one, which is what makes the batch count mean anything.
	private static int remainingCrafts;
	private static CraftStep currentStep;
	// What the run actually produced, in order, for the closing message and the log. A plan that
	// reports "finished" without saying what it made is impossible to argue with.
	private static final java.util.List<String> madeSoFar = new java.util.ArrayList<>();

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
		int cap = Math.min(512, Math.max(1, dev.nei.config.NeiConfig.get().autocraft.maxCrafts));
		// A second press replaces the request rather than running two planners against stale stock.
		cancelPlanning();
		// Start the plan and return. The work is spread over the following ticks: a large modpack's
		// recipe graph used to hold the render thread for up to 25 seconds here, which is the whole
		// reason the game appeared to freeze.
		planJob = CraftPlanner.job(target, amount, cap, fillable, stock);
		jobTarget = target;
		jobAmount = amount;
		jobStartedAt = startedAt;
		message(Text.translatable("nei.autocraft.planning"));
	}

	/** Stops a plan that is still being computed, if any. */
	private static void cancelPlanning() {
		planJob = null;
		jobTarget = EmiStack.EMPTY;
		jobAmount = 0;
	}
	private static void startCraft(EmiStack target, int amount, CraftPlan candidate) {
		MinecraftClient client = MinecraftClient.getInstance();
		Nei.LOG.info("[MaEMI] autocraft {} x{}: {} step(s), missing {}{}",
			dev.nei.util.StackIO.key(target), amount, candidate.steps().size(), candidate.missing(),
			candidate.timedOut() ? " (ran out of planning time)" : "");
		for (CraftStep planned : candidate.steps()) {
			Nei.LOG.info("[MaEMI] plan step {} x{}", planned.recipe().getId(), planned.batches());
		}
		if (!candidate.missing().isEmpty()) {
			// Log what the player actually carries for each missing item: when a stock key does not
			// line up, the count is the only clue that the item was there all along.
			CraftPlanner.describeMissing(candidate.missing());
			if (candidate.timedOut()) {
				// Exploring the recipe graph hit the time budget; that says nothing about whether the
				// item can be made, so do not pretend it is missing.
				message(Text.translatable("nei.autocraft.timeout"));
				return;
			}
			// The planner reports the target itself when it has candidate recipes but none can
			// actually be used here; that is not a missing ingredient, so say what it really is.
			String targetId = target.getId() == null ? "" : target.getId().toString();
			if (candidate.missing().size() == 1 && candidate.missing().get(0).equals(targetId)) {
				message(Text.translatable("nei.autocraft.no_recipe"));
				return;
			}
			// A missing tool is the commonest dead end, and the stock message names it like any other
			// item. Say plainly that a tool is what is needed.
			if (candidate.missingTools() != null && !candidate.missingTools().isEmpty()) {
				message(Text.translatable("nei.autocraft.need_tool",
					String.join(", ", candidate.missingTools())));
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
		resetVerification();
		plan = candidate;
		stepIndex = 0;
		delay = 2;
		failures = 0;
		message(Text.translatable("nei.autocraft.start", candidate.steps().size()));
		if (!(client.currentScreen instanceof HandledScreen)) {
			client.setScreen(new InventoryScreen(client.player));
		}
	}

	/**
	 * Spends one slice of the current planning job. When it finishes, the result is handled exactly
	 * as a synchronous plan used to be, so the rest of the feature is unchanged.
	 */
	private static void advancePlanning() {
		if (!(MinecraftClient.getInstance().currentScreen instanceof HandledScreen)) {
			// The screen the plan is being made for is gone; its result would be unusable.
			cancelPlanning();
			return;
		}
		if (planJob.tick(PLAN_SLICE_NANOS)) {
			CraftPlan candidate = planJob.result();
			long took = (System.nanoTime() - jobStartedAt) / 1_000_000;
			Nei.LOG.info("[MaEMI] autocraft planning took {} ms over {} probe(s)", took, planJob.probes() + 1);
			EmiStack target = jobTarget;
			int amount = jobAmount;
			planJob = null;
			startCraft(target, amount, candidate);
		}
	}

	public static void tick() {
		if (planJob != null) {
			advancePlanning();
			return;
		}
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
		if (verifying) {
			checkVerified();
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
		if (currentStep == null || currentStep != step) {
			currentStep = step;
			remainingCrafts = Math.max(1, step.batches());
			Nei.LOG.info("[MaEMI] step {} recipe {} x{} handler {}", stepIndex + 1, step.recipe().getId(),
				step.batches(), handler.getClass().getSimpleName());
		}
		if (isAe2Handler(handler)) {
			tickAe2(step, screen, handler);
			return;
		}
		// One fill per craft. EMI batches the ingredients but only takes the output once, so
		// asking it for nine crafts made one and reported success.
		boolean crafted = false;
		try {
			crafted = EmiRecipeFiller.performFill(step.recipe(), screen, EmiCraftContext.Type.CRAFTABLE,
				EmiCraftContext.Destination.INVENTORY, 1);
		} catch (Throwable t) {
			Nei.LOG.error("[MaEMI] autocraft step failed", t);
		}
		if (!crafted) {
			onFillFailure();
			return;
		}
		failures = 0;
		beginVerify(step);
	}

	/**
	 * Records what the step is supposed to produce, so the next ticks can confirm it really
	 * appeared. The output is the target itself for the last step and whatever the recipe makes
	 * for the intermediate ones.
	 */
	private static void beginVerify(CraftStep step) {
		EmiStack output = firstOutput(step);
		if (output.isEmpty() || step.recipe().getOutputs().size() > 1) {
			// Recipes with several outputs, and recipes whose output cannot be identified, cannot be
			// verified by counting. They are recorded as done rather than guessed at.
			completeCraft();
			return;
		}
		verifyStack = output;
		verifyBefore = CraftPlanner.carried(output);
		verifyWaits = 0;
		verifying = true;
		delay = 2;
	}

	/** One craft of the current step is done: queue the next batch, or move to the next step. */
	private static void completeCraft() {
		recordMade();
		if (--remainingCrafts > 0) {
			delay = 2;
			return;
		}
		remainingCrafts = 0;
		currentStep = null;
		stepIndex++;
		delay = 2;
	}

	/**
	 * Advances only once the expected output has appeared in the inventory. The server needs a
	 * couple of ticks to answer a fill, and a modded station may need several; a step that still
	 * produced nothing after that is a failure, not a success.
	 */
	private static void checkVerified() {
		long after = CraftPlanner.carried(verifyStack);
		if (after > verifyBefore) {
			verifying = false;
			verifyStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
			completeCraft();
			return;
		}
		// A generous window: a busy server or a station with its own processing time can take a
		// moment, and declaring "stuck" too early cancels a plan that was working.
		if (++verifyWaits > 30) {
			verifying = false;
			Nei.LOG.info("[MaEMI] step {} produced no {} (had {}, still {})", stepIndex + 1,
				dev.nei.util.StackIO.key(verifyStack), verifyBefore, after);
			verifyStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
			onFillFailure();
			return;
		}
		delay = 2;
	}

	private static EmiStack firstOutput(CraftStep step) {
		try {
			for (EmiStack output : step.recipe().getOutputs()) {
				if (output != null && !output.isEmpty()) {
					return output;
				}
			}
		} catch (Throwable t) {
			Nei.LOG.warn("[MaEMI] could not read the outputs of {}", step.recipe().getId());
		}
		return dev.emi.emi.api.stack.EmiStack.EMPTY;
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
		// Read the record before resetting it: clearing first made every run claim it produced
		// nothing, which is exactly the lie this instrument was added to stop.
		String summary = summaryOf(madeSoFar);
		Nei.LOG.info("[MaEMI] autocraft done, made: {}", summary);
		message(Text.translatable("nei.autocraft.done", plan.steps().size(), summary));
		resetVerification();
		plan = null;
		failures = 0;
	}

	public static void cancel() {
		cancelPlanning();
		returnCraftingItems();
		resetAe2();
		resetVerification();
		plan = null;
		stepIndex = 0;
		delay = 0;
		failures = 0;
	}

	/** A short "what came out" list: one entry per distinct recipe, with how often it ran. */
	private static String summaryOf(java.util.List<String> made) {
		if (made.isEmpty()) {
			return "nothing";
		}
		java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		for (String id : made) {
			counts.merge(shortName(id), 1, Integer::sum);
		}
		StringBuilder out = new StringBuilder();
		int shown = 0;
		for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
			if (shown++ == 5) {
				out.append(", ...");
				break;
			}
			if (out.length() > 0) {
				out.append(", ");
			}
			out.append(entry.getKey());
			if (entry.getValue() > 1) {
				out.append(" x").append(entry.getValue());
			}
		}
		return out.toString();
	}

	/** {@code gtceu:shaped/stick_iron} reads better as {@code stick_iron}. */
	private static String shortName(String recipeId) {
		int slash = recipeId.lastIndexOf('/');
		return slash >= 0 && slash + 1 < recipeId.length() ? recipeId.substring(slash + 1) : recipeId;
	}

	private static void recordMade() {
		if (currentStep == null) {
			return;
		}
		String id = currentStep.recipe().getId() == null ? "?" : currentStep.recipe().getId().toString();
		if (madeSoFar.size() < 64) {
			madeSoFar.add(id);
		}
	}

	private static void resetVerification() {
		madeSoFar.clear();
		verifying = false;
		verifyWaits = 0;
		verifyBefore = 0;
		verifyStack = dev.emi.emi.api.stack.EmiStack.EMPTY;
		remainingCrafts = 0;
		currentStep = null;
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
		return plan != null || planJob != null;
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
				any |= EmiRecipeFiller.performFill(node.recipe, screen, EmiCraftContext.Type.CRAFTABLE,
					EmiCraftContext.Destination.INVENTORY, (int) node.neededBatches);
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