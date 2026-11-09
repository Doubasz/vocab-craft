package com.ferhat.jpquiz;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JpQuizMod implements ModInitializer {
	public static final String MOD_ID = "jpquiz";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("JP Quiz loaded");
	}
}
