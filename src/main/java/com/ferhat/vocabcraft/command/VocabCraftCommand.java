package com.ferhat.vocabcraft.command;

import com.ferhat.vocabcraft.challenge.ChallengeManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /vocabcraft trigger: starts a challenge immediately (testing / debugging).
 */
public final class VocabCraftCommand {
	private VocabCraftCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ChallengeManager manager) {
		dispatcher.register(Commands.literal("vocabcraft")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("trigger").executes(ctx -> {
					CommandSourceStack source = ctx.getSource();
					ServerPlayer player = source.getPlayerOrException();
					if (!manager.start(player, source.getServer().getTickCount())) {
						source.sendFailure(Component.literal("You already have an active challenge."));
						return 0;
					}
					return 1;
				})));
	}
}
