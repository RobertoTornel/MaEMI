package dev.nei.autocraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.nei.Nei;
import dev.nei.util.ToolIdentity;
import dev.nei.config.NeiConfig;
import dev.nei.recipe.RecipeRegistry;
import dev.nei.util.StackIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * Works out how to obtain a stack from what the player is carrying, using EMI's recipe index.
 * Deliberately conservative: it never assumes a recipe can be crafted without the inputs, it stops
 * at cycles, and it reports what is missing instead of producing a plan that cannot run.
 */
public final class CraftPlanner {
	private static final int MAX_STEPS = 256;
	// Backtracking tries recipe and ingredient alternatives; cap failed exploration so a large
	// modpack recipe graph cannot stall the client thread.
	private static final int MAX_ATTEMPTS = 4096;

	private CraftPlanner() {
	}

	public static CraftPlan plan(EmiStack target, int amount) {
		int requested = Math.max(1, amount);
		if (target == null || target.isEmpty()) {
			return new CraftPlan(EmiStack.EMPTY, requested, List.of(), List.of("nothing selected"));
		}
		State state = new State(countInventory(), recipe -> true);
		int maxDepth = Math.max(1, NeiConfig.get().autocraft.maxDepth);
		plan(target, requested, 0, state, new HashSet<>(), maxDepth);
		return new CraftPlan(target, requested, state.steps, List.copyOf(state.missing));
	}

	/**
	 * Plans {@code amount} crafts <em>on top of</em> what the player already carries, so pressing C
	 * on a stack you own still does something instead of silently deciding you have enough.
	 */
	public static CraftPlan planAdditional(EmiStack target, int amount) {
		return planAdditional(target, amount, recipe -> true);
	}

	/**
	 * As above, but only recipes the given filter accepts are considered. Autocrafting uses this to
	 * keep the plan to recipes the currently open screen can actually fill.
	 */
	public static CraftPlan planAdditional(EmiStack target, int amount, Predicate<EmiRecipe> allowed) {
		int requested = Math.max(1, amount);
		if (target == null || target.isEmpty()) {
			return new CraftPlan(EmiStack.EMPTY, requested, List.of(), List.of("nothing selected"));
		}
		State state = new State(countInventory(), allowed);
		long have = state.stock.getOrDefault(stockKey(target), 0L);
		int maxDepth = Math.max(1, NeiConfig.get().autocraft.maxDepth);
		plan(target, have + requested, 0, state, new HashSet<>(), maxDepth);
		return new CraftPlan(target, requested, state.steps, List.copyOf(state.missing));
	}

	/**
	 * Reserves {@code needed} from the stock, crafting the shortfall if possible. The stock is the
	 * single source of truth for both inventory items and outputs of earlier steps, so an ingredient
	 * shared by several parents is counted (and consumed) once per parent.
	 */
	private static boolean plan(EmiStack stack, long needed, int depth, State state, Set<String> path, int maxDepth) {
		if (needed <= 0) {
			return true;
		}
		String key = stockKey(stack);
		long have = state.stock.getOrDefault(key, 0L);
		// Tools (GT hammers, files, wire cutters...) are reused between steps: one copy satisfies
		// any number of recipes and is never consumed by planning. The root target is exempt so
		// "craft one more" still plans a real craft.
		boolean reusable = depth > 0 && isReusableTool(stack);
		long neededHere = reusable ? Math.min(needed, 1) : needed;
		if (have >= neededHere) {
			if (!reusable) {
				state.stock.put(key, have - neededHere);
			}
			return true;
		}
		if (++state.attempts > MAX_ATTEMPTS || depth >= maxDepth
				|| state.steps.size() >= MAX_STEPS || path.contains(key)) {
			// A cycle or the configured depth limit means this branch cannot be resolved.
			state.missing.add(describe(stack));
			return false;
		}
		path.add(key);
		try {
			List<RecipeOption> options = recipesFor(stack, needed, state, path);
			if (options.isEmpty()) {
				state.missing.add(describe(stack));
				return false;
			}
			// Try recipes in preference order. Failed attempts are fully rolled back; if none works,
			// keep the missing leaves of the attempt that got furthest (fewest unresolved inputs).
			Set<String> bestFailure = null;
			for (RecipeOption option : options) {
				Map<String, Long> stockBefore = new HashMap<>(state.stock);
				int stepsBefore = state.steps.size();
				Set<String> missingBefore = new LinkedHashSet<>(state.missing);
				if (craft(option.recipe(), stack, needed, depth, state, path, maxDepth)) {
					return true;
				}
				Set<String> failure = new LinkedHashSet<>(state.missing);
				restore(state, stockBefore, stepsBefore, missingBefore);
				if (bestFailure == null || failure.size() < bestFailure.size()) {
					bestFailure = failure;
				}
			}
			if (bestFailure != null && !bestFailure.isEmpty()) {
				state.missing.addAll(bestFailure);
			} else {
				state.missing.add(describe(stack));
			}
			return false;
		} finally {
			path.remove(key);
		}
	}

