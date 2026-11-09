package com.ferhat.vocabcraft.client;

import com.ferhat.vocabcraft.VocabCraftMod;
import com.ferhat.vocabcraft.client.hud.ClientChallengeState;
import com.ferhat.vocabcraft.client.hud.QuizHud;
import com.ferhat.vocabcraft.net.ChallengeResultPayload;
import com.ferhat.vocabcraft.net.ChallengeStartPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public class VocabCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(ChallengeStartPayload.TYPE,
				(payload, context) -> ClientChallengeState.start(payload));
		ClientPlayNetworking.registerGlobalReceiver(ChallengeResultPayload.TYPE,
				(payload, context) -> ClientChallengeState.resolve(payload));

		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				ClientChallengeState.tick();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, minecraft) -> ClientChallengeState.clear());

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(VocabCraftMod.MOD_ID, "quiz"), new QuizHud());
	}
}
