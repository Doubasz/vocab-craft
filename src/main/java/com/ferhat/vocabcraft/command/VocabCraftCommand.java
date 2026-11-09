package com.ferhat.vocabcraft.command;

import com.ferhat.vocabcraft.challenge.ChallengeManager;
import com.ferhat.vocabcraft.challenge.ChallengeType;
import com.ferhat.vocabcraft.config.VocabCraftConfig;
import com.ferhat.vocabcraft.penalty.ItemPenaltyMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
 * </pre>
 * Changes are saved to config/vocabcraft/config.json immediately.
 */
public final class VocabCraftCommand {
	private VocabCraftCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
								ChallengeManager manager, VocabCraftConfig config) {
		dispatcher.register(Commands.literal("vocabcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("trigger").executes(ctx -> trigger(ctx, manager, null)))
				.then(Commands.literal("item").executes(ctx -> trigger(ctx, manager, ChallengeType.ITEM)))
				.then(Commands.literal("vocab").executes(ctx -> trigger(ctx, manager, ChallengeType.VOCAB)))
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
								.then(itemPenalty("all", ItemPenaltyMode.ALL_OF_TYPE, config)))));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> itemPenalty(String name, ItemPenaltyMode mode,
																		   VocabCraftConfig config) {
		return Commands.literal(name).executes(ctx -> {
			config.itemPenaltyMode = mode;
			return saved(ctx, config, "Item penalty set to " + name);
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
				.append(Component.literal("\n  showKana: " + config.showKanaHint).withStyle(ChatFormatting.GRAY)), false);
		return 1;
	}

	private static int saved(CommandContext<CommandSourceStack> ctx, VocabCraftConfig config, String message) {
		config.save();
		ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
		return 1;
	}
}
