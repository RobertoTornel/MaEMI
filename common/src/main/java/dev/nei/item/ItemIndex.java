package dev.nei.item;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;

/**
 * Read access to EMI's item index for the parts of MaEMI that are not a panel.
 *
 * <p>This used to live on the panel's item source, which meant anything that only needed "the list of
 * items EMI knows about" had to import the retired panel to get it. Keeping a copy helper here says
 * what it actually does and lets the panel be deleted without touching compat presets or dumps.</p>
 */
public final class ItemIndex {
	private ItemIndex() {
	}

	/**
	 * A stable copy of EMI's index, or an empty list when EMI is mid-reload.
	 *
	 * <p>EMI rebuilds this list on its reload thread, so iterating it live can throw
	 * {@link java.util.ConcurrentModificationException}. Copying is retried a couple of times because
	 * the rebuild is short, and an empty list is the honest answer when it never settles: callers here
	 * treat empty as "not ready yet" rather than "no items".</p>
	 */
	public static List<EmiStack> snapshot() {
		for (int attempt = 0; attempt < 3; attempt++) {
			try {
				List<EmiStack> stacks = EmiApi.getIndexStacks();
				return stacks == null ? List.of() : new ArrayList<>(stacks);
			} catch (java.util.ConcurrentModificationException racing) {
				// EMI is rebuilding; the next attempt usually sees a stable list.
			} catch (Throwable ignored) {
				break;
			}
		}
		return List.of();
	}
}
