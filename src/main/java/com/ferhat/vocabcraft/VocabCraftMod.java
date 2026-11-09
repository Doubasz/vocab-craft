package com.ferhat.vocabcraft;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VocabCraftMod implements ModInitializer {
	public static final String MOD_ID = "vocabcraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Vocab Craft loaded");
	}
}
