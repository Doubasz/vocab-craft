package com.ferhat.vocabcraft.client.hud;

import com.ferhat.vocabcraft.net.ChallengeResultPayload;
import com.ferhat.vocabcraft.net.ChallengeStartPayload;

/**
 * What the quiz panel shows on this client. Only touched on the client thread.
 * The countdown is visual only: the server decides when time is up.
 */
public final class ClientChallengeState {
	/** How long the result stays on screen. */
	private static final int RESULT_TICKS = 60;
	/** Hide the panel if the server result never arrives (e.g. challenge cancelled by death). */
	private static final int MISSING_RESULT_TICKS = 60;

	private static ChallengeStartPayload challenge;
	private static ChallengeResultPayload result;
	private static int remainingTicks;
	private static int resultTicks;

	private ClientChallengeState() {
	}

	public static void start(ChallengeStartPayload payload) {
		challenge = payload;
		result = null;
		remainingTicks = payload.timeLimitTicks();
	}

	public static void resolve(ChallengeResultPayload payload) {
		if (challenge == null) {
			return;
		}
		result = payload;
		resultTicks = RESULT_TICKS;
	}

	public static void clear() {
		challenge = null;
		result = null;
	}

	/** Called every client tick while the game is not paused. */
	public static void tick() {
		if (challenge == null) {
			return;
		}
		if (result != null) {
			if (--resultTicks <= 0) {
				clear();
			}
		} else if (--remainingTicks < -MISSING_RESULT_TICKS) {
			clear();
		}
	}

	public static ChallengeStartPayload challenge() {
		return challenge;
	}

	public static ChallengeResultPayload result() {
		return result;
	}

	public static int remainingTicks() {
		return Math.max(0, remainingTicks);
	}
}
