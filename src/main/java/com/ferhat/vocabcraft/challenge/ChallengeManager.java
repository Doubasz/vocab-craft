package com.ferhat.vocabcraft.challenge;

import com.ferhat.vocabcraft.config.VocabCraftConfig;
import com.ferhat.vocabcraft.penalty.HeartPenalty;
import com.ferhat.vocabcraft.penalty.Penalty;
import com.ferhat.vocabcraft.vocab.StarterWords;
import com.ferhat.vocabcraft.vocab.VocabEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns every player's active challenge: starts it, checks the answer, applies the penalty on failure or timeout.
 * The server is the only authority; the client only displays and sends text.
 */
public final class ChallengeManager {
	private final ChallengeScheduler scheduler;
	private final Penalty heartPenalty = new HeartPenalty();
	private final Map<UUID, Challenge> active = new ConcurrentHashMap<>();

	public ChallengeManager(ChallengeScheduler scheduler) {
		this.scheduler = scheduler;
	}

	public void onJoin(ServerPlayer player, long now) {
		scheduler.scheduleAfterJoin(player.getUUID(), now);
	}

	public void onDisconnect(ServerPlayer player) {
		active.remove(player.getUUID());
		scheduler.remove(player.getUUID());
	}

	/** Called at the end of every server tick. */
	public void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Challenge challenge = active.get(id);

			if (challenge != null) {
				if (player.isDeadOrDying()) {
					// Died during the challenge: cancel without penalty.
					active.remove(id);
					scheduler.scheduleNext(id, now);
				} else if (now > challenge.deadlineTick()) {
					fail(player, challenge, "Time's up!", now);
				}
			} else if (scheduler.isDue(id, now)) {
				if (canReceiveChallenge(player)) {
					start(player, now);
				} else {
					scheduler.scheduleRetry(id, now);
				}
			}
		}
	}

	/**
	 * Starts a challenge right away (used by the scheduler and /vocabcraft trigger).
	 *
	 * @return false if the player already has an active challenge
	 */
	public boolean start(ServerPlayer player, long now) {
		if (active.containsKey(player.getUUID())) {
			return false;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		List<VocabEntry> words = StarterWords.ALL;
		VocabEntry entry = words.get(random.nextInt(words.size()));
		Direction direction = Direction.values()[random.nextInt(Direction.values().length)];

		long deadline = now
				+ (long) VocabCraftConfig.TIME_LIMIT_SECONDS * VocabCraftConfig.TICKS_PER_SECOND
				+ VocabCraftConfig.LATENCY_GRACE_TICKS;
		Challenge challenge = new Challenge(entry, direction, deadline);
		active.put(player.getUUID(), challenge);

		String instruction = direction == Direction.EN_TO_JP ? "Say it in Japanese: " : "Say it in English: ";
		player.sendSystemMessage(prefix()
				.append(Component.literal(instruction).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(challenge.prompt()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
				.append(Component.literal(" (" + VocabCraftConfig.TIME_LIMIT_SECONDS + "s, answer in chat)")
						.withStyle(ChatFormatting.GRAY)));
		return true;
	}

	/**
	 * Handles a chat message from a player.
	 *
	 * @return true if the message was an answer and must be hidden from chat
	 */
	public boolean onAnswer(ServerPlayer player, String text, long now) {
		Challenge challenge = active.get(player.getUUID());
		if (challenge == null) {
			return false;
		}
		if (now > challenge.deadlineTick()) {
			// Too late: tick() resolves it as a timeout. Still hide the message.
			return true;
		}
		if (challenge.isCorrect(text)) {
			succeed(player, challenge, now);
		} else {
			fail(player, challenge, "Wrong!", now);
		}
		return true;
	}

	private void succeed(ServerPlayer player, Challenge challenge, long now) {
		active.remove(player.getUUID());
		player.sendSystemMessage(prefix()
				.append(Component.literal("✓ Correct! ").withStyle(ChatFormatting.GREEN))
				.append(Component.literal(challenge.solution()).withStyle(ChatFormatting.GRAY)));
		scheduler.scheduleNext(player.getUUID(), now);
	}

	private void fail(ServerPlayer player, Challenge challenge, String reason, long now) {
		active.remove(player.getUUID());
		player.sendSystemMessage(prefix()
				.append(Component.literal("✗ " + reason + " ").withStyle(ChatFormatting.RED))
				.append(Component.literal(challenge.solution()).withStyle(ChatFormatting.GRAY)));
		heartPenalty.apply(player, challenge);
		scheduler.scheduleNext(player.getUUID(), now);
	}

	private static boolean canReceiveChallenge(ServerPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	private static MutableComponent prefix() {
		return Component.literal("[Vocab Craft] ").withStyle(ChatFormatting.AQUA);
	}
}
