package com.ferhat.vocabcraft.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class VocabCraftNetworking {
	private VocabCraftNetworking() {
	}

	/** Registers payload types. Must run on both sides, so it is called from the common initializer. */
	public static void registerPayloads() {
		PayloadTypeRegistry.clientboundPlay().register(ChallengeStartPayload.TYPE, ChallengeStartPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ChallengeResultPayload.TYPE, ChallengeResultPayload.CODEC);
	}
}
