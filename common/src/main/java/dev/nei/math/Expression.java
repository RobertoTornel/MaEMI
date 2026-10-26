package dev.nei.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Evaluates a plain arithmetic expression typed into EMI's search bar.
 *
 * <p>This exists so the search box doubles as a calculator: typing
 * {@code ((232134/23)*29+2.3)-90000000000000} shows its value while the query is typed. It is a
 * hand-written recursive-descent parser over {@link BigDecimal} rather than a script engine, because
 * this runs on every keystroke from the render thread: it must not spin up a runtime, must not be
 * able to execute code, and must never throw into the search field. A rejected expression simply has
 * no result, which is also why nothing here throws.</p>
 *
 * <p>BigDecimal is deliberate. Doubles would print {@code -9.0E13} for a value a player expects to
 * read as {@code -90,000,000,000,000}, and would silently lose the low digits of a long expression.
 * The trade-off is that division has to stop somewhere, so the intermediate precision is bounded and
 * only the display is rounded.</p>
 */
public final class Expression {
	/** Enough digits that ordinary division looks right, small enough to stay cheap per keystroke. */
	private static final MathContext PRECISION = new MathContext(34, RoundingMode.HALF_EVEN);
	/** The search field caps its own text length, but a pasted expression should be rejected early. */
	private static final int MAX_INPUT = 256;
	/** Guards against a pathological "((((((..." paste turning into a stack overflow. */
	private static final int MAX_DEPTH = 32;
	/** 10^999 is already unreadable; refusing beyond it keeps a stray "9^999999999" from hanging. */
	private static final int MAX_POWER = 999;
	/** Past ±10^21 a grouped plain number is less readable than scientific notation. */
	private static final int MAX_PLAIN_EXPONENT = 21;
	/** Fractions longer than this are noise in a 160px search field. */
	private static final int MAX_FRACTION_DIGITS = 12;
	private static final int MAX_SCIENTIFIC_DIGITS = 10;

	private Expression() {
	}

	/**
	 * The number the text evaluates to, or empty when it is not a complete arithmetic expression.
	 * Pure: no config, no client state, no Minecraft classes, so it is unit-testable on its own.
	 */
	public static Optional<BigDecimal> value(String input) {
		try {
			if (input == null) {
				return Optional.empty();
			}
			String text = input.trim();
			if (text.isEmpty() || text.length() > MAX_INPUT || !looksArithmetic(text)) {
				return Optional.empty();
			}
			Parser parser = new Parser(text);
			BigDecimal result = parser.parseExpression(0);
			parser.skipWhitespace();
			if (!parser.atEnd() || result == null) {
				return Optional.empty();
			}
			return Optional.of(result);
		} catch (Throwable ignored) {
			// Division by zero, an out-of-range power, a stack overflow: all just mean "not a result".
			return Optional.empty();
		}
	}

	/** The display form the search bar and the result window show, or empty for a non-expression. */
	public static Optional<String> evaluate(String input) {
		return value(input).map(Expression::format);
	}

	/**
	 * Human-readable number: grouped thousands, no trailing {@code .0}, bounded fraction, and
	 * scientific notation only when the value is genuinely outside the range people read.
	 */
	public static String format(BigDecimal value) {
		if (value == null) {
			return "";
		}
		BigDecimal number = value.stripTrailingZeros();
		if (number.signum() == 0) {
			// "-0" and "0.000" are both just zero once the expression is evaluated.
			return "0";
		}
		boolean negative = number.signum() < 0;
		BigDecimal magnitude = number.abs();

		// The scientific exponent: 1234.5 -> 3, 0.0012 -> -3.
		int exponent = magnitude.precision() - magnitude.scale() - 1;
		if (exponent > MAX_PLAIN_EXPONENT || exponent < -MAX_PLAIN_EXPONENT) {
			return (negative ? "-" : "") + scientific(magnitude);
		}

		if (magnitude.scale() > MAX_FRACTION_DIGITS) {
			magnitude = magnitude.setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_UP).stripTrailingZeros();
		}
		if (magnitude.scale() < 0) {
			magnitude = magnitude.setScale(0);
		}

