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
	private static boolean pinned;
	private static boolean showExpression = true;

	private CalcResult() {
	}

	/**
	 * Records what the search field currently evaluates to. Returns true when the shown value changed,
	 * which is when the window wants to come back.
	 *
	 * <p>When the result is pinned, a new expression does not replace it: the player must unpin or
	 * close first. This stops a fresh keystroke from silently overwriting a calculation the player
	 * meant to keep.
	 */
	public static synchronized boolean update(String query) {
		if (pinned) {
			return false;
		}
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
		return !formatted.isEmpty() && (!dismissed || pinned);
	}

	/** Toggle the pinned state. A pinned calculation stays on screen until explicitly closed. */
	public static synchronized void togglePin() {
		pinned = !pinned;
		if (!pinned) {
			dismissed = true;
		}
	}

	/** True when the calculation is pinned and will not be replaced by new input. */
	public static synchronized boolean isPinned() {
		return pinned;
	}

	/** Toggle whether the expression is shown beneath the value in the window. */
	public static synchronized void toggleExpression() {
		showExpression = !showExpression;
	}

	/** True when the expression should be shown beneath the value. */
	public static synchronized boolean shouldShowExpression() {
		return showExpression;
	}

	/** Close the calculation. A pinned calculation is unpinned instead of dismissed. */
	public static synchronized void dismiss() {
		if (pinned) {
			pinned = false;
		}
		dismissed = true;
	}

	/** Called when the search field is emptied, so a stale value does not reappear later. */
	public static synchronized void clear() {
		formatted = "";
		expression = "";
		dismissed = false;
		pinned = false;
	}

	public static synchronized void restore(boolean open) {
		dismissed = !open;
	}
}
