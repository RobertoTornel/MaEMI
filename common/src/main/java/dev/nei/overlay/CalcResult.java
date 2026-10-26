package dev.nei.overlay;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * What the calculator window is showing, and whether the player still wants to see it.
 *
 * <p>Separate from {@link CalcWindow} so the decision "is there a result worth a window?" is testable
 * and so the input hooks do not have to know about layout. The window reappears whenever a new result
 * arrives, which is why {@link #dismiss()} only clears the value rather than a separate flag: closing
 * the window and typing a new expression are the same event from the player's point of view.</p>
 */
public final class CalcResult {
	private static String expression = "";
	private static String formatted = "";
	private static boolean dismissed;

	private CalcResult() {
	}

	/**
	 * Records what the search field currently evaluates to. Returns true when the shown value changed,
	 * which is when the window wants to come back.
	 */
	public static synchronized boolean update(String query) {
		Optional<BigDecimal> value = dev.nei.math.Expression.value(query);
		String text = value.map(dev.nei.math.Expression::format).orElse("");
		if (text.equals(formatted)) {
			return false;
		}
		formatted = text;
		expression = text.isEmpty() ? "" : query.trim();
		if (!text.isEmpty()) {
			// A fresh result is the player asking to see it again, even if they closed it earlier.
			dismissed = false;
		}
		return true;
	}

	/** The formatted value, or empty when the current query is not arithmetic. */
	public static synchronized String text() {
		return formatted;
	}

	/** The expression the value came from, shown as the window's subtitle. */
	public static synchronized String expression() {
		return expression;
	}

	public static synchronized boolean hasValue() {
		return !formatted.isEmpty();
	}

	/** True when there is something to draw: a value that has not been closed. */
	public static synchronized boolean visible() {
		return !formatted.isEmpty() && !dismissed;
	}

	public static synchronized void dismiss() {
		dismissed = true;
	}

	/** Called when the search field is emptied, so a stale value does not reappear later. */
	public static synchronized void clear() {
		formatted = "";
		expression = "";
		dismissed = false;
	}

	public static synchronized void restore(boolean open) {
		dismissed = !open;
	}
}
