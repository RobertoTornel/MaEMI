package dev.nei.integration;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarType;
import dev.nei.Nei;

/**
 * Guarantees that MaEMI never hides EMI's item index.
 *
 * <p>Older builds could remove EMI's index sidebar to make room for MaEMI's own panel. That panel is
 * gone, so nothing here removes anything: the only remaining job is the clean-up half — if a config
 * written by an older version left the index missing, put it back.</p>
 */
public final class EmiPanelBridge {
	private static boolean checked;

	private EmiPanelBridge() {
	}

	/**
	 * Restores EMI's index on both sides when it is missing. Idempotent and cheap; called once at EMI
	 * registration.
	 */
	public static void sync() {
		if (checked) {
			return;
		}
		checked = true;
		restore(EmiConfig.leftSidebarPages);
		restore(EmiConfig.rightSidebarPages);
	}

	private static void restore(SidebarPages pages) {
		try {
			if (pages == null || pages.pages == null) {
				return;
			}
			if (pages.pages.stream().anyMatch(page -> page.type == SidebarType.INDEX)) {
				return;
			}
			pages.pages.add(0, new SidebarPages.SidebarPage(SidebarType.INDEX));
			Nei.LOG.info("[MaEMI] restored EMI's index sidebar");
		} catch (Throwable t) {
			// A different EMI layout is not a reason to fail startup; the index is simply left alone.
			Nei.LOG.warn("[MaEMI] could not verify EMI's index sidebar (EMI version mismatch?)", t);
		}
	}
}
