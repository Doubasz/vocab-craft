package com.ferhat.vocabcraft.challenge;

import com.ferhat.vocabcraft.config.VocabCraftConfig;
import com.ferhat.vocabcraft.net.ChallengeResultPayload;
import com.ferhat.vocabcraft.net.ChallengeStartPayload;
import com.ferhat.vocabcraft.penalty.HeartPenalty;
import com.ferhat.vocabcraft.penalty.ItemPenalty;
import com.ferhat.vocabcraft.penalty.Penalty;
import com.ferhat.vocabcraft.vocab.StarterWords;
import com.ferhat.vocabcraft.vocab.VocabEntry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns every player's active challenge: starts it, checks the answer, applies the penalty on failure or timeout.
 * The server is the only authority; the client only displays and sends text.
 */
public final class ChallengeManager {
	public enum StartResult { STARTED, ALREADY_ACTIVE, NO_ELIGIBLE_ITEM }

	private final VocabCraftConfig config;
	private final ChallengeScheduler scheduler;
	private final ItemChallengeFactory itemFactory;
	private final Penalty heartPenalty;
	private final Penalty itemPenalty;
	private final Map<UUID, Challenge> active = new ConcurrentHashMap<>();

	public ChallengeManager(VocabCraftConfig config, ChallengeScheduler scheduler, ItemChallengeFactory itemFactory) {
		this.config = config;
		this.scheduler = scheduler;
		this.itemFactory = itemFactory;
		this.heartPenalty = new HeartPenalty();
		this.itemPenalty = new ItemPenalty(config, heartPenalty);
	}

	public ChallengeScheduler scheduler() {
		return scheduler;
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
					fail(player, challenge, "Time's up!", "", now);
				}
			} else if (scheduler.isDue(id, now)) {
				if (canReceiveChallenge(player)) {
					start(player, now, null);
				} else {
					scheduler.scheduleRetry(id, now);
				}
			}
		}
	}

	/**
	 * Starts a challenge right away (used by the scheduler and the /vocabcraft commands).
	 *
	 * @param forcedType type to start, or {@code null} to pick by weight.
	 *                   A weighted ITEM pick falls back to VOCAB when no inventory item has a Japanese name;
	 *                   a forced ITEM does not.
	 */
	public StartResult start(ServerPlayer player, long now, ChallengeType forcedType) {
		if (active.containsKey(player.getUUID())) {
			return StartResult.ALREADY_ACTIVE;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		ChallengeType type = forcedType != null ? forcedType : randomType(random);
		Direction direction = Direction.values()[random.nextInt(Direction.values().length)];

		int timeLimit = config.timeLimitSeconds;
		long deadline = now
				+ (long) timeLimit * VocabCraftConfig.TICKS_PER_SECOND
				+ VocabCraftConfig.LATENCY_GRACE_TICKS;

		Challenge challenge;
		ItemStack icon = ItemStack.EMPTY;
		Optional<ItemChallengeFactory.Pick> pick = type == ChallengeType.ITEM ? itemFactory.pick(player) : Optional.empty();
		if (pick.isPresent()) {
			challenge = new Challenge(pick.get().entry(), direction, deadline, config.showKanaHint, pick.get().target());
			icon = pick.get().stack();
		} else if (forcedType == ChallengeType.ITEM) {
			return StartResult.NO_ELIGIBLE_ITEM;
		} else {
			List<VocabEntry> words = StarterWords.ALL;
			challenge = Challenge.vocab(words.get(random.nextInt(words.size())), direction, deadline, config.showKanaHint);
		}
		active.put(player.getUUID(), challenge);

		if (ServerPlayNetworking.canSend(player, ChallengeStartPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ChallengeStartPayload(direction == Direction.EN_TO_JP,
					challenge.prompt(), timeLimit * VocabCraftConfig.TICKS_PER_SECOND, icon));
			return StartResult.STARTED;
		}
		// Client without the mod: fall back to a chat prompt.
		String instruction = direction == Direction.EN_TO_JP ? "Say it in Japanese: " : "Say it in English: ";
		MutableComponent message = prefix()
				.append(Component.literal(instruction).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(challenge.prompt()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
		if (!icon.isEmpty()) {
			message.append(Component.literal(" or lose your ").withStyle(ChatFormatting.RED)).append(icon.getHoverName());
		}
		player.sendSystemMessage(message.append(Component.literal(" (" + timeLimit + "s, answer in chat)")
				.withStyle(ChatFormatting.GRAY)));
		return StartResult.STARTED;
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
			succeed(player, challenge, text, now);
		} else {
			fail(player, challenge, "Wrong!", text, now);
		}
		return true;
	}

	private ChallengeType randomType(ThreadLocalRandom random) {
		int total = config.itemChallengeWeight + config.vocabChallengeWeight;
		return random.nextInt(total) < config.itemChallengeWeight ? ChallengeType.ITEM : ChallengeType.VOCAB;
	}

	private void succeed(ServerPlayer player, Challenge challenge, String answer, long now) {
		active.remove(player.getUUID());
		sendResult(player, true, answer, challenge, "");
		player.sendSystemMessage(prefix()
				.append(Component.literal("✓ Correct! ").withStyle(ChatFormatting.GREEN))
				.append(Component.literal(challenge.solution()).withStyle(ChatFormatting.GRAY)));
		scheduler.scheduleNext(player.getUUID(), now);
	}

	private void fail(ServerPlayer player, Challenge challenge, String reason, String answer, long now) {
		active.remove(player.getUUID());
		Penalty penalty = challenge.type() == ChallengeType.ITEM ? itemPenalty : heartPenalty;
		Component lost = penalty.apply(player, challenge);
		sendResult(player, false, answer, challenge, lost.getString());
		player.sendSystemMessage(prefix()
				.append(Component.literal("✗ " + reason + " ").withStyle(ChatFormatting.RED))
				.append(Component.literal(challenge.solution() + " ").withStyle(ChatFormatting.GRAY))
				.append(lost.copy().withStyle(ChatFormatting.RED)));
		scheduler.scheduleNext(player.getUUID(), now);
	}

	/** Updates the quiz panel. The chat message is still sent so past answers stay in the chat history. */
	private static void sendResult(ServerPlayer player, boolean correct, String answer, Challenge challenge, String penalty) {
		if (ServerPlayNetworking.canSend(player, ChallengeResultPayload.TYPE)) {
			ServerPlayNetworking.send(player,
					new ChallengeResultPayload(correct, answer.strip(), challenge.solution(), penalty));
		}
	}

	private static boolean canReceiveChallenge(ServerPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	private static MutableComponent prefix() {
		return Component.literal("[Vocab Craft] ").withStyle(ChatFormatting.AQUA);
	}
}
