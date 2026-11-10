package com.ferhat.vocabcraft.penalty;

import com.ferhat.vocabcraft.challenge.Challenge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public interface Penalty {
	/**
	 * Punishes the player for a failed challenge.
	 *
	 * @return short description of what was lost, e.g. "-1 ❤" or "-12 Bread"
	 */
	Component apply(ServerPlayer player, Challenge challenge);
}
