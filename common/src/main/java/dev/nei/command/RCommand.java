package dev.nei.command;

import dev.nei.NeiClient;

/**
 * The {@code /r} command. Whispers to the last player who sent you a private message, or toggles a
 * temporary reply mode that lasts five minutes of inactivity.
 *
 * <p>Usage:</p>
 * <ul>
 *   <li>{@code /r <message>} — sends {@code /msg <target> <message>} where {@code target} is the
 *       reply target if reply mode is active, otherwise the last player who sent you a private
 *       message.</li>
 *   <li>{@code /r} — toggles reply mode for the last player who sent you a private message. Reply
 *       mode stays active for five minutes of inactivity, then switches off automatically.</li>
 * </ul>
 *
 * <p>State (who counts as "the last player") is updated both when you send a private message and
 * when you receive one.</p>
 */
public final class RCommand {
	private RCommand() {
	}

	/** The player who last sent or received a private message, or {@code null} if none yet. */
	private static String lastPmPlayer;

	/** The player reply mode is currently locked to, or {@code null} when reply mode is off. */
	private static String replyTarget;

	/** Whether reply mode is currently active. */
	private static boolean replyModeActive;

	/** System-time expiry for reply mode; after this, {@link #tick()} switches it off. */
	private static long replyModeExpiryMillis;

	private static final long REPLY_MODE_TTL_MILLIS = 5L * 60L * 1000L;

	/** Call whenever a private message is sent or received, to remember the other player. */
	public static void notePrivateMessage(String player) {
		lastPmPlayer = player;
	}

	/** Call once per client tick so reply mode expires after five minutes of inactivity. */
	public static void tick() {
		if (replyModeActive && System.currentTimeMillis() > replyModeExpiryMillis) {
			replyModeActive = false;
			replyTarget = null;
		}
	}

	/**
	 * Handle {@code /r} with an optional message.
	 *
	 * @param args the command arguments (empty for bare {@code /r}, one element for the message)
	 * @return a status key to show the player
	 */
	public static String handle(String[] args) {
		if (args.length > 0) {
			// /r <message> — whisper to the resolved target
			String target = resolveTarget();
			if (target == null) {
				return "nei.r.no_player";
			}
			String message = String.join(" ", args);
			NeiClient.platform().sendCommand("msg " + target + " " + message);
			notePrivateMessage(target);
			return "nei.r.sent";
		} else {
			// /r — toggle reply mode for the last private-message player
			String target = lastPmPlayer;
			if (target == null) {
				return "nei.r.no_player";
			}
			if (replyModeActive && replyTarget != null && replyTarget.equals(target) && !isExpired()) {
				replyModeActive = false;
				replyTarget = null;
				return "nei.r.reply_off";
			}
			replyTarget = target;
			replyModeActive = true;
			replyModeExpiryMillis = System.currentTimeMillis() + REPLY_MODE_TTL_MILLIS;
			return "nei.r.reply_on";
		}
	}

	private static String resolveTarget() {
		if (replyModeActive && replyTarget != null && !isExpired()) {
			return replyTarget;
		}
		return lastPmPlayer;
	}

	private static boolean isExpired() {
		return System.currentTimeMillis() > replyModeExpiryMillis;
	}
}
