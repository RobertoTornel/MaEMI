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
	private static CraftPlan plan;
	private static int stepIndex;
	private static int delay;

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
		CraftPlan candidate = CraftPlanner.plan(target, amount);
		if (!candidate.missing().isEmpty()) {
			message(Text.translatable("nei.autocraft.missing", String.join(", ", candidate.missing())));
			return;
		}
		if (candidate.isEmpty()) {
			message(Text.translatable("nei.autocraft.already"));
			return;
		}
		plan = candidate;
		stepIndex = 0;
		delay = 2;
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
		boolean crafted = false;
		try {
			crafted = EmiRecipeFiller.performFill(step.recipe(), screen, EmiCraftContext.Type.CRAFTABLE,
				EmiCraftContext.Destination.INVENTORY, step.batches());
		} catch (Throwable t) {
			Nei.LOG.error("[NEI] autocraft step failed", t);
		}
		stepIndex++;
		delay = 2;
		if (!crafted) {
			message(Text.translatable("nei.autocraft.stuck", stepIndex));
			cancel();
		}
	}

	private static void finish() {
		message(Text.translatable("nei.autocraft.done", plan.steps().size()));
		plan = null;
	}

	public static void cancel() {
		plan = null;
		stepIndex = 0;
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
			if (node.recipe == null) {
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
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(text, false);
		}
	}
}