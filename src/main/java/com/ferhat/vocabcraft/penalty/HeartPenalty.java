package com.ferhat.vocabcraft.penalty;

import com.ferhat.vocabcraft.challenge.Challenge;
import com.ferhat.vocabcraft.config.VocabCraftConfig;
import net.minecraft.server.level.ServerPlayer;

/**
 * Removes one heart using vanilla magic damage, which bypasses armor.
 * Phase 3 replaces it with the custom vocabcraft:quiz_fail damage type and death message.
 */
public final class HeartPenalty implements Penalty {
	@Override
	public void apply(ServerPlayer player, Challenge challenge) {
		player.hurtServer(player.level(), player.damageSources().magic(), VocabCraftConfig.HEART_PENALTY_DAMAGE);
	}
}
