package dev.nei.integration;

import java.util.List;

import com.google.common.collect.Lists;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarType;
import dev.nei.Nei;
import dev.nei.config.NeiConfig;
import dev.nei.config.NeiConfigData;

/**
 * Optional interoperability with EMI's own sidebars. When the player asks for it, the NEI panel
 * takes over the side of the screen where EMI shows its index, which is where NEI's panel lived
 * historically. Nothing is changed unless {@code panel.replaceEmiIndex} is enabled.
 */
public final class EmiPanelBridge {
	private static boolean indexRemoved;
	private static NeiConfigData.Side sideWithRemovedIndex;

	private EmiPanelBridge() {
	}

	public static void sync() {
		// The custom panel is retired, so EMI's index must never be hidden or replaced. If an
		// older build removed it, put it back now.
		if (indexRemoved) {
			restoreIndex();
		}
	}

	private static void removeIndex(NeiConfigData.Side side) {
		try {
			SidebarPages pages = pagesFor(side);
			if (pages == null) {
				return;
			}
			boolean removed = pages.pages.removeIf(page -> page.type == SidebarType.INDEX);
			indexRemoved = removed;
			sideWithRemovedIndex = side;
			Nei.LOG.info("[NEI] {} EMI index sidebar on the {}", removed ? "hid" : "kept", side.name().toLowerCase());
		} catch (Throwable t) {
			Nei.LOG.warn("[NEI] could not adjust EMI's sidebar (EMI version mismatch?)", t);
		}
	}

	private static void restoreIndex() {
		try {
			SidebarPages pages = pagesFor(sideWithRemovedIndex);
			if (pages != null && pages.pages.stream().noneMatch(page -> page.type == SidebarType.INDEX)) {
				pages.pages.add(0, new SidebarPages.SidebarPage(SidebarType.INDEX));
			}
		} catch (Throwable t) {
			Nei.LOG.warn("[NEI] could not restore EMI's index sidebar", t);
		}
		indexRemoved = false;
		sideWithRemovedIndex = null;
	}

	private static SidebarPages pagesFor(NeiConfigData.Side side) {
		return side == NeiConfigData.Side.LEFT ? EmiConfig.leftSidebarPages : EmiConfig.rightSidebarPages;
	}
}