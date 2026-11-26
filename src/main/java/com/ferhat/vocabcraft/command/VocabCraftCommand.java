package com.ferhat.vocabcraft.command;

import com.ferhat.vocabcraft.challenge.ChallengeManager;
import com.ferhat.vocabcraft.challenge.ChallengeType;
import com.ferhat.vocabcraft.config.VocabCraftConfig;
import com.ferhat.vocabcraft.penalty.ItemPenaltyMode;
import com.ferhat.vocabcraft.vocab.Deck;
import com.ferhat.vocabcraft.vocab.ItemNames;
import com.ferhat.vocabcraft.vocab.VocabRepository;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.ferhat.vocabcraft.config.VocabCraftConfig.MAX_INTERVAL_LIMIT;
import static com.ferhat.vocabcraft.config.VocabCraftConfig.MAX_TIME_LIMIT;
import static com.ferhat.vocabcraft.config.VocabCraftConfig.MAX_WEIGHT;
import static com.ferhat.vocabcraft.config.VocabCraftConfig.MIN_INTERVAL_LIMIT;
import static com.ferhat.vocabcraft.config.VocabCraftConfig.MIN_TIME_LIMIT;

/**
 * <pre>
 * /vocabcraft trigger                      start a challenge now (type picked by weight)
 * /vocabcraft item                         start an item challenge now
 * /vocabcraft vocab                        start a vocab challenge now
 * /vocabcraft config                       show current settings
 * /vocabcraft config interval &lt;s&gt;          fixed interval between challenges
 * /vocabcraft config interval &lt;min&gt; &lt;max&gt; random interval in [min, max]
 * /vocabcraft config timeLimit &lt;s&gt;         seconds to answer
 * /vocabcraft config showKana &lt;true|false&gt; show kana next to kanji
 * /vocabcraft config weights &lt;item&gt; &lt;vocab&gt; relative chance of item vs vocab challenges
 * /vocabcraft config itemPenalty one|stack|all  how much of the item is lost
 * /vocabcraft config itemNames all|curated  every item, or ~40 easy ones
 * /vocabcraft config levels n5 n4         JLPT levels to be quizzed on
 * /vocabcraft config decks &lt;id&gt; ...       decks to be quizzed on (bundled or custom)
 * /vocabcraft decks                        list available decks
 * </pre>
 * Changes are saved to config/vocabcraft/config.json immediately.
 */
