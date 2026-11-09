package com.ferhat.vocabcraft.config;

/**
 * Tunable values. Hardcoded for now; Phase 5 loads them from config/vocabcraft/config.json.
 */
public final class VocabCraftConfig {
	public static final int TICKS_PER_SECOND = 20;

	/** Short interval for Phase 1 testing. Target defaults are 180-480 s. */
	public static final int MIN_INTERVAL_SECONDS = 30;
	public static final int MAX_INTERVAL_SECONDS = 30;

	/** Delay before the first challenge after joining. */
	public static final int JOIN_GRACE_SECONDS = 30;

	/** Retry delay when a challenge is skipped (dead, creative, spectator). */
	public static final int RETRY_SECONDS = 30;

	public static final int TIME_LIMIT_SECONDS = 15;

	/** Extra server-side time so network latency does not cost the player. */
	public static final int LATENCY_GRACE_TICKS = 10;

	/** Damage per failed vocab challenge: 2 HP = one heart. */
	public static final float HEART_PENALTY_DAMAGE = 2.0F;

	private VocabCraftConfig() {
	}
}