	private static boolean craft(EmiRecipe recipe, EmiStack stack, long needed, int depth, State state,
			Set<String> path, int maxDepth) {
		long perCraft = producedAmount(recipe, stack);
		if (perCraft <= 0) {
			return false;
		}
		String key = stockKey(stack);
		long have = state.stock.getOrDefault(key, 0L);
		long shortfall = needed - have;
		if (shortfall <= 0) {
			return true;
		}
		long batches = shortfall / perCraft + (shortfall % perCraft == 0 ? 0 : 1);
		if (batches <= 0 || batches > Integer.MAX_VALUE) {
			state.missing.add(describe(stack));
			return false;
		}
		for (EmiIngredient input : recipe.getInputs()) {
			// Shaped recipes can carry empty grid slots; they are not ingredients to obtain.
			if (input == null || input.isEmpty()) {
				continue;
			}
			long perInput = Math.max(1, input.getAmount());
			long required = multiply(perInput, batches);
			if (required <= 0) {
				state.missing.add(describe(input));
				return false;
			}
			if (!planInput(input, required, depth + 1, state, path, maxDepth)) {
				return false;
			}
		}
		long produced = multiply(perCraft, batches);
		if (produced <= 0 || state.steps.size() >= MAX_STEPS) {
			state.missing.add(describe(stack));
			return false;
		}
		long total = add(state.stock.getOrDefault(key, 0L), produced);
		boolean reusable = depth > 0 && isReusableTool(stack);
		long neededHere = reusable ? Math.min(needed, 1) : needed;
		if (total < neededHere) {
			state.missing.add(describe(stack));
			return false;
		}
		state.stock.put(key, total - (reusable ? 0 : neededHere));
		state.steps.add(new CraftStep(recipe, (int) batches));
		return true;
	}

	private static boolean planInput(EmiIngredient input, long needed, int depth, State state,
			Set<String> path, int maxDepth) {
		List<EmiStack> options = inputOptions(input, needed, state, path);
		if (options.isEmpty()) {
			state.missing.add(describe(input));
			return false;
		}
		Set<String> bestFailure = null;
		for (EmiStack option : options) {
			Map<String, Long> stockBefore = new HashMap<>(state.stock);
			int stepsBefore = state.steps.size();
			Set<String> missingBefore = new LinkedHashSet<>(state.missing);
			if (plan(option, needed, depth, state, path, maxDepth)) {
				return true;
			}
			Set<String> failure = new LinkedHashSet<>(state.missing);
			restore(state, stockBefore, stepsBefore, missingBefore);
			if (bestFailure == null || failure.size() < bestFailure.size()) {
				bestFailure = failure;
			}
		}
		if (bestFailure != null && !bestFailure.isEmpty()) {
			state.missing.addAll(bestFailure);
		} else {
			state.missing.add(describe(input));
		}
		return false;
	}