public final class VocabCraftCommand {
	private VocabCraftCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
								ChallengeManager manager, VocabCraftConfig config, VocabRepository vocab) {
		dispatcher.register(Commands.literal("vocabcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("trigger").executes(ctx -> trigger(ctx, manager, null)))
				.then(Commands.literal("item").executes(ctx -> trigger(ctx, manager, ChallengeType.ITEM)))
				.then(Commands.literal("vocab").executes(ctx -> trigger(ctx, manager, ChallengeType.VOCAB)))
				.then(Commands.literal("decks").executes(ctx -> listDecks(ctx, config, vocab)))
				.then(Commands.literal("config")
						.executes(ctx -> show(ctx, config))
						.then(Commands.literal("interval")
								.then(Commands.argument("seconds", IntegerArgumentType.integer(MIN_INTERVAL_LIMIT, MAX_INTERVAL_LIMIT))
										.executes(ctx -> {
											int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
											return setInterval(ctx, manager, config, seconds, seconds);
										})
										.then(Commands.argument("max", IntegerArgumentType.integer(MIN_INTERVAL_LIMIT, MAX_INTERVAL_LIMIT))
												.executes(ctx -> setInterval(ctx, manager, config,
														IntegerArgumentType.getInteger(ctx, "seconds"),
														IntegerArgumentType.getInteger(ctx, "max"))))))
						.then(Commands.literal("timeLimit")
								.then(Commands.argument("seconds", IntegerArgumentType.integer(MIN_TIME_LIMIT, MAX_TIME_LIMIT))
										.executes(ctx -> {
											config.timeLimitSeconds = IntegerArgumentType.getInteger(ctx, "seconds");
											return saved(ctx, config, "Time limit set to " + config.timeLimitSeconds + "s");
										})))
						.then(Commands.literal("showKana")
								.then(Commands.argument("enabled", BoolArgumentType.bool())
										.executes(ctx -> {
											config.showKanaHint = BoolArgumentType.getBool(ctx, "enabled");
											return saved(ctx, config, "Kana hint " + (config.showKanaHint ? "on" : "off"));
										})))
						.then(Commands.literal("weights")
								.then(Commands.argument("item", IntegerArgumentType.integer(0, MAX_WEIGHT))
										.then(Commands.argument("vocab", IntegerArgumentType.integer(0, MAX_WEIGHT))
												.executes(ctx -> setWeights(ctx, config,
														IntegerArgumentType.getInteger(ctx, "item"),
														IntegerArgumentType.getInteger(ctx, "vocab"))))))
						.then(Commands.literal("itemPenalty")
								.then(itemPenalty("one", ItemPenaltyMode.ONE, config))
								.then(itemPenalty("stack", ItemPenaltyMode.STACK, config))
								.then(itemPenalty("all", ItemPenaltyMode.ALL_OF_TYPE, config)))
						.then(Commands.literal("itemNames")
								.then(itemNames("all", ItemNames.ALL, config))
								.then(itemNames("curated", ItemNames.CURATED, config)))
						.then(Commands.literal("levels")
								.then(Commands.argument("levels", StringArgumentType.greedyString())
										.executes(ctx -> setLevels(ctx, config, vocab, StringArgumentType.getString(ctx, "levels")))))
						.then(Commands.literal("decks")
								.then(Commands.argument("decks", StringArgumentType.greedyString())
										.executes(ctx -> setDecks(ctx, config, vocab, StringArgumentType.getString(ctx, "decks")))))));
	}

	/** "n5 n4", "N5,N4" or "5 4" → [jlpt_n5, jlpt_n4]. */
	private static int setLevels(CommandContext<CommandSourceStack> ctx, VocabCraftConfig config, VocabRepository vocab,
								 String input) {
		Set<String> ids = new LinkedHashSet<>();
		for (String token : input.toLowerCase(Locale.ROOT).strip().split("[\\s,]+")) {
			String level = token.replaceFirst("^(jlpt_)?n?", "");
			if (!level.matches("[1-5]")) {
				ctx.getSource().sendFailure(Component.literal("Unknown level '" + token + "'. Use n5, n4, n3, n2 or n1."));
				return 0;
			}
			ids.add("jlpt_n" + level);
		}
		return applyDecks(ctx, config, vocab, ids);
	}

	private static int setDecks(CommandContext<CommandSourceStack> ctx, VocabCraftConfig config, VocabRepository vocab,
								String input) {
		Set<String> ids = new LinkedHashSet<>(List.of(input.strip().split("[\\s,]+")));
		for (String id : ids) {
			if (vocab.deck(id).isEmpty()) {
				ctx.getSource().sendFailure(Component.literal("Unknown deck '" + id + "'. See /vocabcraft decks."));
				return 0;
			}
		}
		return applyDecks(ctx, config, vocab, ids);
	}

	private static int applyDecks(CommandContext<CommandSourceStack> ctx, VocabCraftConfig config, VocabRepository vocab,
								  Set<String> ids) {
		config.enabledDecks = new ArrayList<>(ids);
		int words = ids.stream().mapToInt(id -> vocab.deck(id).map(deck -> deck.words().size()).orElse(0)).sum();
		return saved(ctx, config, "Decks set to " + String.join(", ", ids) + " (" + words + " words)");
	}

	private static int listDecks(CommandContext<CommandSourceStack> ctx, VocabCraftConfig config, VocabRepository vocab) {
		MutableComponent message = Component.literal("Available decks:").withStyle(ChatFormatting.AQUA);
		for (Deck deck : vocab.decks()) {
			boolean enabled = config.enabledDecks.contains(deck.id());
			message.append(Component.literal("\n  " + (enabled ? "✓ " : "   ") + deck.id() + " — " + deck.title()
					+ " (" + deck.words().size() + " words)").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
		ctx.getSource().sendSuccess(() -> message, false);
		return 1;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> itemPenalty(String name, ItemPenaltyMode mode,
																		   VocabCraftConfig config) {
		return Commands.literal(name).executes(ctx -> {
			config.itemPenaltyMode = mode;
			return saved(ctx, config, "Item penalty set to " + name);
		});
	}

	private static LiteralArgumentBuilder<CommandSourceStack> itemNames(String name, ItemNames names,
																		 VocabCraftConfig config) {
		return Commands.literal(name).executes(ctx -> {
			config.itemNames = names;
			return saved(ctx, config, "Item names set to " + name);
		});
	}

	private static int setWeights(CommandContext<CommandSourceStack> ctx, VocabCraftConfig config, int item, int vocab) {
		if (item + vocab == 0) {
			ctx.getSource().sendFailure(Component.literal("At least one weight must be above 0"));
			return 0;
		}
		config.itemChallengeWeight = item;
		config.vocabChallengeWeight = vocab;
		return saved(ctx, config, "Weights set to item " + item + " / vocab " + vocab);
	}

	private static int trigger(CommandContext<CommandSourceStack> ctx, ChallengeManager manager, ChallengeType type)
			throws CommandSyntaxException {
		CommandSourceStack source = ctx.getSource();
		ServerPlayer player = source.getPlayerOrException();
		return switch (manager.start(player, source.getServer().getTickCount(), type)) {
			case STARTED -> 1;
			case ALREADY_ACTIVE -> {
				source.sendFailure(Component.literal("You already have an active challenge."));
				yield 0;
			}
			case NO_ELIGIBLE_ITEM -> {
				source.sendFailure(Component.literal("No item in your inventory has a Japanese name yet."));
				yield 0;
			}
			case NO_WORDS -> {
				source.sendFailure(Component.literal("The enabled decks have no words. See /vocabcraft decks."));
				yield 0;
			}
		};
	}

	private static int setInterval(CommandContext<CommandSourceStack> ctx, ChallengeManager manager,
								   VocabCraftConfig config, int min, int max) {
		if (max < min) {
			ctx.getSource().sendFailure(Component.literal("max (" + max + ") must be >= min (" + min + ")"));
			return 0;
		}
		config.minIntervalSeconds = min;
		config.maxIntervalSeconds = max;
		// Apply right away instead of waiting for the old countdown to finish.
		manager.scheduler().rescheduleAll(ctx.getSource().getServer().getTickCount());
		String range = min == max ? min + "s" : min + "-" + max + "s";
		return saved(ctx, config, "Interval set to " + range);
	}

	private static int show(CommandContext<CommandSourceStack> ctx, VocabCraftConfig config) {
		String interval = config.minIntervalSeconds == config.maxIntervalSeconds
				? config.minIntervalSeconds + "s"
				: config.minIntervalSeconds + "-" + config.maxIntervalSeconds + "s";
		ctx.getSource().sendSuccess(() -> Component.literal("Vocab Craft settings:").withStyle(ChatFormatting.AQUA)
				.append(Component.literal("\n  interval: " + interval).withStyle(ChatFormatting.GRAY))
				.append(Component.literal("\n  timeLimit: " + config.timeLimitSeconds + "s").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("\n  showKana: " + config.showKanaHint).withStyle(ChatFormatting.GRAY))
				.append(Component.literal("\n  weights: item " + config.itemChallengeWeight
						+ " / vocab " + config.vocabChallengeWeight).withStyle(ChatFormatting.GRAY))
				.append(Component.literal("\n  itemPenalty: " + config.itemPenaltyMode.name().toLowerCase(Locale.ROOT))
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal("\n  itemNames: " + config.itemNames.name().toLowerCase(Locale.ROOT))
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal("\n  decks: " + String.join(", ", config.enabledDecks))
						.withStyle(ChatFormatting.GRAY)), false);
		return 1;
	}

	private static int saved(CommandContext<CommandSourceStack> ctx, VocabCraftConfig config, String message) {
		config.save();
		ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}
}