		String plain = magnitude.toPlainString();
		int dot = plain.indexOf('.');
		String integerPart = dot < 0 ? plain : plain.substring(0, dot);
		String fractionPart = dot < 0 ? "" : plain.substring(dot + 1);
		StringBuilder out = new StringBuilder();
		if (negative) {
			out.append('-');
		}
		out.append(group(integerPart));
		if (!fractionPart.isEmpty()) {
			out.append('.').append(fractionPart);
		}
		return out.toString();
	}

	private static String group(String digits) {
		if (digits.length() <= 3) {
			return digits;
		}
		StringBuilder out = new StringBuilder(digits.length() + digits.length() / 3);
		int head = digits.length() % 3;
		if (head > 0) {
			out.append(digits, 0, head);
		}
		for (int i = head; i < digits.length(); i += 3) {
			if (out.length() > 0) {
				out.append(',');
			}
			out.append(digits, i, i + 3);
		}
		return out.toString();
	}

	private static String scientific(BigDecimal magnitude) {
		BigDecimal rounded = magnitude.round(new MathContext(MAX_SCIENTIFIC_DIGITS, RoundingMode.HALF_UP))
				.stripTrailingZeros();
		String text = rounded.toString();
		if (text.indexOf('E') >= 0) {
			return text;
		}
		// BigDecimal only switches to E notation outside its own scale window; build it by hand so the
		// output is scientific for every absurd exponent.
		int exponent = rounded.precision() - rounded.scale() - 1;
		BigDecimal mantissa = rounded.movePointLeft(exponent).stripTrailingZeros();
		return mantissa.toPlainString() + "E" + (exponent >= 0 ? "+" : "") + exponent;
	}

	/**
	 * Cheap first pass so a normal search query ("iron ingot", "@gregtech", "$forge:ingots") never
	 * reaches the parser at all. Returns true only for arithmetic characters and at least one digit.
	 */
	private static boolean looksArithmetic(String text) {
		boolean digit = false;
		for (int i = 0; i < text.length(); i++) {
			char chr = text.charAt(i);
			if (chr >= '0' && chr <= '9') {
				digit = true;
				continue;
			}
			switch (chr) {
				case ' ', '\t', '.', '+', '-', '*', '/', '%', '^', '(', ')' -> {
				}
				default -> {
					return false;
				}
			}
		}
		return digit;
	}

	/**
	 * Recursive descent over the usual precedence ladder. Every method returns null for "not an
	 * expression" instead of throwing, so the caller only has to check the final result.
	 */
	private static final class Parser {
		private final String text;
		private int index;

		Parser(String text) {
			this.text = text;
		}

		BigDecimal parseExpression(int depth) {
			if (depth > MAX_DEPTH) {
				return null;
			}
			BigDecimal left = parseTerm(depth);
			if (left == null) {
				return null;
			}
			while (true) {
				skipWhitespace();
				if (consume('+')) {
					BigDecimal right = parseTerm(depth);
					if (right == null) {
						return null;
					}
					left = left.add(right, PRECISION);
				} else if (consume('-')) {
					BigDecimal right = parseTerm(depth);
					if (right == null) {
						return null;
					}
					left = left.subtract(right, PRECISION);
				} else {
					return left;
				}
			}
		}

		private BigDecimal parseTerm(int depth) {
			BigDecimal left = parseUnary(depth);
			if (left == null) {
				return null;
			}
			while (true) {
				skipWhitespace();
				// "**" has to be claimed here, before the single "*" below, or it would be read as a
				// multiplication followed by a stray star.
				if (peek("**")) {
					index += 2;
					BigDecimal exponent = parseUnary(depth);
					if (exponent == null) {
						return null;
					}
					left = power(left, exponent);
					if (left == null) {
						return null;
					}
				} else if (consume('*')) {
					BigDecimal right = parseUnary(depth);
					if (right == null) {
						return null;
					}
					left = left.multiply(right, PRECISION);
				} else if (consume('/')) {
					BigDecimal right = parseUnary(depth);
					if (right == null || right.signum() == 0) {
						return null;
					}
					left = left.divide(right, PRECISION);
				} else if (consume('%')) {
					BigDecimal right = parseUnary(depth);
					if (right == null || right.signum() == 0) {
						return null;
					}
					left = left.remainder(right, PRECISION);
				} else {
					return left;
				}
			}
		}

		private BigDecimal parseUnary(int depth) {
			if (depth > MAX_DEPTH) {
				return null;
			}
			skipWhitespace();
			if (consume('-')) {
				BigDecimal value = parseUnary(depth + 1);
				return value == null ? null : value.negate();
			}
			if (consume('+')) {
				return parseUnary(depth + 1);
			}
			return parsePower(depth);
		}

		/** Right-associative power, so {@code 2^3^2} is {@code 512}. */
		private BigDecimal parsePower(int depth) {
			BigDecimal base = parsePrimary(depth);
			if (base == null) {
				return null;
			}
			skipWhitespace();
			if (consume('^')) {
				BigDecimal exponent = parseUnary(depth + 1);
				if (exponent == null) {
					return null;
				}
				return power(base, exponent);
			}
			return base;
		}

		private BigDecimal power(BigDecimal base, BigDecimal exponent) {
			BigDecimal stripped = exponent.stripTrailingZeros();
			// A fractional exponent would need doubles and would print noise; treat it as not a result.
			if (stripped.scale() > 0) {
				return null;
			}
			if (stripped.abs().compareTo(BigDecimal.valueOf(MAX_POWER)) > 0) {
				return null;
			}
			int power = stripped.intValueExact();
			if (power < 0) {
				if (base.signum() == 0) {
					return null;
				}
				return BigDecimal.ONE.divide(base.pow(-power, PRECISION), PRECISION);
			}
			return base.pow(power, PRECISION);
		}

		private BigDecimal parsePrimary(int depth) {
			skipWhitespace();
			if (consume('(')) {
				BigDecimal inner = parseExpression(depth + 1);
				if (inner == null) {
					return null;
				}
				skipWhitespace();
				if (!consume(')')) {
					return null;
				}
				return inner;
			}
			return parseNumber();
		}

		private BigDecimal parseNumber() {
			skipWhitespace();
			int start = index;
			boolean digits = false;
			boolean dot = false;
			while (index < text.length()) {
				char chr = text.charAt(index);
				if (chr >= '0' && chr <= '9') {
					digits = true;
					index++;
				} else if (chr == '.' && !dot) {
					dot = true;
					index++;
				} else {
					break;
				}
			}
			if (!digits) {
				index = start;
				return null;
			}
			try {
				return new BigDecimal(text.substring(start, index));
			} catch (NumberFormatException ignored) {
				index = start;
				return null;
			}
		}

		private boolean consume(char chr) {
			if (index < text.length() && text.charAt(index) == chr) {
				index++;
				return true;
			}
			return false;
		}

		private boolean peek(String token) {
			return text.startsWith(token, index);
		}

		void skipWhitespace() {
			while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
				index++;
			}
		}

		boolean atEnd() {
			return index >= text.length();
		}
	}
}