	private static List<RecipeOption> recipesFor(EmiStack stack, long needed, State state, Set<String> path) {
		List<RecipeOption> options = new ArrayList<>();
		List<EmiRecipe> candidates = RecipeRegistry.recipesFor(stack);
		for (EmiRecipe recipe : candidates) {
			if (recipe == null || producedAmount(recipe, stack) <= 0) {
				continue;
			}
			// Every step has to run in the screen that is open right now, not just the target.
			if (!state.allowed.test(recipe)) {
				continue;
			}
			// Recycling recipes (grindstone disenchanting, repair) take the item itself as input
			// and give it back. They neither add a copy nor run in a normal crafting screen, so
			// they must never be how "craft one more" is satisfied.
			if (consumesTarget(recipe, stack)) {
				continue;
			}
			options.add(estimate(recipe, stack, needed, state, path));
		}
		if (options.isEmpty() && !candidates.isEmpty()) {
			Nei.LOG.info("[MaEMI] no usable recipe for {} among {} candidate(s)", stockKey(stack), candidates.size());
			int shown = 0;
			for (EmiRecipe recipe : candidates) {
				if (recipe == null || shown++ >= 4) {
					continue;
				}
				Nei.LOG.info("[MaEMI]   {} produced={} outputs={}", recipe.getId(), producedAmount(recipe, stack), recipe.getOutputs());
			}
		}
		// Deterministic order: recipes that can be resolved first, then those needing the fewest
		// items not already in stock, then vanilla crafting and finally recipe identity.
		options.sort((a, b) -> {
			int byMissing = Integer.compare(a.missing(), b.missing());
			if (byMissing != 0) {
				return byMissing;
			}
			// On a tie, a plain crafting table recipe beats a machine or mod alternative. This
			// keeps "craft the thing" on the obvious recipe instead of a conversion chain.
			boolean vanillaA = isVanillaCrafting(a.recipe());
			boolean vanillaB = isVanillaCrafting(b.recipe());
			if (vanillaA != vanillaB) {
				return vanillaA ? -1 : 1;
			}
			int byUnreachable = Integer.compare(a.unreachable(), b.unreachable());
			if (byUnreachable != 0) {
				return byUnreachable;
			}
			int byId = recipeKey(a.recipe()).compareTo(recipeKey(b.recipe()));
			if (byId != 0) {
				return byId;
			}
			return Integer.compare(a.recipe().getInputs().size(), b.recipe().getInputs().size());
		});
		return options;
	}

	/**
	 * Shallow score used only to order recipes; the recursive attempt decides whether one works.
	 * {@code missing} counts inputs not already in stock; {@code unreachable} counts inputs that
	 * are neither in stock nor craftable, so a craftable recipe is tried before an impossible one.
	 */
	private static RecipeOption estimate(EmiRecipe recipe, EmiStack stack, long needed, State state, Set<String> path) {
		long perCraft = producedAmount(recipe, stack);
		if (perCraft <= 0) {
			return new RecipeOption(recipe, Integer.MAX_VALUE, Integer.MAX_VALUE);
		}
		long have = state.stock.getOrDefault(stockKey(stack), 0L);
		long shortfall = Math.max(0, needed - have);
		long batches = shortfall == 0 ? 1 : shortfall / perCraft + (shortfall % perCraft == 0 ? 0 : 1);
		int missing = 0;
		int unreachable = 0;
		for (EmiIngredient input : recipe.getInputs()) {
			if (input == null || input.isEmpty()) {
				continue;
			}
			long required = multiply(Math.max(1, input.getAmount()), batches);
			if (required <= 0 || !available(input, required, state)) {
				missing++;
				if (required <= 0 || !canSatisfy(input, required, state, path)) {
					unreachable++;
				}
			}
		}
		return new RecipeOption(recipe, unreachable, missing);
	}

	private static boolean available(EmiIngredient input, long required, State state) {
		for (EmiStack candidate : input.getEmiStacks()) {
			if (candidate != null && !candidate.isEmpty()
					&& state.stock.getOrDefault(stockKey(candidate), 0L) >= needFor(candidate, required)) {
				return true;
			}
		}
		return false;
	}

