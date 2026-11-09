package com.ferhat.vocabcraft.vocab;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Japanese names of Minecraft items, keyed by item id (e.g. "minecraft:diamond").
 * Phase 2: curated list only. Phase 3 adds the generated vanilla names for "hard" difficulty.
 */
public final class ItemDictionary {
	public static final String CURATED_RESOURCE = "/vocabcraft/items_curated.json";

	private final Map<String, VocabEntry> entries;

	private ItemDictionary(Map<String, VocabEntry> entries) {
		this.entries = Collections.unmodifiableMap(entries);
	}

	/** Loads the curated dictionary bundled in the mod jar. */
	public static ItemDictionary loadCurated() {
		try (InputStream in = ItemDictionary.class.getResourceAsStream(CURATED_RESOURCE)) {
			if (in == null) {
				throw new IllegalStateException("Missing resource " + CURATED_RESOURCE);
			}
			return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + CURATED_RESOURCE, e);
		}
	}

	/**
	 * Parses a JSON array of item entries.
	 *
	 * @throws JsonParseException       on malformed JSON
	 * @throws IllegalArgumentException on a missing field or duplicate item id
	 */
	public static ItemDictionary parse(Reader reader) {
		List<Json> list = new Gson().fromJson(reader, new TypeToken<List<Json>>() { }.getType());
		Map<String, VocabEntry> entries = new LinkedHashMap<>();
		if (list != null) {
			for (Json json : list) {
				if (json.item == null || json.kana == null || json.romaji == null || json.en == null) {
					throw new IllegalArgumentException("Item entry needs item, kana, romaji and en: " + json.item);
				}
				String id = json.item.substring(json.item.indexOf(':') + 1);
				VocabEntry entry = new VocabEntry(id, json.kanji, json.kana, json.romaji, json.en);
				if (entries.put(json.item, entry) != null) {
					throw new IllegalArgumentException("Duplicate item " + json.item);
				}
			}
		}
		return new ItemDictionary(entries);
	}

	public Optional<VocabEntry> get(String itemId) {
		return Optional.ofNullable(entries.get(itemId));
	}

	/** Item id → word, in file order. */
	public Map<String, VocabEntry> entries() {
		return entries;
	}

	/** JSON shape of one entry. */
	private static final class Json {
		String item;
		String kanji;
		String kana;
		List<String> romaji;
		List<String> en;
	}
}
