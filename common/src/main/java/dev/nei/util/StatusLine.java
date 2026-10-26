package dev.nei.util;

/**
 * The short-lived status line drawn at the bottom of the screen.
 *
 * <p>This is all that is left of the retired panel's state: {@link Messages} still wants feedback that
 * is readable while a container GUI covers the HUD, and the chat line alone scrolls away behind the
 * inventory. Keeping it as three fields of state with no widgets attached is what lets the panel
 * package be deleted without changing what the player sees.</p>
 */
public final class StatusLine {
	/** Long enough to read a missing-ingredient list, short enough not to linger over the GUI. */
	private static final long DURATION_MS = 4000L;

	private static String text = "";
	private static long until;

	private StatusLine() {
	}

	public static synchronized void set(String value) {
		text = value == null ? "" : value;
		until = System.currentTimeMillis() + DURATION_MS;
	}

	/** The text to draw right now, or an empty string once it has expired. */
	public static synchronized String current() {
		return System.currentTimeMillis() < until ? text : "";
	}
}