	/** Tools are required, not spent: one copy satisfies any required amount. */
	private static long needFor(EmiStack stack, long required) {
		return isReusableTool(stack) ? Math.min(required, 1) : required;
	}

	private static boolean canSatisfy(EmiIngredient input, long required, State state, Set<String> path) {
		for (EmiStack candidate : input.getEmiStacks()) {
			if (candidate == null || candidate.isEmpty()) {
				continue;
			}
			String key = stockKey(candidate);
			if (state.stock.getOrDefault(key, 0L) >= needFor(candidate, required)) {
				return true;
			}
			if (!path.contains(key) && hasRecipe(candidate, state)) {
				return true;
			}
		}
		return false;
	}

	private static List<EmiStack> inputOptions(EmiIngredient input, long needed, State state, Set<String> path) {
		List<EmiStack> options = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (EmiStack candidate : input.getEmiStacks()) {
			if (candidate == null || candidate.isEmpty() || !seen.add(stockKey(candidate))) {
				continue;
			}
			options.add(candidate);
		}
		options.sort((a, b) -> {
			int tierA = optionTier(a, needed, state, path);
			int tierB = optionTier(b, needed, state, path);
			if (tierA != tierB) {
				return Integer.compare(tierB, tierA);
			}
			long haveA = state.stock.getOrDefault(stockKey(a), 0L);
			long haveB = state.stock.getOrDefault(stockKey(b), 0L);
			if (haveA != haveB) {
				return Long.compare(haveB, haveA);
			}
			return stockKey(a).compareTo(stockKey(b));
		});
		return options;
	}

	private static int optionTier(EmiStack stack, long needed, State state, Set<String> path) {
		String key = stockKey(stack);
		long have = state.stock.getOrDefault(key, 0L);
		if (have >= needFor(stack, needed)) {
			return 3;
		}
		if (!path.contains(key) && hasRecipe(stack, state)) {
			return 2;
		}
		return have > 0 ? 1 : 0;
	}

	private static boolean hasRecipe(EmiStack stack, State state) {
		String key = stockKey(stack);
		Boolean cached = state.craftable.get(key);
		if (cached != null) {
			return cached;
		}
		boolean craftable = false;
		for (EmiRecipe recipe : RecipeRegistry.recipesFor(stack)) {
			if (recipe != null && producedAmount(recipe, stack) > 0) {
				craftable = true;
				break;
			}
		}
		state.craftable.put(key, craftable);
		return craftable;
	}

