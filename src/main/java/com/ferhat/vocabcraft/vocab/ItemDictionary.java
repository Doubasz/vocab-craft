package com.ferhat.vocabcraft.vocab;

import com.ferhat.vocabcraft.text.KanaConverter;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Japanese names of Minecraft items, keyed by item id (e.g. "minecraft:diamond").
 * <ul>
 *   <li>curated: ~40 common items with short, natural names (items_curated.json, written by hand)</li>
 *   <li>vanilla: every item, with the game's own Japanese name (items_vanilla.json, from tools/build_item_names.py)</li>
 * </ul>
 */
public final class ItemDictionary {
	public static final String CURATED_RESOURCE = "/vocabcraft/items_curated.json";
	public static final String VANILLA_RESOURCE = "/vocabcraft/items_vanilla.json";

	private final Map<String, VocabEntry> entries;

	private ItemDictionary(Map<String, VocabEntry> entries) {
		this.entries = Collections.unmodifiableMap(entries);
	}

	/** The hand-written names only. */
	public static ItemDictionary loadCurated() {
		return load(CURATED_RESOURCE);
	}

	/** Every item: the game's names, with the curated name as the main answer where there is one. */
	public static ItemDictionary loadAll() {
		return merge(load(VANILLA_RESOURCE), loadCurated());
	}

	private static ItemDictionary load(String resource) {
		try (InputStream in = ItemDictionary.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Missing resource " + resource);
			}
			return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + resource, e);
		}
	}

	/**
	 * Combines two dictionaries. Where both name an item, the preferred entry is kept as the prompt and
	 * solution, and the other entry's names and meanings are accepted too (鉄 and 鉄インゴット for iron).
	 */
	public static ItemDictionary merge(ItemDictionary base, ItemDictionary preferred) {
		Map<String, VocabEntry> merged = new LinkedHashMap<>(base.entries);
		preferred.entries.forEach((itemId, entry) -> merged.merge(itemId, entry, (other, main) -> combine(main, other)));
		return new ItemDictionary(merged);
	}

	private static VocabEntry combine(VocabEntry main, VocabEntry other) {
		Set<String> alternatives = new LinkedHashSet<>(main.alternatives());
		if (other.kanji() != null) {
			alternatives.add(other.kanji());
		}
		alternatives.add(other.kana());
		alternatives.addAll(other.alternatives());
		alternatives.remove(main.kana());
		alternatives.remove(main.kanji());

		List<String> romaji = new ArrayList<>(new LinkedHashSet<>(concat(main.romaji(), other.romaji())));
		List<String> en = new ArrayList<>(new LinkedHashSet<>(concat(main.en(), other.en())));
		return new VocabEntry(main.id(), main.kanji(), main.kana(), romaji, en, new ArrayList<>(alternatives));
	}

	private static List<String> concat(List<String> a, List<String> b) {
		List<String> result = new ArrayList<>(a);
		result.addAll(b);
		return result;
	}

	/**
	 * Parses a JSON array of item entries. Romaji is generated from the kana when missing.
	 *
	 * @throws JsonParseException       on malformed JSON
	 * @throws IllegalArgumentException on a missing field or duplicate item id
	 */
	public static ItemDictionary parse(Reader reader) {
		List<Json> list = new Gson().fromJson(reader, new TypeToken<List<Json>>() { }.getType());
		Map<String, VocabEntry> entries = new LinkedHashMap<>();
		if (list != null) {
			for (Json json : list) {
				if (json.item == null || json.kana == null || json.en == null) {
					throw new IllegalArgumentException("Item entry needs item, kana and en: " + json.item);
				}
				String id = json.item.substring(json.item.indexOf(':') + 1);
				List<String> romaji = json.romaji != null && !json.romaji.isEmpty()
						? json.romaji : List.of(KanaConverter.toRomaji(json.kana));
				VocabEntry entry = new VocabEntry(id, json.kanji, json.kana, romaji, json.en);
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
