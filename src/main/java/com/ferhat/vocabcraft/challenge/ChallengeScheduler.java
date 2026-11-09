package com.ferhat.vocabcraft.challenge;

import com.ferhat.vocabcraft.config.VocabCraftConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tracks, per player, the server tick at which the next challenge should fire.
 * Tick-based, so pausing a singleplayer game pauses the countdown too.
 */
public final class ChallengeScheduler {
	private final VocabCraftConfig config;
	private final Map<UUID, Long> nextTriggerTick = new ConcurrentHashMap<>();

	public ChallengeScheduler(VocabCraftConfig config) {
		this.config = config;
	}

	public void scheduleAfterJoin(UUID player, long now) {
		scheduleIn(player, now, seconds(config.joinGraceSeconds));
	}

	public void scheduleNext(UUID player, long now) {
		scheduleIn(player, now, randomIntervalTicks());
	}

	public void scheduleRetry(UUID player, long now) {
		scheduleIn(player, now, seconds(config.retrySeconds));
	}

	public boolean isDue(UUID player, long now) {
		Long tick = nextTriggerTick.get(player);
		return tick != null && now >= tick;
	}

	public void remove(UUID player) {
		nextTriggerTick.remove(player);
	}

	/** Reschedules every waiting player with the current interval, e.g. after the interval was changed. */
	public void rescheduleAll(long now) {
		nextTriggerTick.replaceAll((player, tick) -> now + randomIntervalTicks());
	}

	private long randomIntervalTicks() {
		return seconds(ThreadLocalRandom.current().nextInt(config.minIntervalSeconds, config.maxIntervalSeconds + 1));
	}

	private void scheduleIn(UUID player, long now, long ticks) {
		nextTriggerTick.put(player, now + ticks);
	}

	private static long seconds(int seconds) {
		return (long) seconds * VocabCraftConfig.TICKS_PER_SECOND;
	}
}
