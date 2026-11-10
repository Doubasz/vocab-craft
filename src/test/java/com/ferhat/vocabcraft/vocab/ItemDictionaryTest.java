package com.ferhat.vocabcraft.vocab;

import com.ferhat.vocabcraft.challenge.Challenge;
import com.ferhat.vocabcraft.challenge.Direction;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemDictionaryTest {
	@Test
	void curatedDictionaryLoads() {
		ItemDictionary dictionary = ItemDictionary.loadCurated();
		assertTrue(dictionary.entries().size() >= 30, "expected at least 30 curated items");
		dictionary.entries().keySet().forEach(id -> assertTrue(id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), id));
	}

	@Test
	void curatedEntriesAcceptTheirOwnForms() {
		ItemDictionary.loadCurated().entries().forEach((itemId, word) -> {
			Challenge toJp = Challenge.vocab(word, Direction.EN_TO_JP, 0, false);
			word.romaji().forEach(romaji -> assertTrue(toJp.isCorrect(romaji), itemId + " / " + romaji));
			assertTrue(toJp.isCorrect(word.kana()), itemId);

			Challenge toEn = Challenge.vocab(word, Direction.JP_TO_EN, 0, false);
			word.en().forEach(en -> assertTrue(toEn.isCorrect(en), itemId + " / " + en));
		});
	}

	@Test
	void diamondLookup() {
		VocabEntry diamond = ItemDictionary.loadCurated().get("minecraft:diamond").orElseThrow();
		assertEquals("ダイヤモンド", diamond.kana());
		assertEquals("diamond", diamond.id());
	}

	@Test
	void rejectsMissingFields() {
		String json = "[{\"item\": \"minecraft:stick\", \"kana\": \"ぼう\"}]";
		assertThrows(IllegalArgumentException.class, () -> ItemDictionary.parse(new StringReader(json)));
	}

	@Test
	void rejectsDuplicates() {
		String entry = "{\"item\": \"minecraft:stick\", \"kana\": \"ぼう\", \"romaji\": [\"bou\"], \"en\": [\"stick\"]}";
		assertThrows(IllegalArgumentException.class,
				() -> ItemDictionary.parse(new StringReader("[" + entry + "," + entry + "]")));
	}
}
