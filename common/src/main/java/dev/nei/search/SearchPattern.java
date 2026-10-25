package dev.nei.search;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Compiles MaEMI search text exactly the way MaEMI's {@code SearchField.getPattern} does.
 */
public final class SearchPattern {
	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	private SearchPattern() {
	}

	public static Pattern compile(String raw, SearchMode mode) {
		if (raw == null || raw.isEmpty()) {
			return null;
		}
		if (mode == SearchMode.LITERAL) {
			return Pattern.compile(Pattern.quote(raw), FLAGS);
		}
		String expression = raw;
		if (expression.length() >= 3 && expression.startsWith("r/") && expression.endsWith("/")) {
			expression = expression.substring(2, expression.length() - 1);
		} else {
			Matcher matcher = Pattern.compile("([?*])").matcher(expression);
			StringBuilder out = new StringBuilder();
			int last = 0;
			while (matcher.find()) {
				out.append(Pattern.quote(expression.substring(last, matcher.start())));
				out.append(matcher.group(1).charAt(0) == '?' ? "." : ".+?");
				last = matcher.end();
			}
			out.append(Pattern.quote(expression.substring(last)));
			expression = out.toString();
		}
		try {
			return Pattern.compile(expression, FLAGS);
		} catch (PatternSyntaxException e) {
			return null;
		}
	}

	public static boolean test(Pattern pattern, String haystack) {
		return pattern != null && haystack != null && pattern.matcher(haystack).find();
	}
}
