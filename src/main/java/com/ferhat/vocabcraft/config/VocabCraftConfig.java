package com.ferhat.vocabcraft.config;

import com.ferhat.vocabcraft.VocabCraftMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod settings, stored in config/vocabcraft/config.json and editable with /vocabcraft config.
 * Field names are the JSON keys.
 */
public final class VocabCraftConfig {
	public static final int TICKS_PER_SECOND = 20;

	/** Extra server-side time so network latency does not cost the player. */
	public static final int LATENCY_GRACE_TICKS = 10;

	/** Damage per failed vocab challenge: 2 HP = one heart. */
	public static final float HEART_PENALTY_DAMAGE = 2.0F;

	public static final int MIN_INTERVAL_LIMIT = 5;
	public static final int MAX_INTERVAL_LIMIT = 3600;
	public static final int MIN_TIME_LIMIT = 3;
	public static final int MAX_TIME_LIMIT = 120;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Seconds between two challenges; a random value in [min, max] is picked each time. */
	public int minIntervalSeconds = 30;
	public int maxIntervalSeconds = 30;

	/** Delay before the first challenge after joining. */
	public int joinGraceSeconds = 30;

	/** Retry delay when a challenge is skipped (dead, creative, spectator). */
	public int retrySeconds = 30;

	/** Seconds the player has to answer. */
	public int timeLimitSeconds = 15;

	/** Show the kana reading next to kanji, e.g. "食べる (たべる)" instead of "食べる". */
	public boolean showKanaHint = false;

	private transient Path path;

	public static VocabCraftConfig load(Path path) {
		VocabCraftConfig config = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, VocabCraftConfig.class);
			} catch (IOException | JsonParseException e) {
				VocabCraftMod.LOGGER.error("Could not read {}, using defaults", path, e);
			}
		}
		if (config == null) {
			config = new VocabCraftConfig();
		}
		config.path = path;
		config.sanitize();
		config.save();
		return config;
	}

	public void save() {
		if (path == null) {
			return;
		}
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			VocabCraftMod.LOGGER.error("Could not save {}", path, e);
		}
	}

	/** Clamps values edited by hand in the JSON file to the allowed ranges. */
	private void sanitize() {
		minIntervalSeconds = clamp(minIntervalSeconds, MIN_INTERVAL_LIMIT, MAX_INTERVAL_LIMIT);
		maxIntervalSeconds = clamp(maxIntervalSeconds, minIntervalSeconds, MAX_INTERVAL_LIMIT);
		joinGraceSeconds = clamp(joinGraceSeconds, 0, MAX_INTERVAL_LIMIT);
		retrySeconds = clamp(retrySeconds, MIN_INTERVAL_LIMIT, MAX_INTERVAL_LIMIT);
		timeLimitSeconds = clamp(timeLimitSeconds, MIN_TIME_LIMIT, MAX_TIME_LIMIT);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
