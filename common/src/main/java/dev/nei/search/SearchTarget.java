package dev.nei.search;

import java.util.List;

/**
 * Everything the MaEMI search language is allowed to look at, abstracted away from Minecraft so the
 * parser and its filters stay unit testable.
 */
public interface SearchTarget {
	String name();

	String modId();

	String modName();

	String identifier();

	List<String> tooltip();

	List<String> tags();
}
