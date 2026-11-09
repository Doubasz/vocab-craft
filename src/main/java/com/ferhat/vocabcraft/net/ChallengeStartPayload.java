package com.ferhat.vocabcraft.net;

import com.ferhat.vocabcraft.VocabCraftMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: a challenge started, show the quiz panel.
 *
 * @param toJapanese      true for EN→JP ("say it in Japanese"), false for JP→EN
 * @param prompt          the word to translate
 * @param timeLimitTicks  visual countdown length; the server deadline is the one that counts
 */
public record ChallengeStartPayload(boolean toJapanese, String prompt, int timeLimitTicks) implements CustomPacketPayload {
	public static final Type<ChallengeStartPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(VocabCraftMod.MOD_ID, "challenge_start"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ChallengeStartPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ChallengeStartPayload::toJapanese,
			ByteBufCodecs.STRING_UTF8, ChallengeStartPayload::prompt,
			ByteBufCodecs.VAR_INT, ChallengeStartPayload::timeLimitTicks,
			ChallengeStartPayload::new);

	@Override
	public Type<ChallengeStartPayload> type() {
		return TYPE;
	}
}
