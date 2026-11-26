package com.ferhat.vocabcraft;

import com.ferhat.vocabcraft.challenge.ChallengeManager;
import com.ferhat.vocabcraft.challenge.ChallengeScheduler;
import com.ferhat.vocabcraft.challenge.ItemChallengeFactory;
import com.ferhat.vocabcraft.command.VocabCraftCommand;
import com.ferhat.vocabcraft.config.VocabCraftConfig;
import com.ferhat.vocabcraft.net.VocabCraftNetworking;
import com.ferhat.vocabcraft.vocab.ItemDictionary;
import com.ferhat.vocabcraft.vocab.VocabRepository;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class VocabCraftMod implements ModInitializer {
	public static final String MOD_ID = "vocabcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		VocabCraftConfig config = VocabCraftConfig.load(configDir.resolve("config.json"));
		ItemDictionary curatedItems = ItemDictionary.loadCurated();
		ItemDictionary allItems = ItemDictionary.loadAll();
		VocabRepository vocab = VocabRepository.load(configDir.resolve("decks"));
		vocab.errors().forEach(error -> LOGGER.error("Deck not loaded: {}", error));
		config.enabledDecks.stream()
				.filter(id -> vocab.deck(id).isEmpty())
				.forEach(id -> LOGGER.warn("Unknown deck in config: {}", id));
		ChallengeManager challengeManager = new ChallengeManager(
				config, new ChallengeScheduler(config), new ItemChallengeFactory(config, curatedItems, allItems), vocab);

		VocabCraftNetworking.registerPayloads();
		warnUnknownItems(curatedItems);

		ServerTickEvents.END_SERVER_TICK.register(challengeManager::tick);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				challengeManager.onJoin(handler.getPlayer(), server.getTickCount()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				challengeManager.onDisconnect(handler.getPlayer()));

		// Chat answers: returning false cancels the message, so the answer never reaches chat.
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) ->
				!challengeManager.onAnswer(sender, message.signedContent(), sender.level().getServer().getTickCount()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				VocabCraftCommand.register(dispatcher, challengeManager, config, vocab));

		LOGGER.info("Vocab Craft loaded ({} item names, {} decks: {} words)", allItems.entries().size(),
				vocab.decks().size(), vocab.decks().stream().mapToInt(deck -> deck.words().size()).sum());
	}

	/** Typos in items_curated.json would silently never match, so report them at startup. */
	private static void warnUnknownItems(ItemDictionary dictionary) {
		for (String itemId : dictionary.entries().keySet()) {
			Identifier id = Identifier.tryParse(itemId);
			if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
				LOGGER.warn("Unknown item in items_curated.json: {}", itemId);
			}
		}
	}
}
