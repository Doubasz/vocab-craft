package com.ferhat.vocabcraft.net;

import com.ferhat.vocabcraft.VocabCraftMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the challenge is resolved, show the result on the panel.
 *
 * @param correct  whether the answer was accepted
 * @param answer   what the player typed, empty on timeout
 * @param solution full answer, e.g. "食べる = taberu / to eat"
 */
public record ChallengeResultPayload(boolean correct, String answer, String solution) implements CustomPacketPayload {
	public static final Type<ChallengeResultPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(VocabCraftMod.MOD_ID, "challenge_result"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ChallengeResultPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ChallengeResultPayload::correct,
			ByteBufCodecs.STRING_UTF8, ChallengeResultPayload::answer,
			ByteBufCodecs.STRING_UTF8, ChallengeResultPayload::solution,
			ChallengeResultPayload::new);

	@Override
	public Type<ChallengeResultPayload> type() {
		return TYPE;
	}
}
