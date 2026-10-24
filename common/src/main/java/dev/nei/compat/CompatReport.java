package dev.nei.compat;

import java.util.List;

public record CompatReport(List<Entry> entries) {
	public record Entry(String id, List<String> requiredMods, boolean enabled, String reason, int subsetPresets) {
	}
}
