package dev.nei.search;

@FunctionalInterface
public interface SearchFilter {
	SearchFilter ANY = target -> true;
	SearchFilter NONE = target -> false;

	boolean matches(SearchTarget target);
}