	private static boolean consumesTarget(EmiRecipe recipe, EmiStack target) {
		ItemStack targetStack = target.getItemStack();
		if (targetStack == null || targetStack.isEmpty()) {
			return false;
		}
		if (!isReusableTool(target)) {
			return false;
		}
		for (EmiIngredient input : recipe.getInputs()) {
			if (input == null || input.isEmpty()) {
				continue;
			}
			for (EmiStack candidate : input.getEmiStacks()) {
				if (candidate != null && sameItem(candidate, target)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isVanillaCrafting(EmiRecipe recipe) {
		try {
			if (recipe.getCategory() == null || recipe.getCategory().getId() == null) {
				return false;
			}
			String id = recipe.getCategory().getId().toString();
			return "minecraft:crafting".equals(id) || id.endsWith(":crafting") || id.endsWith("/crafting");
		} catch (Throwable t) {
			return false;
		}
	}

	private static String recipeKey(EmiRecipe recipe) {
		try {
			Identifier id = recipe.getId();
			Identifier category = recipe.getCategory() == null ? null : recipe.getCategory().getId();
			return (id == null ? "" : id.toString()) + "|" + (category == null ? "" : category.toString());
		} catch (Throwable t) {
			return "";
		}
	}

	private static boolean sameItem(EmiStack a, EmiStack b) {
		ItemStack left = a.getItemStack();
		ItemStack right = b.getItemStack();
		return left != null && right != null && !left.isEmpty() && !right.isEmpty()
			&& left.getItem() == right.getItem();
	}

	private static boolean isReusableTool(EmiStack stack) {
		return ToolIdentity.isTool(stack);
	}

	private static long producedAmount(EmiRecipe recipe, EmiStack stack) {
		long amount = 0;
		for (EmiStack output : recipe.getOutputs()) {
			if (output == null || output.isEmpty()) {
				continue;
			}
			// EMI's comparison includes NBT, and GT tool outputs carry generated tool stats that
			// the hovered item may spell slightly differently. Fall back to the item identity.
			if (!output.isEqual(stack) && !(isReusableTool(stack) && sameItem(output, stack))) {
				continue;
			}
			amount = add(amount, Math.max(1, output.getAmount()));
		}
		return amount;
	}

	/**
	 * Stock identity uses EMI's own comparison key, not the strict NBT key: recipes and the item
	 * panel hand us different NBT variants of the same item (tools, damage, GT behaviours), and
	 * EMI itself treats them as the same ingredient. Hide/subset files keep using StackIO.key.
	 */
	private static String stockKey(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		// Tools are interchangeable regardless of damage/enchantments, so one key per tool item.
		// Everything else (GT plates, ingots, ...) carries its material in NBT and must keep it,
		// or an aluminium plate would count as a wrought iron plate.
		if (isReusableTool(stack)) {
			Identifier id = stack.getId();
			return "tool:" + (id == null ? "" : id);
		}
		return StackIO.key(stack);
	}

	private static Map<String, Long> countInventory() {
		Map<String, Long> counts = new HashMap<>();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return counts;
		}
		PlayerInventory inventory = client.player.getInventory();
		for (ItemStack stack : inventory.main) {
			add(counts, stack);
		}
		for (ItemStack stack : inventory.armor) {
			add(counts, stack);
		}
		for (ItemStack stack : inventory.offHand) {
			add(counts, stack);
		}
		if (client.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen<?> screen) {
			for (net.minecraft.screen.slot.Slot slot : screen.getScreenHandler().slots) {
				if (slot instanceof net.minecraft.screen.slot.CraftingResultSlot
						|| !(slot.inventory instanceof net.minecraft.inventory.CraftingInventory)) {
					continue;
				}
				add(counts, slot.getStack());
			}
			add(counts, screen.getScreenHandler().getCursorStack());
		}
		return counts;
	}

	private static void add(Map<String, Long> counts, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		String key = stockKey(EmiStack.of(stack));
		if (!key.isEmpty()) {
			counts.merge(key, (long) stack.getCount(), Long::sum);
		}
	}

	private static long multiply(long a, long b) {
		if (a <= 0 || b <= 0) {
			return 0;
		}
		return a > Long.MAX_VALUE / b ? -1 : a * b;
	}

	private static long add(long a, long b) {
		return b > 0 && a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
	}

	private static void restore(State state, Map<String, Long> stock, int steps, Set<String> missing) {
		state.stock.clear();
		state.stock.putAll(stock);
		while (state.steps.size() > steps) {
			state.steps.remove(state.steps.size() - 1);
		}
		state.missing.clear();
		state.missing.addAll(missing);
	}

	private static String describe(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "unknown";
		}
		Identifier id = stack.getId();
		return id == null ? "unknown" : id.toString();
	}

	private static String describe(EmiIngredient ingredient) {
		if (ingredient != null) {
			for (EmiStack stack : ingredient.getEmiStacks()) {
				if (stack != null && !stack.isEmpty()) {
					return describe(stack);
				}
			}
		}
		return "unknown";
	}

	private static final class State {
		final Predicate<EmiRecipe> allowed;

		private final Map<String, Long> stock;
		private final List<CraftStep> steps = new ArrayList<>();
		private final Set<String> missing = new LinkedHashSet<>();
		private final Map<String, Boolean> craftable = new HashMap<>();
		private int attempts;

		private State(Map<String, Long> stock, Predicate<EmiRecipe> allowed) {
			this.stock = stock;
			this.allowed = allowed;
		}
	}

	private record RecipeOption(EmiRecipe recipe, int unreachable, int missing) {
	}
}