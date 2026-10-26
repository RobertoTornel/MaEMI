package dev.nei.autocraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import dev.emi.emi.EmiRecipeFiller;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.nei.Nei;
import dev.nei.util.ToolIdentity;
import dev.nei.config.NeiConfig;
import dev.nei.recipe.RecipeRegistry;
import dev.nei.util.StackIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
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
		state.hint = ToolIdentity.materialOf(target);
		int maxDepth = Math.max(1, NeiConfig.get().autocraft.maxDepth);
		plan(target, requested, 0, state, new HashSet<>(), maxDepth);
		return new CraftPlan(target, requested, orderSteps(state.steps), List.copyOf(state.missing));
	}

	/**
	 * Plans {@code amount} crafts <em>on top of</em> what the player already carries, so pressing C
	 * on a stack you own still does something instead of silently deciding you have enough.
	 */
	public static CraftPlan planAdditional(EmiStack target, int amount) {
		return planAdditional(target, amount, recipe -> true);
	}

	/**
	 * Plans against an explicit stock snapshot. Autocrafting uses this so items exposed by the
	 * screen's EMI handler (AE2 network storage, for example) count as available instead of only
	 * the player inventory.
	 */
	public static CraftPlan planAdditional(EmiStack target, int amount, Predicate<EmiRecipe> allowed,
			Map<String, Long> stock) {
		int requested = Math.max(1, amount);
		if (target == null || target.isEmpty()) {
			return new CraftPlan(EmiStack.EMPTY, requested, List.of(), List.of("nothing selected"));
		}
		State state = new State(new HashMap<>(stock), allowed);
		state.hint = ToolIdentity.materialOf(target);
		long have = state.stock.getOrDefault(stockKey(target), 0L);
		int maxDepth = Math.max(1, NeiConfig.get().autocraft.maxDepth);
		plan(target, have + requested, 0, state, new HashSet<>(), maxDepth);
		return new CraftPlan(target, requested, orderSteps(state.steps), List.copyOf(state.missing));
	}

	/**
	 * Finds the largest amount that can actually be crafted, then returns that plan. Used by
	 * Ctrl+Shift+C. The cap keeps a request bounded to the configured maximum and the planner's
	 * step limit.
	 */
	public static CraftPlan planAll(EmiStack target, int cap, Predicate<EmiRecipe> allowed,
			Map<String, Long> stock) {
		int limit = Math.max(1, cap);
		CraftPlan atLimit = planAdditional(target, limit, allowed, stock);
		if (atLimit.missing().isEmpty()) {
			return atLimit;
		}
		int low = 1;
		int high = limit - 1;
		CraftPlan best = null;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			CraftPlan candidate = planAdditional(target, middle, allowed, stock);
			if (candidate.missing().isEmpty()) {
				best = candidate;
				low = middle + 1;
			} else {
				high = middle - 1;
			}
		}
		return best != null ? best : planAdditional(target, 1, allowed, stock);
	}

	/**
	 * As above, but only recipes the given filter accepts are considered. Autocrafting uses this to
	 * keep the plan to recipes the currently open screen can actually fill.
	 */
	public static CraftPlan planAdditional(EmiStack target, int amount, Predicate<EmiRecipe> allowed) {
		return planAdditional(target, amount, allowed, countInventory());
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
		// EMI fills a recipe amount as "this many independent crafts". A tool ingredient cannot
		// supply several copies, so recipes that need a tool are planned as single crafts.
		if (batches > 1 && hasToolInput(recipe)) {
			for (long i = 0; i < batches; i++) {
				state.steps.add(new CraftStep(recipe, 1));
			}
		} else {
			state.steps.add(new CraftStep(recipe, (int) batches));
		}
		return true;
	}

	private static boolean hasToolInput(EmiRecipe recipe) {
		for (EmiIngredient input : recipe.getInputs()) {
			if (input == null || input.isEmpty()) {
				continue;
			}
			for (EmiStack candidate : input.getEmiStacks()) {
				if (ToolIdentity.isTool(candidate)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean planInput(EmiIngredient input, long needed, int depth, State state,
			Set<String> path, int maxDepth) {
		List<EmiStack> options = inputOptions(input, needed, state, path);
		if (options.isEmpty()) {
			state.missing.add(describe(input));
			return false;
		}
		if (options.size() > 1) {
			List<String> keys = new ArrayList<>();
			for (int i = 0; i < options.size() && i < 8; i++) {
				keys.add(stockKey(options.get(i)));
			}
			Nei.LOG.info("[MaEMI] tag input {}: {} option(s) {}", describe(input), options.size(), keys);
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
		Map<String, Integer> tiers = new HashMap<>();
		Map<String, Integer> materials = new HashMap<>();
		Map<String, Long> stock = new HashMap<>();
		for (EmiStack candidate : input.getEmiStacks()) {
			String key = stockKey(candidate);
			if (candidate == null || candidate.isEmpty() || !seen.add(key)) {
				continue;
			}
			options.add(candidate);
			// Score each candidate once. The comparator used to re-run recipe lookups for every
			// comparison, which is where the pause after pressing C came from.
			tiers.put(key, optionTier(candidate, needed, state, path));
			materials.put(key, materialScore(candidate, state.hint));
			stock.put(key, state.stock.getOrDefault(key, 0L));
		}
		options.sort((a, b) -> {
			String keyA = stockKey(a);
			String keyB = stockKey(b);
			int tierA = tiers.getOrDefault(keyA, 0);
			int tierB = tiers.getOrDefault(keyB, 0);
			if (tierA != tierB) {
				return Integer.compare(tierB, tierA);
			}
			// Among equally available candidates, keep the target's material: a wrought iron
			// tool recipe should ask for a wrought iron file, not an aluminium one.
			int materialA = materials.getOrDefault(keyA, 0);
			int materialB = materials.getOrDefault(keyB, 0);
			if (materialA != materialB) {
				return Integer.compare(materialB, materialA);
			}
			long haveA = stock.getOrDefault(keyA, 0L);
			long haveB = stock.getOrDefault(keyB, 0L);
			if (haveA != haveB) {
				return Long.compare(haveB, haveA);
			}
			return keyA.compareTo(keyB);
		});
		return options;
	}

	private static int materialScore(EmiStack stack, String hint) {
		if (hint.isEmpty() || !ToolIdentity.isTool(stack)) {
			return 0;
		}
		String material = ToolIdentity.materialOf(stack);
		if (material.equals(hint)) {
			return 2;
		}
		return material.startsWith(hint) || hint.startsWith(material) ? 1 : 0;
	}

	private static int optionTier(EmiStack stack, long needed, State state, Set<String> path) {
		String key = stockKey(stack);
		long have = state.stock.getOrDefault(key, 0L);
		if (have >= needFor(stack, needed)) {
			return 4;
		}
		if (!path.contains(key) && shallowCraftable(stack, state, path)) {
			return 3;
		}
		if (!path.contains(key) && hasRecipe(stack, state)) {
			return 2;
		}
		return have > 0 ? 1 : 0;
	}

	/**
	 * True when at least one allowed recipe for the stack has every input already in stock. Used
	 * only to order tag candidates: oak planks made from oak logs in your inventory should be tried
	 * before some modded plank whose log you have never seen.
	 */
	private static boolean shallowCraftable(EmiStack stack, State state, Set<String> path) {
		String key = stockKey(stack);
		for (EmiRecipe recipe : RecipeRegistry.recipesFor(stack)) {
			if (recipe == null || producedAmount(recipe, stack) <= 0 || !state.allowed.test(recipe)
					|| consumesTarget(recipe, stack)) {
				continue;
			}
			boolean all = true;
			for (EmiIngredient input : recipe.getInputs()) {
				if (input == null || input.isEmpty()) {
					continue;
				}
				boolean found = false;
				for (EmiStack candidate : input.getEmiStacks()) {
					if (candidate != null && !candidate.isEmpty()
							&& state.stock.getOrDefault(stockKey(candidate), 0L)
								>= needFor(candidate, Math.max(1, input.getAmount()))) {
						found = true;
						break;
					}
				}
				if (!found) {
					all = false;
					break;
				}
			}
			if (all) {
				return true;
			}
		}
		return false;
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

	/**
	 * Orders the steps so every ingredient is produced before the step that consumes it. Recipe
	 * exploration can reserve stock in an order that is not the execution order (a later planned
	 * step may be what makes an earlier one possible); executing the raw list would then fail on
	 * an ingredient that only appears further down. The sort is stable, so independent steps keep
	 * the order the planner chose.
	 */
	private static List<CraftStep> orderSteps(List<CraftStep> steps) {
		int size = steps.size();
		if (size < 2) {
			return steps;
		}
		Map<String, List<Integer>> producers = new HashMap<>();
		for (int i = 0; i < size; i++) {
			for (EmiStack output : steps.get(i).recipe().getOutputs()) {
				String key = stockKey(output);
				if (!key.isEmpty()) {
					producers.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
				}
			}
		}
		List<List<Integer>> after = new ArrayList<>(size);
		int[] indegree = new int[size];
		for (int i = 0; i < size; i++) {
			after.add(new ArrayList<>());
		}
		for (int consumer = 0; consumer < size; consumer++) {
			Set<Integer> dependencies = new LinkedHashSet<>();
			for (EmiIngredient input : steps.get(consumer).recipe().getInputs()) {
				if (input == null || input.isEmpty()) {
					continue;
				}
				for (EmiStack candidate : input.getEmiStacks()) {
					String key = stockKey(candidate);
					for (int producer : producers.getOrDefault(key, List.of())) {
						if (producer != consumer) {
							dependencies.add(producer);
						}
					}
				}
			}
			for (int producer : dependencies) {
				after.get(producer).add(consumer);
				indegree[consumer]++;
			}
		}
		java.util.PriorityQueue<Integer> ready = new java.util.PriorityQueue<>();
		for (int i = 0; i < size; i++) {
			if (indegree[i] == 0) {
				ready.add(i);
			}
		}
		List<CraftStep> ordered = new ArrayList<>(size);
		while (!ready.isEmpty()) {
			int next = ready.poll();
			ordered.add(steps.get(next));
			for (int dependent : after.get(next)) {
				if (--indegree[dependent] == 0) {
					ready.add(dependent);
				}
			}
		}
		if (ordered.size() != size) {
			// A cyclic plan cannot be ordered; leave it alone and let execution report the failure.
			Nei.LOG.warn("[MaEMI] crafting plan has a dependency cycle; keeping planner order");
			return steps;
		}
		return ordered;
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

	/**
	 * Stock visible through the open screen. The screen's EMI handler is preferred because it may
	 * expose storage beyond the player inventory (AE2's terminal handler exposes the ME network);
	 * the crafting grid and cursor are added on top because handlers generally do not include them.
	 */
	public static Map<String, Long> stockForScreen(HandledScreen<?> screen, List<EmiRecipe> recipes) {
		// AE2 only exposes its network to EMI when the player turns that option on, and it is off
		// by default. Read the terminal's client-side repo directly so network items are stock
		// regardless of that setting; fall back to the EMI handler if the repo cannot be read.
		if (screen != null && screen.getClass().getName().startsWith("appeng.")) {
			Map<String, Long> network = ae2NetworkStock(screen);
			if (network != null) {
				addPlayerInventory(network);
				addCraftingSlots(screen, network);
				add(network, screen.getScreenHandler().getCursorStack());
				return network;
			}
		}
		Map<String, Long> counts = null;
		if (screen != null) {
			for (EmiRecipe recipe : recipes) {
				EmiRecipeHandler<?> handler = EmiRecipeFiller.getFirstValidHandler(recipe, screen);
				if (!(handler instanceof StandardRecipeHandler)) {
					continue;
				}
				try {
					@SuppressWarnings({"rawtypes", "unchecked"})
					EmiPlayerInventory inventory = ((StandardRecipeHandler) handler).getInventory(screen);
					if (inventory != null && inventory.inventory != null && !inventory.inventory.isEmpty()) {
						counts = new HashMap<>();
						for (EmiStack stack : inventory.inventory.values()) {
							add(counts, stack);
						}
						break;
					}
				} catch (Throwable t) {
					Nei.LOG.warn("[MaEMI] could not read screen stock from {}", handler.getClass().getSimpleName(), t);
				}
			}
		}
		if (counts == null) {
			counts = countInventory();
			return counts;
		}
		// Standard EMI handlers include the crafting grid in getInputSources (vanilla's handler
		// covers slots 1-45, AE2's covers player inventory plus its grid), so the grid is not
		// added again here. Only the no-handler fallback uses countInventory, which does add it.
		add(counts, screen.getScreenHandler().getCursorStack());
		return counts;
	}

	public static boolean isCraftingSlot(Slot slot) {
		if (slot == null || slot instanceof net.minecraft.screen.slot.CraftingResultSlot
				|| slot.inventory == null) {
			return false;
		}
		String slotClass = slot.getClass().getName();
		if (slotClass.contains("CraftingResultSlot") || slotClass.endsWith("CraftingTermSlot")) {
			return false;
		}
		String inventoryClass = slot.inventory.getClass().getName();
		return slot.inventory instanceof net.minecraft.inventory.CraftingInventory
				|| slotClass.contains("CraftingMatrixSlot")
				|| inventoryClass.contains("CraftingInventory")
				|| inventoryClass.contains("CraftingMatrix");
	}

	private static void addCraftingSlots(HandledScreen<?> screen, Map<String, Long> counts) {
		if (screen == null) {
			return;
		}
		for (Slot slot : screen.getScreenHandler().slots) {
			if (isCraftingSlot(slot)) {
				add(counts, slot.getStack());
			}
		}
	}

	private static void addPlayerInventory(Map<String, Long> counts) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
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
	}

	private static Map<String, Long> countInventory() {
		Map<String, Long> counts = new HashMap<>();
		if (MinecraftClient.getInstance().player == null) {
			return counts;
		}
		addPlayerInventory(counts);
		if (MinecraftClient.getInstance().currentScreen instanceof HandledScreen<?> screen) {
			addCraftingSlots(screen, counts);
			add(counts, screen.getScreenHandler().getCursorStack());
		}
		return counts;
	}

	/**
	 * Reads AE2's client repo without linking against AE2. Every storage/crafting terminal keeps a
	 * {@code repo} field with the synced network entries; only item keys are converted here.
	 */
	private static Map<String, Long> ae2NetworkStock(HandledScreen<?> screen) {
		try {
			Object repo = findField(screen, "repo");
			if (repo == null) {
				return null;
			}
			Object entriesObject = repo.getClass().getMethod("getAllEntries").invoke(repo);
			if (!(entriesObject instanceof Iterable<?> entries)) {
				return null;
			}
			Map<String, Long> counts = new HashMap<>();
			for (Object entry : entries) {
				Object key = entry.getClass().getMethod("getWhat").invoke(entry);
				if (key == null || !key.getClass().getName().equals("appeng.api.stacks.AEItemKey")) {
					continue;
				}
				Object storedObject = entry.getClass().getMethod("getStoredAmount").invoke(entry);
				if (!(storedObject instanceof Number stored) || stored.longValue() <= 0) {
					continue;
				}
				Object stackObject = key.getClass().getMethod("toStack").invoke(key);
				if (!(stackObject instanceof ItemStack stack) || stack.isEmpty()) {
					continue;
				}
				add(counts, EmiStack.of(stack).setAmount(stored.longValue()));
			}
			return counts;
		} catch (Throwable t) {
			Nei.LOG.warn("[MaEMI] could not read the AE2 network stock; falling back to the EMI handler", t);
			return null;
		}
	}

	private static Object findField(Object object, String name) throws ReflectiveOperationException {
		Class<?> type = object.getClass();
		while (type != null) {
			try {
				java.lang.reflect.Field field = type.getDeclaredField(name);
				field.setAccessible(true);
				return field.get(object);
			} catch (NoSuchFieldException ignored) {
				type = type.getSuperclass();
			}
		}
		return null;
	}

	private static void add(Map<String, Long> counts, EmiStack stack) {
		if (stack == null || stack.isEmpty() || stack.getAmount() <= 0) {
			return;
		}
		String key = stockKey(stack);
		if (!key.isEmpty()) {
			counts.merge(key, stack.getAmount(), Long::sum);
		}
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
		private String hint = "";
		private int attempts;

		private State(Map<String, Long> stock, Predicate<EmiRecipe> allowed) {
			this.stock = stock;
			this.allowed = allowed;
		}
	}

	private record RecipeOption(EmiRecipe recipe, int unreachable, int missing) {
	}
}