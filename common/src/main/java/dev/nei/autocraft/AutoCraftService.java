package dev.nei.autocraft;

import java.util.ArrayDeque;
import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.bom.MaterialNode;
import dev.emi.emi.registry.EmiRecipeFiller;
import dev.nei.Nei;
import dev.nei.util.HoveredStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.text.Text;

/**
 * Runs a {@link CraftPlan} against the open screen. NEI's autocrafting is deliberately
 * screen-driven: it uses the same fill path as EMI's own craft button, so it obeys the screen
 * handler's rules instead of injecting items, and it stops as soon as a step fails.
 */
public final class AutoCraftService {
	private static final int MAX_FILL_FAILURES = 3;

	private static CraftPlan plan;
	private static int stepIndex;
	private static int delay;
	private static int failures;

	private AutoCraftService() {
	}

	public static void requestCraftHovered() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		if (isBoMScreen(client) && craftTree()) {
			return;
		}
		EmiStack hovered = HoveredStack.get();
		Nei.LOG.info("[NEI] autocraft key: hovered={}", hovered.isEmpty() ? "none" : dev.nei.util.StackIO.key(hovered));
		if (hovered.isEmpty()) {
			message(Text.translatable("nei.autocraft.nothing"));
			return;
		}
		requestCraft(hovered, 1);
	}

	public static void requestCraft(EmiStack target, int amount) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		int recipes = dev.nei.recipe.RecipeRegistry.recipesFor(target).size();
		Nei.LOG.info("[NEI] autocraft lookup {}: {} EMI recipe(s)", dev.nei.util.StackIO.key(target), recipes);
		CraftPlan candidate = CraftPlanner.plan(target, amount);
		Nei.LOG.info("[NEI] autocraft {} x{}: {} step(s), missing {}",
			dev.nei.util.StackIO.key(target), amount, candidate.steps().size(), candidate.missing());
		if (!candidate.missing().isEmpty()) {
			message(Text.translatable("nei.autocraft.missing", String.join(", ", candidate.missing())));
			return;
		}
		if (candidate.isEmpty()) {
			// Nothing to do; do not scold the player with "you already have enough".
			Nei.LOG.info("[NEI] autocraft {}: already satisfied, no steps", dev.nei.util.StackIO.key(target));
			return;
		}
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
		if (EmiRecipeFiller.getFirstValidHandler(step.recipe(), screen) == null) {
			onFillFailure();
			return;
		}
		boolean crafted = false;
		try {
			crafted = EmiRecipeFiller.performFill(step.recipe(), screen, EmiCraftContext.Type.CRAFTABLE,
				EmiCraftContext.Destination.INVENTORY, step.batches());
		} catch (Throwable t) {
			Nei.LOG.error("[NEI] autocraft step failed", t);
		}
		if (!crafted) {
			onFillFailure();
			return;
		}
		failures = 0;
		stepIndex++;
		delay = 2;
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
		message(Text.translatable("nei.autocraft.done", plan.steps().size()));
		plan = null;
		failures = 0;
	}

	public static void cancel() {
		plan = null;
		stepIndex = 0;
		delay = 0;
		failures = 0;
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
				any |= EmiRecipeFiller.performFill(node.recipe, screen, EmiCraftContext.Type.CRAFTABLE,
					EmiCraftContext.Destination.INVENTORY, (int) node.neededBatches);
			} catch (Throwable t) {
				Nei.LOG.error("[NEI] autocraft tree step failed", t);
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