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
	private final Map<UUID, Long> nextTriggerTick = new ConcurrentHashMap<>();

	public void scheduleAfterJoin(UUID player, long now) {
		scheduleIn(player, now, seconds(VocabCraftConfig.JOIN_GRACE_SECONDS));
	}

	public void scheduleNext(UUID player, long now) {
		int delay = ThreadLocalRandom.current().nextInt(
				VocabCraftConfig.MIN_INTERVAL_SECONDS, VocabCraftConfig.MAX_INTERVAL_SECONDS + 1);
		scheduleIn(player, now, seconds(delay));
	}

	public void scheduleRetry(UUID player, long now) {
		scheduleIn(player, now, seconds(VocabCraftConfig.RETRY_SECONDS));
	}

	public boolean isDue(UUID player, long now) {
		Long tick = nextTriggerTick.get(player);
		return tick != null && now >= tick;
	}

	public void remove(UUID player) {
		nextTriggerTick.remove(player);
	}

	private void scheduleIn(UUID player, long now, long ticks) {
		nextTriggerTick.put(player, now + ticks);
	}

	private static long seconds(int seconds) {
		return (long) seconds * VocabCraftConfig.TICKS_PER_SECOND;
	}
}
