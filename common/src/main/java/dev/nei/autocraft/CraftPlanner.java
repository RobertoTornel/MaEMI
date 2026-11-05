package dev.nei.autocraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import dev.emi.emi.registry.EmiRecipeFiller;
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
	// A step is one craft. A deep machine (hull -> casing -> plate -> ingot, plus tools) is tens of
	// steps, not hundreds; the cap only stops a runaway plan.
	private static final int MAX_STEPS = 512;
	// Backtracking tries recipe and ingredient alternatives; cap failed exploration so a large
	// modpack recipe graph cannot stall the client thread.
	// Backtracking tries recipe and ingredient alternatives. The cap exists so a pathological graph
	// cannot spin forever; it is generous because a real chain is deep (machine -> casing -> plate
	// -> ingot) and each level multiplies the alternatives. Planning is time-sliced now, so a large
	// number here costs ticks, not a freeze.
	private static final int MAX_ATTEMPTS = 200_000;
	// Interchangeable tag alternatives are ordered best-first, so walking hundreds of them only
	// multiplies the work: a GT cable tag lists 144 tools and one plan used to try every one.
	private static final int MAX_OPTIONS_TRIED = 8;
	// Likewise for recipes of one item: the sorted list already puts usable ones first.
	private static final int MAX_RECIPES_TRIED = 12;
	// Diagnostic blocks are useful but the logging itself is part of the freeze: one failed plan
	// wrote 60k log lines. Cap it per request.
	private static final int DEBUG_LOG_LIMIT = 40;
	// "Craft all" binary-searches the largest amount; log2(512) is nine probes, which leaves room
	// for the odd extra without turning one key press into dozens of full plans.
	private static final int MAX_ALL_PROBES = 12;
	// How many alternatives of one ingredient get a full score. Scoring is a recipe lookup per
	// alternative and a GregTech tag can list hundreds; only the first few are ever tried, so a
	// bounded score keeps one planning step short enough to fit inside a single slice.
	private static final int MAX_OPTIONS_SCORED = 48;
	// Upper bound on how many candidate recipes one step examines, so a single step always fits
	// inside a slice. Real items are far below this; the largest seen in a Monifactory log was 458.
	private static final int MAX_CANDIDATES = 1024;

	private CraftPlanner() {
	}

	/**
	 * Starts planning {@code amount} crafts on top of what the player already carries. Pass {@code 0}
	 * for "as many as the stock allows".
	 *
	 * <p>Planning never blocks: {@link Job#tick(long)} advances it by a bounded slice of work and
	 * returns, so one request can be spread over as many client ticks as it needs. The previous
	 * implementation recursed until it had an answer and held the render thread for up to 25 seconds
	 * on a large modpack. A wall-clock budget only chose where to give up; it never stopped the stall,
	 * and it could not: recursion cannot be paused half-way.</p>
	 */
	public static Job job(EmiStack target, int amount, int cap, Predicate<EmiRecipe> allowed,
			Map<String, Long> stock) {
		return new Job(target, amount, cap, allowed, stock);
	}

	/** One request being planned across client ticks. */
	public static final class Job {
		/**
		 * Total compute allowed for one request, spread over ticks. Past this the answer is reported
		 * as "the tree was too large" rather than spinning forever.
		 */
		private static final long TOTAL_BUDGET_NANOS = 1_500_000_000L;

		private final EmiStack target;
		private final int requested;
		private final int cap;
		private final Predicate<EmiRecipe> allowed;
		private final Map<String, Long> stock;
		private long startedAt = System.nanoTime();

		private Planner planner;
		private int amount;
		private int phase;
		private int low;
		private int high;
		private int probes;
		private CraftPlan best;
		private CraftPlan result;
		private boolean done;
		private boolean gaveUp;
		/** Set once the full request has been retried as a single craft after running out of time. */
		private boolean retriedSmaller;
		/** Overrides the requested amount for the retry, without losing what was asked for. */
		private int requestedOverride;

		private Job(EmiStack target, int amount, int cap, Predicate<EmiRecipe> allowed,
				Map<String, Long> stock) {
			this.target = target;
			this.requested = amount <= 0 ? 0 : Math.max(1, amount);
			this.cap = Math.max(1, cap);
			this.allowed = allowed;
			this.stock = stock;
			if (target == null || target.isEmpty()) {
				this.result = new CraftPlan(EmiStack.EMPTY, Math.max(1, amount), List.of(),
					List.of("nothing selected"), false, List.of());
				this.done = true;
			}
		}

		/**
		 * Advances the request by at most {@code budgetNanos} of work.
		 *
		 * @return true when the job is finished; false when it needs another tick
		 */
		public boolean tick(long budgetNanos) {
			if (done) {
				return true;
			}
			long deadline = System.nanoTime() + Math.max(200_000L, budgetNanos);
			while (!done) {
				if (System.nanoTime() - startedAt > TOTAL_BUDGET_NANOS) {
					// Out of time. The plan in hand is incomplete, so it must not be handed back as if it
					// were an answer: an empty step list with an empty missing list reads as "you already
					// have enough" and the craft silently does nothing, which is exactly what happened.
					// Retry once with a single craft, which is a far smaller search, and only then admit
					// defeat.
					if (best != null) {
						// The craft-all search already found an amount that works; that is a real
						// answer, so hand it back instead of throwing the work away.
						result = best;
						done = true;
						break;
					}
					if (!retriedSmaller && amount > 1) {
						retriedSmaller = true;
						planner = null;
						phase = 0;
						requestedOverride = 1;
						startedAt = System.nanoTime();
						continue;
					}
					gaveUp = true;
					State state = planner == null ? null : planner.state;
					if (state != null) {
						state.timedOut = true;
					}
					result = planner == null ? null : finishPlan(target, amount, planner.state);
					if (result == null || (result.steps().isEmpty() && result.missing().isEmpty())) {
						// Never return "nothing to do" for a request that was never answered.
						result = new CraftPlan(target, Math.max(1, amount), List.of(), List.of(), true,
							List.of());
					}
					done = true;
					break;
				}
				if (planner == null && !startNext()) {
					break;
				}
				if (!planner.run(deadline)) {
					return false;
				}
				CraftPlan plan = finishPlan(target, amount, planner.state);
				planner = null;
				accept(plan);
				if (System.nanoTime() > deadline) {
					return done;
				}
			}
			return done;
		}

		public boolean isDone() {
			return done;
		}

		/** Whether the search was abandoned for taking too long, rather than failing on its merits. */
		public boolean gaveUp() {
			return gaveUp;
		}

		public CraftPlan result() {
			return result;
		}

		/** How many separate plans the "craft all" search ran, for the log. */
		public int probes() {
			return probes;
		}

		private boolean startNext() {
			switch (phase) {
				case 0:
					// "Craft all" starts at one, not at the cap. The cap-sized plan is the most expensive
					// search there is, and it was the first thing tried -- which is how a request for a
					// handful of items ran out of planning time and then did nothing at all.
					startPlan(requestedOverride > 0 ? requestedOverride : (requested > 0 ? requested : 1));
					return true;
				case 1:
					if (low <= high && probes < MAX_ALL_PROBES) {
						startPlan((low + high) >>> 1);
						return true;
					}
					phase = 2;
					return false;
				case 2:
					// Not even one can be made here; report the blocker for a single craft, which is
					// the honest answer, rather than the wish list of a five-hundred-fold request.
					startPlan(1);
					return true;
				default:
					done = true;
					return false;
			}
		}

		private void startPlan(int crafts) {
			amount = crafts;
			State state = new State(new HashMap<>(stock), allowed);
			state.hint = ToolIdentity.materialOf(target);
			if (target.getId() != null) {
				state.targetNamespace = target.getId().getNamespace();
			}
			long have = state.stock.getOrDefault(stockKey(target), 0L);
			planner = new Planner(state, Math.max(1, NeiConfig.get().autocraft.maxDepth));
			planner.push(new PlanFrame(target, have + Math.max(1, crafts), 0));
		}

		private void accept(CraftPlan plan) {
			if (requested > 0 || requestedOverride > 0) {
				result = plan;
				done = true;
				return;
			}
			switch (phase) {
				case 0 -> {
					if (plan.missing().isEmpty()) {
						// One is possible; search upwards for the largest amount that still is.
						best = plan;
						phase = 1;
						low = 2;
						high = cap;
						probes = 0;
					} else {
						// Not even one can be made; that answer is the honest one.
						result = plan;
						done = true;
					}
				}
				case 1 -> {
					probes++;
					if (plan.missing().isEmpty()) {
						best = plan;
						low = amount + 1;
					} else if (plan.timedOut()) {
						high = low - 1;
					} else {
						high = amount - 1;
					}
					if (low > high || probes >= MAX_ALL_PROBES) {
						if (best != null) {
							result = best;
							done = true;
						} else {
							phase = 2;
						}
					}
				}
				default -> {
					result = plan;
					done = true;
				}
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Frames: the recursive descent written so it can be paused.
	//
	// plan() looked at a recipe, craft() looked at its inputs, and planInput() looked at the
	// alternatives for one input; together they form a tree walk. Keeping that as recursion means the
	// walk must run to completion in one call, which is exactly the freeze. The same three steps are
	// therefore frames on an explicit stack: a frame either finishes with an answer or pushes a child
	// and yields, and the caller can stop between any two frames.
	// ---------------------------------------------------------------------------------------------

	private static final int YIELD = 0;
	private static final int DONE_OK = 1;
	private static final int DONE_FAIL = 2;

	private abstract static class Frame {
		private boolean hasChild;
		private boolean childOk;

		/** Advances one step: pushes a child and returns {@link #YIELD}, or finishes. */
		abstract int step(Planner planner);

		final void childDone(boolean ok) {
			this.hasChild = true;
			this.childOk = ok;
		}

		final boolean hasChild() {
			return hasChild;
		}

		final boolean childOk() {
			return childOk;
		}

		final void clearChild() {
			hasChild = false;
		}
	}

	/** Walks the frame stack within a deadline, and can be resumed exactly where it stopped. */
	static final class Planner {
		final State state;
		final Set<String> path = new HashSet<>();
		final int maxDepth;
		private final ArrayList<Frame> stack = new ArrayList<>();
		private long frames;

		Planner(State state, int maxDepth) {
			this.state = state;
			this.maxDepth = maxDepth;
		}

		void push(Frame frame) {
			stack.add(frame);
		}

		/**
		 * Runs frames until the stack empties or the deadline passes.
		 *
		 * @return true if the walk finished, false if it needs more time
		 */
		boolean run(long deadlineNanos) {
			while (!stack.isEmpty()) {
				// Checked every frame, because frame cost varies: a cached lookup is microseconds and a
				// prefilt over hundreds of candidates is milliseconds. nanoTime costs nothing next to
				// either, and checking only every n-th frame is how a slice silently grows.
				if (System.nanoTime() > deadlineNanos) {
					return false;
				}
				Frame top = stack.get(stack.size() - 1);
				int outcome = top.step(this);
				if (outcome == YIELD) {
					continue;
				}
				stack.remove(stack.size() - 1);
				if (stack.isEmpty()) {
					return true;
				}
				stack.get(stack.size() - 1).childDone(outcome == DONE_OK);
			}
			return true;
		}
	}

	/** {@code plan()}: obtain {@code needed} of one stack, crafting the shortfall. */
	private static final class PlanFrame extends Frame {
		private final EmiStack stack;
		private final long needed;
		private final int depth;

		private boolean entered;
		private String key;
		private List<RecipeOption> options;
		private int index;
		private int tried;
		private Set<String> bestFailure;
		private int bestSteps = -1;
		private Map<String, Long> stockBefore;
		private int stepsBefore;
		private Set<String> missingBefore;
		private boolean pathAdded;
		private boolean reusable;

		PlanFrame(EmiStack stack, long needed, int depth) {
			this.stack = stack;
			this.needed = needed;
			this.depth = depth;
		}

		@Override
		int step(Planner planner) {
			State state = planner.state;
			if (hasChild()) {
				clearChild();
				if (childOk()) {
					dropPath(planner);
					return DONE_OK;
				}
				int stepsPlanned = state.steps.size() - stepsBefore;
				Set<String> failure = new LinkedHashSet<>(state.missing);
				restore(state, stockBefore, stepsBefore, missingBefore);
				if (bestFailure == null || stepsPlanned > bestSteps) {
					bestFailure = failure;
					bestSteps = stepsPlanned;
				}
			}
			if (!entered) {
				entered = true;
				int outcome = enter(planner);
				if (outcome != YIELD) {
					return outcome;
				}
			}
			while (true) {
				if (index >= options.size() || tried >= MAX_RECIPES_TRIED) {
					if (bestFailure != null && !bestFailure.isEmpty()) {
						state.missing.addAll(bestFailure);
					} else {
						state.missing.add(describe(stack));
					}
					if (reusable) {
						// A tool that could not be made is the real blocker, and saying "missing
						// wrought_iron_plate" when the plate needs a hammer you do not have sends the
						// player looking for the wrong thing. Name the tool.
						state.unavailableTool = describe(stack);
						state.unavailableToolStack = stack;
					}
					dropPath(planner);
					return DONE_FAIL;
				}
				RecipeOption option = options.get(index++);
				tried++;
				stockBefore = new HashMap<>(state.stock);
				stepsBefore = state.steps.size();
				missingBefore = new LinkedHashSet<>(state.missing);
				planner.push(new CraftFrame(option.recipe(), stack, needed, depth));
				return YIELD;
			}
		}

		private int enter(Planner planner) {
			State state = planner.state;
			if (needed <= 0) {
				return DONE_OK;
			}
			key = stockKey(stack);
			long have = state.stock.getOrDefault(key, 0L);
			// Tools (GT hammers, files, wire cutters...) are reused between steps: one copy satisfies
			// any number of recipes and is never consumed by planning. The root target is exempt so
			// "craft one more" still plans a real craft.
			reusable = depth > 0 && isReusableTool(stack);
			long neededHere = reusable ? Math.min(needed, 1) : needed;
			if (have >= neededHere) {
				if (!reusable) {
					state.stock.put(key, have - neededHere);
				}
				return DONE_OK;
			}
			if (planner.path.contains(key)) {
				// A cycle back to an item already being planned along this path cannot resolve it.
				return DONE_FAIL;
			}
			if (++state.attempts > MAX_ATTEMPTS || depth >= planner.maxDepth
					|| state.steps.size() >= MAX_STEPS) {
				// Say which limit stopped it. "Missing" is a lie when the real cause is a depth cap,
				// and a deep chain (a machine, its casing, the plate, the ingot) is exactly where a
				// cap bites first.
				if (state.debugLogs > 0) {
					state.debugLogs--;
					Nei.LOG.info("[MaEMI] stopped at {}: attempts={} depth={}/{} steps={}/{}",
						describe(stack), state.attempts, depth, planner.maxDepth, state.steps.size(), MAX_STEPS);
				}
				state.missing.add(describe(stack));
				return DONE_FAIL;
			}
			// A missing tool is crafted like anything else -- a plan that needs a file and has none
			// must make one. What it must not do is make one per dependent craft, and that is handled
			// by the reusable clamp above (one copy satisfies any number of recipes) and by the
			// batch merge, which never asks for a second copy of a tool.
			planner.path.add(key);
			pathAdded = true;
			options = recipesFor(stack, needed, state, planner.path);
			if (options.isEmpty()) {
				state.missing.add(describe(stack));
				dropPath(planner);
				return DONE_FAIL;
			}
			index = 0;
			tried = 0;
			return YIELD;
		}

		private void dropPath(Planner planner) {
			if (pathAdded) {
				planner.path.remove(key);
				pathAdded = false;
			}
		}
	}

	/** {@code craft()}: run one recipe, obtaining each of its inputs first. */
	private static final class CraftFrame extends Frame {
		private final EmiRecipe recipe;
		private final EmiStack stack;
		private final long needed;
		private final int depth;

		private boolean entered;
		private List<EmiIngredient> inputs;
		private int index;
		private long batches;
		private String key;
		private boolean reusable;
		private long neededHere;
		private long perCraft;

		CraftFrame(EmiRecipe recipe, EmiStack stack, long needed, int depth) {
			this.recipe = recipe;
			this.stack = stack;
			this.needed = needed;
			this.depth = depth;
		}

		@Override
		int step(Planner planner) {
			State state = planner.state;
			if (hasChild()) {
				clearChild();
				if (!childOk()) {
					return DONE_FAIL;
				}
			}
			if (!entered) {
				entered = true;
				perCraft = producedAmount(recipe, stack);
				if (perCraft <= 0) {
					return DONE_FAIL;
				}
				key = stockKey(stack);
				long have = state.stock.getOrDefault(key, 0L);
				// A reusable tool is required once, not once per craft: five recipes that each ask
				// for a file must plan one file, not five.
				reusable = depth > 0 && isReusableTool(stack);
				neededHere = reusable ? Math.min(needed, 1) : needed;
				long shortfall = neededHere - have;
				if (shortfall <= 0) {
					return DONE_OK;
				}
				batches = shortfall / perCraft + (shortfall % perCraft == 0 ? 0 : 1);
				if (batches <= 0 || batches > Integer.MAX_VALUE) {
					state.missing.add(describe(stack));
					return DONE_FAIL;
				}
				inputs = recipe.getInputs();
				index = 0;
			}
			while (true) {
				EmiIngredient input = null;
				while (index < inputs.size()) {
					EmiIngredient candidate = inputs.get(index++);
					// Shaped recipes can carry empty grid slots; they are not ingredients to obtain.
					if (candidate != null && !candidate.isEmpty()) {
						input = candidate;
						break;
					}
				}
				if (input == null) {
					long produced = multiply(perCraft, batches);
					if (produced <= 0 || state.steps.size() >= MAX_STEPS) {
						state.missing.add(describe(stack));
						return DONE_FAIL;
					}
					long total = add(state.stock.getOrDefault(key, 0L), produced);
					if (total < neededHere) {
						state.missing.add(describe(stack));
						return DONE_FAIL;
					}
					state.stock.put(key, total - (reusable ? 0 : neededHere));
					// One step carrying the batch count. Execution fills once per batch, so a tool
					// ingredient is reused across them and the plan does not repeat the same recipe.
					state.steps.add(new CraftStep(recipe, (int) batches));
					return DONE_OK;
				}
				long required = multiply(Math.max(1, input.getAmount()), batches);
				if (required <= 0) {
					state.missing.add(describe(input));
					return DONE_FAIL;
				}
				planner.push(new InputFrame(input, required, depth + 1));
				return YIELD;
			}
		}
	}

	/** {@code planInput()}: satisfy one ingredient by any of its interchangeable stacks. */
	private static final class InputFrame extends Frame {
		private final EmiIngredient input;
		private final long needed;
		private final int depth;

		private boolean entered;
		private List<EmiStack> options;
		private int index;
		private int tried;
		private Set<String> bestFailure;
		private int bestSteps = -1;
		private Map<String, Long> stockBefore;
		private int stepsBefore;
		private Set<String> missingBefore;

		InputFrame(EmiIngredient input, long needed, int depth) {
			this.input = input;
			this.needed = needed;
			this.depth = depth;
		}

		@Override
		int step(Planner planner) {
			State state = planner.state;
			if (hasChild()) {
				clearChild();
				if (childOk()) {
					return DONE_OK;
				}
				int stepsPlanned = state.steps.size() - stepsBefore;
				Set<String> failure = new LinkedHashSet<>(state.missing);
				restore(state, stockBefore, stepsBefore, missingBefore);
				if (bestFailure == null || stepsPlanned > bestSteps) {
					bestFailure = failure;
					bestSteps = stepsPlanned;
				}
			}
			if (!entered) {
				entered = true;
				options = inputOptions(input, needed, state, planner.path);
				if (options.isEmpty()) {
					state.missing.add(describe(input));
					return DONE_FAIL;
				}
				if (options.size() > 1 && state.debugLogs > 0 && planner.path.size() <= 4) {
					state.debugLogs--;
					List<String> keys = new ArrayList<>();
					for (int i = 0; i < options.size() && i < 8; i++) {
						keys.add(stockKey(options.get(i)));
					}
					Nei.LOG.info("[MaEMI] tag input {}: {} option(s) {}", describe(input), options.size(), keys);
				}
			}
			while (true) {
				if (index >= options.size() || tried >= MAX_OPTIONS_TRIED) {
					if (bestFailure != null && !bestFailure.isEmpty() && bestSteps > 0) {
						state.missing.addAll(bestFailure);
					} else {
						// Nothing got anywhere, so the blocker is the ingredient itself rather than
						// one of its interchangeable alternatives. Naming the canonical stack keeps
						// the message useful: "tin wire" rather than whichever variant came first.
						state.missing.add(describe(input));
					}
					return DONE_FAIL;
				}
				EmiStack option = options.get(index++);
				tried++;
				stockBefore = new HashMap<>(state.stock);
				stepsBefore = state.steps.size();
				missingBefore = new LinkedHashSet<>(state.missing);
				planner.push(new PlanFrame(option, needed, depth));
				return YIELD;
			}
		}
	}

	/**
	 * Drops the candidate recipes that can never work for this stack in this screen: those that do
	 * not produce it, those this screen cannot fill, and recycling recipes that eat the very item
	 * they make. None of these depend on the stock, so the answer is cached per item.
	 */
	private static List<EmiRecipe> prefilter(EmiStack stack, State state) {
		List<EmiRecipe> candidates = RecipeRegistry.recipesFor(stack);
		if (candidates.size() > MAX_CANDIDATES) {
			// Bounds one planning step without ever dropping a real recipe: the largest candidate list
			// seen in a Monifactory log was 458.
			candidates = candidates.subList(0, MAX_CANDIDATES);
		}
		List<EmiRecipe> passing = new ArrayList<>(candidates.size());
		List<String> rejected = new ArrayList<>();
		for (EmiRecipe recipe : candidates) {
			if (recipe == null) {
				continue;
			}
			if (producedAmount(recipe, stack) <= 0) {
				rejected.add(recipeKey(recipe) + " (does not produce this stack)");
				continue;
			}
			// Every step has to run in the screen that is open right now, not just the target.
			if (!state.allowed.test(recipe)) {
				rejected.add(recipeKey(recipe) + " (not craftable in this screen)");
				continue;
			}
			// Recycling recipes take the item itself as input and give it back with zero net gain.
			if (consumesTarget(recipe, stack)) {
				rejected.add(recipeKey(recipe) + " (consumes the item it makes)");
				continue;
			}
			passing.add(recipe);
		}
		state.rejectedCandidates.put(keyFor(stack), rejected);
		return passing;
	}

	private static String keyFor(EmiStack stack) {
		String key = stockKey(stack);
		return key.isEmpty() ? describe(stack) : key;
	}

	private static List<RecipeOption> recipesFor(EmiStack stack, long needed, State state, Set<String> path) {
		if (state.seenStacks.size() < 512 && !stack.isEmpty()) {
			state.seenStacks.add(stack);
		}
		List<RecipeOption> options = new ArrayList<>();
		// The same item is asked about many times in one plan (every parent that needs it). The
		// candidate list and the static rejections do not depend on stock, so they are cached: the
		// filter below is the expensive part and repeating it was pure waste.
		List<EmiRecipe> candidates = state.filteredCandidates.get(keyFor(stack));
		boolean cached = candidates != null;
		if (!cached) {
			candidates = prefilter(stack, state);
			state.filteredCandidates.put(keyFor(stack), candidates);
		}
		// Only the checks that depend on the current stock and plan path are left, and those have to
		// run every time; everything above is already settled by the cache.
		for (EmiRecipe recipe : candidates) {
			if (recipe == null) {
				continue;
			}
			// Do not recursively plan uncrafting/splitting recipes if the input is not in stock
			if (isReverseRecipe(recipe, stack, state, path)) {
				continue;
			}
			options.add(estimate(recipe, stack, needed, state, path));
		}
		if (options.isEmpty() && !candidates.isEmpty() && state.debugLogs > 0 && path.size() <= 4) {
			state.debugLogs--;
			Nei.LOG.info("[MaEMI] no usable recipe for {} among {} candidate(s)", stockKey(stack), candidates.size());
			List<String> rejected = state.rejectedCandidates.getOrDefault(keyFor(stack), List.of());
			for (int i = 0; i < rejected.size() && i < 6; i++) {
				Nei.LOG.info("[MaEMI]   {}", rejected.get(i));
			}
		}
		// Deterministic order: reachable recipes first, then fewest items not in stock,
		// plain crafting table over machines, same-mod recipes, higher yield, and recipe identity.
		options.sort((a, b) -> {
			int byUnreachable = Integer.compare(a.unreachable(), b.unreachable());
			if (byUnreachable != 0) {
				return byUnreachable;
			}
			int byMissing = Integer.compare(a.missing(), b.missing());
			if (byMissing != 0) {
				return byMissing;
			}
			int byTool = Integer.compare(a.toolShortage(), b.toolShortage());
			if (byTool != 0) {
				return byTool;
			}
			boolean vanillaA = isVanillaCrafting(a.recipe());
			boolean vanillaB = isVanillaCrafting(b.recipe());
			if (vanillaA != vanillaB) {
				return vanillaA ? -1 : 1;
			}
			int modA = sameNamespace(a.recipe(), stack) ? 1 : 0;
			int modB = sameNamespace(b.recipe(), stack) ? 1 : 0;
			if (modA != modB) {
				return Integer.compare(modB, modA);
			}
			long prodA = producedAmount(a.recipe(), stack);
			long prodB = producedAmount(b.recipe(), stack);
			if (prodA != prodB) {
				return Long.compare(prodB, prodA);
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
	 * Returns true if this recipe needs an input that is not in stock and that is itself one of the
	 * blocked items: the target, or something already being planned on this path. Those are the
	 * uncrafting/splitting recipes (8x wire -> 1x wire, 9 ingots -> block, cable stripping): they
	 * cannot produce {@code target} from scratch, so they only count when the player already carries
	 * the composite input.
	 *
	 * <p>Only direct inputs are considered. Following the recipe graph any deeper used to flag
	 * perfectly ordinary recipes as "reverse" whenever some ancestor item happened to be reachable
	 * through a cycle, which is what made the mod report "no recipe is known" for items that have
	 * dozens of them.</p>
	 */
	private static boolean isReverseRecipe(EmiRecipe recipe, EmiStack target, State state, Set<String> path) {
		Set<String> blocked = new HashSet<>(path);
		blocked.add(stockKey(target));
		for (EmiIngredient input : recipe.getInputs()) {
			if (input == null || input.isEmpty()) {
				continue;
			}
			boolean inputSatisfiable = false;
			for (EmiStack candidate : input.getEmiStacks()) {
				if (candidate == null || candidate.isEmpty() || ToolIdentity.isTool(candidate)) {
					inputSatisfiable = true;
					break;
				}
				String key = stockKey(candidate);
				if (state.stock.getOrDefault(key, 0L) >= needFor(candidate, input.getAmount())) {
					inputSatisfiable = true;
					break;
				}
				if (!blocked.contains(key) && !onlyMadeFromBlocked(candidate, blocked, state)) {
					inputSatisfiable = true;
					break;
				}
			}
			if (!inputSatisfiable) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True when every recipe the open screen can run for {@code stack} consumes one of the blocked
	 * items directly. Only direct inputs count, and only recipes this screen can actually fill, so a
	 * machine recipe can never make an item look unreachable from a crafting table.
	 */
	private static boolean onlyMadeFromBlocked(EmiStack stack, Set<String> blocked, State state) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		String key = stockKey(stack);
		String cacheKey = key + "|" + String.join(",", new java.util.TreeSet<>(blocked));
		Boolean cached = state.reverseCache.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		boolean anyUsable = false;
		boolean result = true;
		for (EmiRecipe recipe : RecipeRegistry.recipesFor(stack)) {
			if (recipe == null || producedAmount(recipe, stack) <= 0 || consumesTarget(recipe, stack)
					|| !state.allowed.test(recipe)) {
				continue;
			}
			anyUsable = true;
			boolean requiresBlocked = false;
			for (EmiIngredient input : recipe.getInputs()) {
				if (input == null || input.isEmpty()) {
					continue;
				}
				boolean everyCandidateBlocked = true;
				for (EmiStack candidate : input.getEmiStacks()) {
					if (candidate == null || candidate.isEmpty() || ToolIdentity.isTool(candidate)
							|| !blocked.contains(stockKey(candidate))) {
						everyCandidateBlocked = false;
						break;
					}
				}
				if (everyCandidateBlocked) {
					requiresBlocked = true;
					break;
				}
			}
			if (!requiresBlocked) {
				result = false;
				break;
			}
		}
		result = anyUsable && result;
		state.reverseCache.put(cacheKey, result);
		return result;
	}


	/**
	 * Shallow score used only to order recipes; the recursive attempt decides whether one works.
	 * {@code missing} counts inputs not already in stock; {@code unreachable} counts inputs that
	 * are neither in stock nor craftable, so a craftable recipe is tried before an impossible one.
	 */
	private static RecipeOption estimate(EmiRecipe recipe, EmiStack stack, long needed, State state, Set<String> path) {
		long perCraft = producedAmount(recipe, stack);
		if (perCraft <= 0) {
			return new RecipeOption(recipe, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
		}
		long have = state.stock.getOrDefault(stockKey(stack), 0L);
		long shortfall = Math.max(0, needed - have);
		long batches = shortfall == 0 ? 1 : shortfall / perCraft + (shortfall % perCraft == 0 ? 0 : 1);
		int missing = 0;
		int unreachable = 0;
		int toolShortage = 0;
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
				if (isToolIngredient(input)) {
					// A recipe that needs a hammer you do not own is a worse plan than one that needs
					// nothing extra, even though both are "one item short".
					toolShortage++;
				}
			}
		}
		return new RecipeOption(recipe, unreachable, missing, toolShortage);
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
			// Whether the player already has it is a map lookup, so it is checked for every option.
			// Only the expensive part -- does it have a recipe, is that recipe craftable -- is capped,
			// because that is a recipe lookup per alternative and a GregTech tag can list hundreds.
			// Capping the whole score was a bug: a wire cutter tag lists 144 options, so a wire cutter
			// the player was carrying could fall past the cap, score lowest, and never be tried -- the
			// mod then asked for a different material's wire cutter.
			long have = state.stock.getOrDefault(key, 0L);
			if (have >= needFor(candidate, needed)) {
				tiers.put(key, 5);
			} else if (have > 0) {
				tiers.put(key, 4);
			} else if (options.size() <= MAX_OPTIONS_SCORED) {
				tiers.put(key, optionTier(candidate, needed, state, path));
			} else {
				tiers.put(key, 1);
			}
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
			int modA = matchesTargetMod(a, state) ? 2 : (isMinecraft(a) ? 1 : 0);
			int modB = matchesTargetMod(b, state) ? 2 : (isMinecraft(b) ? 1 : 0);
			if (modA != modB) {
				return Integer.compare(modB, modA);
			}
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
			return 5;
		}
		if (have > 0) {
			return 4;
		}
		if (path.contains(key)) {
			return 0;
		}
		if (shallowCraftable(stack, state, path)) {
			return 3;
		}
		if (hasRecipe(stack, state)) {
			return 2;
		}
		return 1;
	}

	private static boolean sameNamespace(EmiRecipe recipe, EmiStack stack) {
		try {
			if (recipe == null || stack == null) {
				return false;
			}
			Identifier recipeId = recipe.getId();
			Identifier stackId = stack.getId();
			if (recipeId == null || stackId == null) {
				return false;
			}
			return recipeId.getNamespace().equals(stackId.getNamespace());
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean matchesTargetMod(EmiStack stack, State state) {
		try {
			if (stack == null || stack.getId() == null || state.targetNamespace == null || state.targetNamespace.isEmpty()) {
				return false;
			}
			return state.targetNamespace.equals(stack.getId().getNamespace());
		} catch (Throwable t) {
			return false;
		}
	}

	private static boolean isMinecraft(EmiStack stack) {
		try {
			if (stack == null || stack.getId() == null) {
				return false;
			}
			return "minecraft".equals(stack.getId().getNamespace());
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * True when at least one allowed recipe for the stack has every input already in stock. Used
	 * only to order tag candidates: oak planks made from oak logs in your inventory should be tried
	 * before some modded plank whose log you have never seen.
	 */
	private static boolean shallowCraftable(EmiStack stack, State state, Set<String> path) {
		String key = stockKey(stack);
		Boolean cached = state.shallowCache.get(key);
		if (cached != null) {
			return cached;
		}
		boolean result = shallowCraftableUncached(stack, state);
		state.shallowCache.put(key, result);
		return result;
	}

	private static boolean shallowCraftableUncached(EmiStack stack, State state) {
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
			// Only recipes the open screen can run, and never the ones that eat the item itself.
			if (recipe != null && producedAmount(recipe, stack) > 0
					&& state.allowed.test(recipe) && !consumesTarget(recipe, stack)) {
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
		steps = mergeRepeats(steps);
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

	/**
	 * Folds repeated crafts of the same recipe into one step.
	 *
	 * <p>Recipes are explored per parent, so an ingredient shared by nine parents came out as
	 * nine separate "craft one plate" steps, and the plan for a single machine ran to 49 steps
	 * with seventeen identical ones. EMI fills a step with a batch count, so one step asking for
	 * nine does exactly the same work in a ninth of the clicks. Reusable tools are never merged
	 * past one: a second copy of a file is never wanted.</p>
	 */
	private static List<CraftStep> mergeRepeats(List<CraftStep> steps) {
		if (steps.size() < 2) {
			return steps;
		}
		List<CraftStep> merged = new ArrayList<>(steps.size());
		Map<String, Integer> indexByRecipe = new HashMap<>();
		for (CraftStep step : steps) {
			String key = recipeKey(step.recipe());
			Integer index = indexByRecipe.get(key);
			if (index == null) {
				indexByRecipe.put(key, merged.size());
				merged.add(step);
				continue;
			}
			CraftStep existing = merged.get(index);
			int batches = existing.batches() + step.batches();
			if (isToolOutput(step.recipe())) {
				// A tool is needed once; extra copies are exactly the "it keeps crafting files" bug.
				batches = Math.min(batches, 1);
			}
			merged.set(index, new CraftStep(existing.recipe(), batches));
		}
		return merged;
	}

	private static boolean isToolOutput(EmiRecipe recipe) {
		try {
			for (EmiStack output : recipe.getOutputs()) {
				if (output != null && !output.isEmpty() && ToolIdentity.isTool(output)) {
					return true;
				}
			}
		} catch (Throwable t) {
			return false;
		}
		return false;
	}

	private static long producedAmount(EmiRecipe recipe, EmiStack stack) {
		long amount = 0;
		String key = stockKey(stack);
		for (EmiStack output : recipe.getOutputs()) {
			if (output == null || output.isEmpty()) {
				continue;
			}
			// Identity is the item id, matching how stock is counted. EMI's own comparison includes
			// NBT, and that rejected every recipe for an item whose recipes all produce a tagged
			// variant: the Sophisticated Storage barrel the player hovered carries no woodType, so
			// all 32 of its recipes were logged as "does not produce this stack" and the mod
			// reported an item it could make from the materials at hand as impossible.
			if (!key.isEmpty() && key.equals(stockKey(output))) {
				amount = add(amount, Math.max(1, output.getAmount()));
			}
		}
		return amount;
	}

	/**
	 * Stock identity is the item id alone, with no NBT.
	 *
	 * <p>This used to key on {@link StackIO#key}, which appends a canonical form of the whole NBT
	 * compound. A recipe hands the planner the pristine stack, while the copy in a player's
	 * inventory carries whatever tags the world added to it, so the two never matched and the
	 * plan reported as missing an item the player was holding -- the log shows this verbatim:
	 * {@code missing gtceu:bronze_normal_fluid_pipe: 9 carried by the player (id match)}.</p>
	 *
	 * <p>Different GregTech materials are different <em>items</em> ({@code tin_plate} versus
	 * {@code iron_plate}), so dropping NBT does not merge materials. It is also what EMI itself
	 * does when it decides whether an inventory can fill a recipe.</p>
	 */
	private static String stockKey(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return "";
		}
		Identifier id = stack.getId();
		if (id == null) {
			return "";
		}
		return isReusableTool(stack) ? "tool:" + id : id.toString();
	}

	/**
	 * Stock the open screen can offer.
	 *
	 * <p>The player's own inventory is always counted, and every other slot the open menu has (the
	 * crafting grid, a station's storage, a machine's input slots) is added on top, each slot once.
	 * Neither a hard-coded slot range nor "whatever EMI's handler exposes" is trustworthy here: a
	 * modded crafting screen may put its own slots before the player's inventory, and EMI's
	 * fallback handler then hands back a slice of the menu that simply does not contain the items
	 * the player is carrying. That is how 22 tin cables in the inventory could be reported missing.</p>
	 */
	public static Map<String, Long> stockForScreen(HandledScreen<?> screen, List<EmiRecipe> recipes) {
		Map<String, Long> counts = new HashMap<>();
		// AE2 only exposes its network to EMI when the player turns that option on, and it is off by
		// default. Read the terminal's client-side repo directly so network items are stock
		// regardless of that setting; fall back to the screen's own slots if the repo cannot be read.
		if (screen != null && screen.getClass().getName().startsWith("appeng.")) {
			Map<String, Long> network = ae2NetworkStock(screen);
			if (network != null) {
				counts = network;
			}
		}
		addPlayerInventory(counts);
		if (screen != null) {
			addScreenSlots(screen, counts);
			add(counts, screen.getScreenHandler().getCursorStack());
			// Whatever EMI's handler for this screen can see is added on top, never instead: a
			// terminal's network, a backpack, a station's own storage. Replacing the player's
			// inventory with the handler's view is what once made 22 carried cables look missing.
			addHandlerStock(screen, recipes, counts);
		}
		return counts;
	}

	/**
	 * How many of {@code stack} the player is holding right now, counting the open screen's other
	 * slots too. Autocrafting uses this to confirm a step really produced its output: EMI's fill
	 * reports success when the request is sent, not when the server has placed the item.
	 */
	public static long carried(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		String key = stockKey(stack);
		if (key.isEmpty()) {
			return 0;
		}
		long total = 0;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			PlayerInventory inventory = client.player.getInventory();
			for (ItemStack held : inventory.main) {
				total += countOf(held, key);
			}
			for (ItemStack held : inventory.armor) {
				total += countOf(held, key);
			}
			for (ItemStack held : inventory.offHand) {
				total += countOf(held, key);
			}
		}
		if (client.currentScreen instanceof HandledScreen<?> open) {
			for (Slot slot : open.getScreenHandler().slots) {
				if (slot != null) {
					total += countOf(slot.getStack(), key);
				}
			}
		}
		return total;
	}

	private static long countOf(ItemStack stack, String key) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		return key.equals(stockKey(EmiStack.of(stack))) ? stack.getCount() : 0;
	}

	/**
	 * Adds everything the EMI recipe handler for this screen can draw from. This is how a storage
	 * terminal's network, a backpack or a station's own storage becomes usable stock; the player's
	 * inventory is already counted, so this can only add.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void addHandlerStock(HandledScreen<?> screen, List<EmiRecipe> recipes,
			Map<String, Long> counts) {
		if (recipes == null || recipes.isEmpty()) {
			return;
		}
		try {
			EmiRecipeHandler handler = EmiRecipeFiller.getFirstValidHandler(recipes.get(0), screen);
			if (handler == null) {
				return;
			}
			EmiPlayerInventory inventory = handler.getInventory(screen);
			if (inventory == null || inventory.inventory == null) {
				return;
			}
			for (EmiStack stack : inventory.inventory.values()) {
				if (stack == null || stack.isEmpty() || stack.getAmount() <= 0) {
					continue;
				}
				String key = stockKey(stack);
				if (key.isEmpty()) {
					continue;
				}
				// The handler's view normally includes the player's own slots, so summing would count
				// them twice and make the planner think it has twice what it has. The larger of the two
				// is the honest number: it adds a network or a backpack without inflating the rest.
				counts.merge(key, stack.getAmount(), Math::max);
			}
		} catch (Throwable t) {
			// A handler that cannot describe its inventory must not break planning; the player's own
			// slots are already counted.
			Nei.LOG.debug("[MaEMI] could not read the screen handler's inventory", t);
		}
	}

	/** Every slot of the open screen that is not part of the player's inventory or a result slot. */
	private static void addScreenSlots(HandledScreen<?> screen, Map<String, Long> counts) {
		MinecraftClient client = MinecraftClient.getInstance();
		for (Slot slot : screen.getScreenHandler().slots) {
			if (slot == null || slot instanceof net.minecraft.screen.slot.CraftingResultSlot) {
				continue;
			}
			// The player's inventory was counted once, up front.
			if (client.player != null && slot.inventory == client.player.getInventory()) {
				continue;
			}
			add(counts, slot.getStack());
		}
	}

	/**
	 * Turns a blocked item into the items that actually block it.
	 *
	 * <p>Backtracking reports whichever item it was standing on when it gave up, and that is
	 * usually an intermediate: the log shows a request for a steam turbine answered with
	 * "missing lv_machine_hull" three seconds after that same hull had been planned and crafted,
	 * and a wooden widget answered with "missing wooden_widget_right", a part with a perfectly
	 * good recipe. Naming an item the player can make is a lie about what is missing, so each
	 * entry is followed down to the ingredients that have no usable recipe at all.</p>
	 */
	private static void explainMissing(List<String> missing, State state) {
		Set<String> roots = new LinkedHashSet<>();
		int examined = 0;
		for (String entry : missing) {
			// Diagnostics, not the answer: a handful is enough to name the blocker, and walking a
			// hundred of them would put the cost back into a single tick.
			if (examined++ >= 8) {
				roots.add(entry);
				continue;
			}
			EmiStack stack = stackFor(entry, state);
			if (stack == null || stack.isEmpty()) {
				roots.add(entry);
				continue;
			}
			expandRoots(stack, state, roots, new HashSet<>(), 0);
		}
		missing.clear();
		missing.addAll(roots);
	}

	private static void expandRoots(EmiStack stack, State state, Set<String> roots, Set<String> seen, int depth) {
		String key = stockKey(stack);
		if (depth >= 8 || key.isEmpty() || !seen.add(key)) {
			roots.add(describe(stack));
			return;
		}
		if (ToolIdentity.isTool(stack)) {
			// A tool with no matching recipe is a missing tool, full stop.
			roots.add(describe(stack));
			return;
		}
		List<EmiRecipe> usable = new ArrayList<>();
		for (EmiRecipe recipe : RecipeRegistry.recipesFor(stack)) {
			if (recipe != null && producedAmount(recipe, stack) > 0 && state.allowed.test(recipe)
					&& !consumesTarget(recipe, stack)) {
				usable.add(recipe);
			}
		}
		if (usable.isEmpty()) {
			// Nothing here can make it, so this is the real blocker.
			roots.add(describe(stack));
			return;
		}
		// Prefer the recipe that is closest to being satisfiable: its unsatisfied inputs are the
		// likeliest root causes.
		EmiRecipe best = null;
		EmiIngredient worst = null;
		int fewest = Integer.MAX_VALUE;
		for (EmiRecipe recipe : usable) {
			int lacking = 0;
			EmiIngredient firstMissing = null;
			for (EmiIngredient input : recipe.getInputs()) {
				if (input == null || input.isEmpty()) {
					continue;
				}
				if (!available(input, Math.max(1, input.getAmount()), state)) {
					lacking++;
					if (firstMissing == null) {
						firstMissing = input;
					}
				}
			}
			if (lacking < fewest) {
				fewest = lacking;
				best = recipe;
				worst = firstMissing;
			}
		}
		if (best == null || fewest == 0) {
			// The plan failed here for a reason that is not a missing ingredient (a step limit, a
			// cycle, a recipe that cannot be filled in this screen). Say so plainly.
			roots.add(describe(stack));
			return;
		}
		if (worst != null) {
			EmiStack chosen = null;
			for (EmiStack candidate : worst.getEmiStacks()) {
				if (candidate != null && !candidate.isEmpty()
						&& className(stockKey(candidate)).equals(describe(stack))) {
					chosen = candidate;
					break;
				}
			}
			if (chosen == null) {
				for (EmiStack candidate : worst.getEmiStacks()) {
					if (candidate != null && !candidate.isEmpty()) {
						chosen = candidate;
						break;
					}
				}
			}
			if (chosen != null) {
				expandRoots(chosen, state, roots, seen, depth + 1);
				return;
			}
		}
		roots.add(describe(stack));
	}

	private static String className(String stockKey) {
		return stockKey.startsWith("tool:") ? stockKey.substring(5) : stockKey;
	}

	private static EmiStack stackFor(String id, State state) {
		for (EmiStack stack : state.seenStacks) {
			if (describe(stack).equals(id)) {
				return stack;
			}
		}
		return null;
	}

	/**
	 * When a plan fails, say how many of each missing item the player is actually carrying. A stock
	 * key that does not match (NBT on one side but not the other) is invisible from the outside, so
	 * the count and the keys the inventory offers are logged next to the name.
	 */
	public static void describeMissing(List<String> missing) {
		if (missing.isEmpty()) {
			return;
		}
		Map<String, Long> byId = new HashMap<>();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			for (ItemStack stack : client.player.getInventory().main) {
				countById(byId, stack);
			}
			for (ItemStack stack : client.player.getInventory().armor) {
				countById(byId, stack);
			}
			for (ItemStack stack : client.player.getInventory().offHand) {
				countById(byId, stack);
			}
		}
		if (client.currentScreen instanceof HandledScreen<?> screen) {
			// Only the screen's own slots: its menu also exposes the player's inventory, and
			// counting those twice reported 46 for a stack of 23, which made the diagnostic itself
			// look like a different bug.
			for (Slot slot : screen.getScreenHandler().slots) {
				if (slot == null || slot instanceof net.minecraft.screen.slot.CraftingResultSlot) {
					continue;
				}
				if (client.player != null && slot.inventory == client.player.getInventory()) {
					continue;
				}
				countById(byId, slot.getStack());
			}
		}
		Map<String, Long> rawKeys = countInventory();
		for (String id : missing) {
			long carried = byId.getOrDefault(id, 0L);
			Nei.LOG.info("[MaEMI] missing {}: {} carried by the player (id match)", id, carried);
			if (carried > 0) {
				for (Map.Entry<String, Long> entry : rawKeys.entrySet()) {
					if (entry.getKey().startsWith(id)) {
						Nei.LOG.info("[MaEMI]   carried under stock key {} x{}", entry.getKey(), entry.getValue());
					}
				}
			}
		}
	}

	private static void countById(Map<String, Long> counts, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		Identifier id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
		if (id != null) {
			counts.merge(id.toString(), (long) stack.getCount(), Long::sum);
		}
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

	private static CraftPlan finishPlan(EmiStack target, int requested, State state) {
		if (state.debugLogs > 0 && !state.steps.isEmpty()) {
			state.debugLogs--;
			CraftStep first = state.steps.get(0);
			Nei.LOG.info("[MaEMI] plan for {} starts with {} x{} ({} step(s) total)", describe(target),
				first.recipe().getId(), first.batches(), state.steps.size());
		}
		List<String> missing = new ArrayList<>(state.missing);
		if (!state.timedOut) {
			explainMissing(missing, state);
		}
		List<String> tools = state.unavailableTool.isEmpty() ? List.of()
			: List.of(ToolIdentity.kindOf(state.unavailableToolStack));
		return new CraftPlan(target, requested, orderSteps(state.steps), List.copyOf(missing), state.timedOut,
			tools);
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
		// The shallow cache is left alone on purpose: it only decides candidate order, and
		// clearing it on every failed branch made backtracking quadratic for no gain.
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
		private final Map<String, Boolean> shallowCache = new HashMap<>();
		private final Map<String, Boolean> reverseCache = new HashMap<>();
		/** Candidate recipes per item, already filtered down to those that can work at all. */
		private final Map<String, List<EmiRecipe>> filteredCandidates = new HashMap<>();
		private final Map<String, List<String>> rejectedCandidates = new HashMap<>();
		private final java.util.List<EmiStack> seenStacks = new ArrayList<>();
		private String hint = "";
		private String targetNamespace = "";
		private int attempts;
		private int debugLogs = DEBUG_LOG_LIMIT;
		private boolean timedOut;
		private String unavailableTool = "";
		private EmiStack unavailableToolStack = EmiStack.EMPTY;

		private State(Map<String, Long> stock, Predicate<EmiRecipe> allowed) {
			this.stock = stock;
			this.allowed = allowed;
		}
	}

	private record RecipeOption(EmiRecipe recipe, int unreachable, int missing, int toolShortage) {
	}

	/** True when every alternative for this ingredient is a tool. */
	private static boolean isToolIngredient(EmiIngredient input) {
		boolean any = false;
		for (EmiStack candidate : input.getEmiStacks()) {
			if (candidate == null || candidate.isEmpty()) {
				continue;
			}
			if (!ToolIdentity.isTool(candidate)) {
				return false;
			}
			any = true;
		}
		return any;
	}
}