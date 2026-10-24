package dev.nei.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Recursive descent parser for NEI's search expression language.
 *
 * <pre>
 * expression := or
 * or         := and ('|' and)*
 * and        := unary+
 * unary      := ('-' | '!') unary | primary
 * primary    := '{' expression '}' | term
 * term       := ('@' | '$' | '#' | '&amp;')? (quoted | regex | bare)
 * </pre>
 *
 * {@code @} matches the mod id/name, {@code $} matches item tags (NEI's ore dictionary),
 * {@code #} matches tooltip lines and {@code &} matches the registry identifier.
 */
public final class NeiSearchParser {
	private final String input;
	private final SearchMode mode;
	private int pos;

	private NeiSearchParser(String input, SearchMode mode) {
		this.input = input;
		this.mode = mode;
	}

	public static SearchFilter parse(String input, SearchMode mode) {
		if (input == null || input.isBlank()) {
			return SearchFilter.ANY;
		}
		NeiSearchParser parser = new NeiSearchParser(input, mode);
		SearchFilter filter = parser.parseOr();
		parser.skipSpaces();
		if (!parser.eof()) {
			throw new SearchParseException("Unexpected '" + parser.peek() + "'", parser.pos);
		}
		return filter;
	}

	private SearchFilter parseOr() {
		List<SearchFilter> parts = new ArrayList<>();
		parts.add(parseAnd());
		skipSpaces();
		while (!eof() && peek() == '|') {
			pos++;
			parts.add(parseAnd());
			skipSpaces();
		}
		return parts.size() == 1 ? parts.get(0) : new Or(parts);
	}

	private SearchFilter parseAnd() {
		List<SearchFilter> parts = new ArrayList<>();
		skipSpaces();
		while (!eof() && peek() != '|' && peek() != '}') {
			parts.add(parseUnary());
			skipSpaces();
		}
		if (parts.isEmpty()) {
			return SearchFilter.ANY;
		}
		return parts.size() == 1 ? parts.get(0) : new And(parts);
	}

	private SearchFilter parseUnary() {
		skipSpaces();
		if (!eof() && (peek() == '-' || peek() == '!')) {
			pos++;
			return new Not(parseUnary());
		}
		return parsePrimary();
	}

	private SearchFilter parsePrimary() {
		skipSpaces();
		if (eof()) {
			throw new SearchParseException("Expected a search term", pos);
		}
		if (peek() == '{') {
			pos++;
			SearchFilter inner = parseOr();
			skipSpaces();
			if (eof() || peek() != '}') {
				throw new SearchParseException("Unclosed '{'", pos);
			}
			pos++;
			return inner;
		}
		return parseTerm();
	}

	private SearchFilter parseTerm() {
		skipSpaces();
		char prefix = 0;
		if (!eof() && isPrefix(peek())) {
			prefix = peek();
			pos++;
		}
		boolean quoted = false;
		boolean regex = false;
		StringBuilder text = new StringBuilder();
		if (!eof() && peek() == '"') {
			quoted = true;
			pos++;
			while (!eof() && peek() != '"') {
				if (peek() == '\\' && pos + 1 < input.length()) {
					pos++;
				}
				text.append(input.charAt(pos++));
			}
			if (eof()) {
				throw new SearchParseException("Unclosed quote", pos);
			}
			pos++;
		} else if (!eof() && peek() == 'r' && pos + 1 < input.length() && input.charAt(pos + 1) == '/') {
			regex = true;
			pos += 2;
			while (!eof() && peek() != '/') {
				if (peek() == '\\' && pos + 1 < input.length()) {
					text.append(input.charAt(pos++));
				}
				text.append(input.charAt(pos++));
			}
			if (eof()) {
				throw new SearchParseException("Unclosed regular expression", pos);
			}
			pos++;
		} else {
			while (!eof()) {
				char c = peek();
				if (Character.isWhitespace(c) || c == '|' || c == '{' || c == '}') {
					break;
				}
				if (c == '\\' && pos + 1 < input.length()) {
					pos++;
					text.append(input.charAt(pos++));
					continue;
				}
				text.append(c);
				pos++;
			}
		}
		if (text.isEmpty()) {
			throw new SearchParseException("Empty search term", pos);
		}
		SearchMode effective = quoted ? SearchMode.LITERAL : mode;
		String value = text.toString();
		if (regex && !value.startsWith("r/")) {
			value = "r/" + value + "/";
		}
		return build(prefix, value, effective);
	}

	private SearchFilter build(char prefix, String value, SearchMode effective) {
		Pattern pattern = SearchPattern.compile(value, effective);
		if (pattern == null) {
			return SearchFilter.NONE;
		}
		String lower = value.toLowerCase(Locale.ROOT);
		return switch (prefix) {
			case '@' -> t -> SearchPattern.test(pattern, t.modId()) || SearchPattern.test(pattern, t.modName());
			case '$' -> t -> {
				for (String tag : t.tags()) {
					if (SearchPattern.test(pattern, tag)) {
						return true;
					}
				}
				return false;
			};
			case '#' -> t -> {
				for (String line : t.tooltip()) {
					if (SearchPattern.test(pattern, line)) {
						return true;
					}
				}
				return false;
			};
			case '&' -> t -> SearchPattern.test(pattern, t.identifier());
			default -> t -> SearchPattern.test(pattern, t.name())
				|| SearchPattern.test(pattern, t.identifier())
				// NEI searches the modid implicitly when the query looks like one
				|| (lower.indexOf(':') >= 0 && SearchPattern.test(pattern, t.modId() + ":" + t.identifier()));
		};
	}

	private static boolean isPrefix(char c) {
		return c == '@' || c == '$' || c == '#' || c == '&';
	}

	private void skipSpaces() {
		while (!eof() && Character.isWhitespace(peek())) {
			pos++;
		}
	}

	private boolean eof() {
		return pos >= input.length();
	}

	private char peek() {
		return input.charAt(pos);
	}

	record And(List<SearchFilter> parts) implements SearchFilter {
		@Override
		public boolean matches(SearchTarget target) {
			for (SearchFilter part : parts) {
				if (!part.matches(target)) {
					return false;
				}
			}
			return true;
		}
	}

	record Or(List<SearchFilter> parts) implements SearchFilter {
		@Override
		public boolean matches(SearchTarget target) {
			for (SearchFilter part : parts) {
				if (part.matches(target)) {
					return true;
				}
			}
			return false;
		}
	}

	record Not(SearchFilter inner) implements SearchFilter {
		@Override
		public boolean matches(SearchTarget target) {
			return !inner.matches(target);
		}
	}
}
