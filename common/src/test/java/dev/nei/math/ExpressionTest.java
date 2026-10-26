package dev.nei.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Covers the calculator behind EMI's search bar. The point of these tests is that the search field
 * must never show a wrong number and must never throw: a rejected expression is an absent result, not
 * an exception, and that distinction is asserted everywhere below.
 */
class ExpressionTest {
	/** The expression named in the brief; its exact value matters, not just its magnitude. */
	private static final String BRIEF = "((232134/23)*29+2.3)-90000000000000";

	private static String format(String input) {
		return Expression.evaluate(input).orElse("<none>");
	}

	private static boolean rejected(String input) {
		return Expression.value(input).isEmpty();
	}

	@Test
	void evaluatesTheBriefExpression() {
		Optional<BigDecimal> value = Expression.value(BRIEF);
		assertTrue(value.isPresent(), "the brief's expression must evaluate");
		assertEquals(0, new BigDecimal("-89999999707307.00434782608695652174").compareTo(value.get()));
	}

	@Test
	void formatsTheBriefExpressionAsGroupedPlainDigits() {
		String shown = format(BRIEF);
		assertTrue(shown.startsWith("-89,999,999,707,307"), "expected grouped plain notation, got " + shown);
		assertFalse(shown.contains("E"), "no scientific notation at this magnitude: " + shown);
	}

	@Test
	void theBareLargeIntegerReadsAsGroupedDigits() {
		assertEquals("-90,000,000,000,000", Expression.format(new BigDecimal("-90000000000000")));
		assertEquals("90,000,000,000,000", format("90000000000000"));
	}

	@Test
	void precedenceFollowsArithmeticNotLeftToRight() {
		assertEquals("14", format("2+3*4"));
		assertEquals("20", format("(2+3)*4"));
		assertEquals("1", format("7-2*3"));
		assertEquals("2", format("8/2/2"));
		assertEquals("5", format("10-2-3"));
	}

	@Test
	void unaryMinusAppliesBeforeMultiply() {
		assertEquals("-6", format("-2*3"));
		assertEquals("6", format("-2*-3"));
		assertEquals("4", format("--4"));
		assertEquals("-4", format("-2^2"));
		assertEquals("3", format("1--2"));
	}

	@Test
	void nestedParenthesesAreHonoured() {
		assertEquals("9", format("((((1+2))))*3"));
		assertEquals("22", format("2*(3+(4*(5-3)))"));
		assertEquals("2", format("((2))"));
	}

	@Test
	void divisionAndModuloWork() {
		assertEquals("0.5", format("1/2"));
		assertEquals("2", format("4/2"));
		assertEquals("0.75", format("3/4"));
		assertEquals("1", format("7%3"));
		assertEquals("0.5", format("7.5%1"));
	}

	@Test
	void whitespaceIsIrrelevant() {
		assertEquals("14", format("  2 + 3 * 4  "));
		assertEquals("14", format("\t2\t+\t3\t*\t4\t"));
		assertEquals("20", format("( 2 + 3 ) * 4"));
	}

	@Test
	void malformedInputHasNoResultAndDoesNotThrow() {
		assertTrue(rejected(""));
		assertTrue(rejected("   "));
		assertTrue(rejected("(1+2"));
		assertTrue(rejected("1+2)"));
		assertTrue(rejected("1+"));
		assertTrue(rejected("*2"));
		assertTrue(rejected("1/0"));
		assertTrue(rejected("1%0"));
		assertTrue(rejected("1..2"));
		assertTrue(rejected("."));
		assertTrue(rejected(null));
	}

	@Test
	void ordinarySearchQueriesAreNotExpressions() {
		// A real search must never turn into a number by accident.
		assertTrue(rejected("iron ingot"));
		assertTrue(rejected("@gregtech"));
		assertTrue(rejected("$forge:ingots"));
		assertTrue(rejected("r/^iron.*$/"));
		assertTrue(rejected("#sturdy"));
		assertTrue(rejected("-gold"));
		assertTrue(rejected("2 apples"));
		assertTrue(rejected("2E5"));
	}

	@Test
	void powerOperatorsAreSupported() {
		assertEquals("1,024", format("2^10"));
		assertEquals("1,024", format("2**10"));
		assertEquals("512", format("2^3^2"));
		assertEquals("0.125", format("2^-3"));
		assertTrue(rejected("0^-1"));
		assertTrue(rejected("2^0.5"));
	}

	@Test
	void formattingDropsTrailingZeroes() {
		assertEquals("1", Expression.format(new BigDecimal("1.0")));
		assertEquals("1.5", Expression.format(new BigDecimal("1.50")));
		assertEquals("0", Expression.format(BigDecimal.ZERO));
		assertEquals("0", Expression.format(new BigDecimal("-0.000")));
		assertEquals("1,000", Expression.format(new BigDecimal("1000")));
		assertEquals("-1,234.5", Expression.format(new BigDecimal("-1234.50")));
	}

	@Test
	void veryLargeAndVerySmallValuesFallBackToScientificOnlyWhenAbsurd() {
		assertEquals("1,000,000,000,000,000,000,000", format("10^21"));
		String huge = format("10^30");
		assertTrue(huge.contains("E"), "10^30 is too large to read as digits: " + huge);
		assertEquals("1E+30", huge);

		String tiny = format("1/10^30");
		assertTrue(tiny.contains("E"), "1/10^30 is too small to read as digits: " + tiny);

		// Values inside long range stay plain, even with many digits.
		assertEquals("-9,223,372,036,854,775,808", format("-9223372036854775808"));
	}

	@Test
	void divisionKeepsEnoughPrecisionToBeExact() {
		assertEquals("0.333333333333", format("1/3"));
		assertEquals("10,092.782608695652", format("232134/23"));
	}
}
