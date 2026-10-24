package dev.nei.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.nei.bookmark.BookmarkGroup;
import dev.nei.bookmark.Bookmarks;
import dev.nei.config.NeiConfig;
import dev.nei.compat.NeiCompat;
import dev.nei.config.NeiConfigData;
import dev.nei.item.HideList;
import dev.nei.item.ItemSubset;
import dev.nei.item.ItemSubsetList;
import dev.nei.search.NeiSearchParser;
import dev.nei.search.SearchFilter;
import dev.nei.search.StackSearchTarget;
import dev.nei.util.StackIO;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The item list behind the panel: picks the active source (the whole EMI index, the bookmarks or a
 * user subset), applies the search expression and the hide list, and sorts the result. The filtered
 * list is cached; rendering only reads it.
 */
public final class ItemPanelSource {
	private static final ItemPanelSource INSTANCE = new ItemPanelSource();

	private List<EmiStack> cached = List.of();
	private String signature = "";
	private long revision;

	private ItemPanelSource() {
	}

	public static ItemPanelSource get() {
		return INSTANCE;
	}

	/** Drops the cache. Call after any data source or config change. */
	public void invalidate() {
		revision++;
		signature = "";
	}

	public List<EmiStack> stacks() {
		NeiCompat.ensurePresets();
		NeiConfigData config = NeiConfig.get();
		String current = signature(config);
		if (current.equals(signature) && cached != null) {
			return cached;
		}
		List<EmiStack> built = build(config);
		// EMI rebuilds its index during startup and resource reloads; an empty index is not an
		// empty panel. Keep the last good list instead of flashing "No items to show".
		if (built.isEmpty() && !cached.isEmpty() && sourceIsIndex()
				&& EmiApi.getIndexStacks().isEmpty()) {
			return cached;
		}
		cached = Collections.unmodifiableList(built);
		signature = current;
		return cached;
	}

	public int size() {
		return stacks().size();
	}

	private boolean sourceIsIndex() {
		NeiPanelState state = NeiPanel.get().state();
		return state.selectedSubset() == null;
	}

	private String signature(NeiConfigData config) {
		NeiPanelState state = NeiPanel.get().state();
		StringBuilder builder = new StringBuilder(64);
		builder.append(System.identityHashCode(state)).append('|')
				.append(state.selectedSubset()).append('|')
				.append(state.search()).append('|')
				.append(config.panel.sort).append('|')
				.append(config.panel.showHiddenItems).append('|')
				.append(config.search.mode).append('|')
				.append(revision).append('|');
		try {
			builder.append(HideList.get().size());
		} catch (Throwable ignored) {
			builder.append('?');
		}
		builder.append('|').append(sourceHint(state));
		return builder.toString();
	}

	private String sourceHint(NeiPanelState state) {
		try {
			String subset = state.selectedSubset();
			if (NeiPanelState.BOOKMARKS.equals(subset)) {
				BookmarkGroup active = Bookmarks.get().active();
				int activeSize = active == null ? 0 : active.stacks().size();
				return "bookmarks:" + activeSize + "+" + Bookmarks.get().history().size();
			}
			if (subset != null) {
				ItemSubset itemSubset = ItemSubsetList.get().byName(subset);
				return itemSubset == null ? "missing:" + subset : "subset:" + itemSubset.stacks().size();
			}
			return "index:" + EmiApi.getIndexStacks().size();
		} catch (Throwable ignored) {
			return "unknown";
		}
	}

	private List<EmiStack> build(NeiConfigData config) {
		List<EmiStack> result = new ArrayList<>();
		List<EmiStack> source = sourceStacks(NeiPanel.get().state());

		SearchFilter filter = SearchFilter.ANY;
		try {
			filter = NeiSearchParser.parse(NeiPanel.get().state().search(), searchMode(config));
		} catch (RuntimeException ignored) {
			// An incomplete or invalid expression shows everything rather than nothing.
		}

		boolean showHidden = config.panel.showHiddenItems;
		HideList hideList = null;
		try {
			hideList = HideList.get();
		} catch (Throwable ignored) {
		}

		Set<String> seen = new HashSet<>();
		for (EmiStack stack : source) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			if (!showHidden && hideList != null) {
				try {
					if (hideList.isHidden(stack)) {
						continue;
					}
				} catch (Throwable ignored) {
				}
			}
			if (filter != SearchFilter.ANY) {
				try {
					if (!filter.matches(new StackSearchTarget(stack))) {
						continue;
					}
				} catch (Throwable ignored) {
					continue;
				}
			}
			String key;
			try {
				key = StackIO.key(stack);
			} catch (Throwable ignored) {
				key = null;
			}
			if (key != null && !seen.add(key)) {
				continue;
			}
			result.add(stack);
		}

		result.sort(comparator(config.panel.sort));
		return result;
	}

	private List<EmiStack> sourceStacks(NeiPanelState state) {
		try {
			String subset = state.selectedSubset();
			if (NeiPanelState.BOOKMARKS.equals(subset)) {
				List<EmiStack> result = new ArrayList<>();
				BookmarkGroup active = Bookmarks.get().active();
				if (active != null && active.stacks() != null) {
					result.addAll(active.stacks());
				}
				if (Bookmarks.get().history() != null) {
					result.addAll(Bookmarks.get().history());
				}
				return result;
			}
			if (subset != null) {
				ItemSubset itemSubset = ItemSubsetList.get().byName(subset);
				return itemSubset == null || itemSubset.stacks() == null ? List.of() : itemSubset.stacks();
			}
			List<EmiStack> stacks = EmiApi.getIndexStacks();
			return stacks == null ? List.of() : stacks;
		} catch (Throwable ignored) {
			return List.of();
		}
	}

	private static dev.nei.search.SearchMode searchMode(NeiConfigData config) {
		return config.search.mode == NeiConfigData.SearchMode.LITERAL
				? dev.nei.search.SearchMode.LITERAL
				: dev.nei.search.SearchMode.WILDCARD;
	}

	private static Comparator<EmiStack> comparator(NeiConfigData.Sort sort) {
		Comparator<EmiStack> byName = Comparator.comparing(ItemPanelSource::name, String.CASE_INSENSITIVE_ORDER);
		return switch (sort) {
			case NAME -> byName;
			case MOD -> Comparator.<EmiStack, String>comparing(ItemPanelSource::modId, String.CASE_INSENSITIVE_ORDER)
					.thenComparing(byName);
			case ID -> Comparator.<EmiStack, String>comparing(ItemPanelSource::identifier, String.CASE_INSENSITIVE_ORDER);
			case COUNT -> Comparator.<EmiStack>comparingLong(ItemPanelSource::amount).reversed().thenComparing(byName);
		};
	}

	private static String name(EmiStack stack) {
		try {
			Text text = stack.getName();
			return text == null ? "" : text.getString();
		} catch (Throwable ignored) {
			return "";
		}
	}

	private static String modId(EmiStack stack) {
		try {
			Identifier id = stack.getId();
			return id == null ? "" : id.getNamespace().toLowerCase(Locale.ROOT);
		} catch (Throwable ignored) {
			return "";
		}
	}

	private static String identifier(EmiStack stack) {
		try {
			Identifier id = stack.getId();
			return id == null ? "" : id.toString().toLowerCase(Locale.ROOT);
		} catch (Throwable ignored) {
			return "";
		}
	}

	private static long amount(EmiStack stack) {
		try {
			return stack.getAmount();
		} catch (Throwable ignored) {
			return 0L;
		}
	}
}