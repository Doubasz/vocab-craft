package com.ferhat.vocabcraft.penalty;

import com.ferhat.vocabcraft.challenge.Challenge;
import net.minecraft.server.level.ServerPlayer;

public interface Penalty {
	void apply(ServerPlayer player, Challenge challenge);
}
