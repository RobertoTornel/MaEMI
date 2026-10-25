package dev.nei.search;

/**
 * Mirrors MaEMI's {@code inventory.search.patternMode} option.
 */
public enum SearchMode {
	/** The query is a literal substring. */
	LITERAL,
	/** {@code *} and {@code ?} are wildcards, {@code r/.../} is a regular expression. */
	WILDCARD
}
