package com.ferhat.vocabcraft.vocab;

import com.ferhat.vocabcraft.challenge.Challenge;
import com.ferhat.vocabcraft.challenge.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabRepositoryTest {
	private static VocabRepository repository;

	@BeforeAll
	static void loadBundled() {
		repository = VocabRepository.load(null);
	}

	@Test
	void allBundledDecksLoad() {
		assertTrue(repository.errors().isEmpty(), () -> String.join("\n", repository.errors()));
		assertEquals(VocabRepository.BUNDLED_DECKS, repository.decks().stream().map(Deck::id).toList());
		assertTrue(repository.deck("jlpt_n5").orElseThrow().words().size() > 600);
		assertTrue(repository.deck("jlpt_n1").orElseThrow().words().size() > 2000);
	}

	@Test
	void idsAreUniqueWithinEachDeck() {
		for (Deck deck : repository.decks()) {
			Set<String> ids = new HashSet<>();
			deck.words().forEach(word -> assertTrue(ids.add(word.id()), deck.id() + " duplicate " + word.id()));
		}
	}

	@Test
	void generatedRomajiIsPlainAscii() {
		for (Deck deck : repository.decks()) {
			for (VocabEntry word : deck.words()) {
				String romaji = word.romaji().getFirst();
				assertTrue(romaji.matches("[a-z']+"), deck.id() + ": " + word.kana() + " → " + romaji);
			}
		}
	}

	@Test
	void everyWordAcceptsItsOwnForms() {
		for (Deck deck : repository.decks()) {
			for (VocabEntry word : deck.words()) {
				Challenge toJp = Challenge.vocab(word, Direction.EN_TO_JP, 0, false);
				assertTrue(toJp.isCorrect(word.romaji().getFirst()), word.id() + " romaji");
				assertTrue(toJp.isCorrect(word.kana()), word.id() + " kana");
				if (word.kanji() != null) {
					assertTrue(toJp.isCorrect(word.kanji()), word.id() + " kanji");
				}
				Challenge toEn = Challenge.vocab(word, Direction.JP_TO_EN, 0, false);
				word.en().forEach(en -> assertTrue(toEn.isCorrect(en), word.id() + " / " + en));
			}
		}
	}

	@Test
	void taberuInN5() {
		VocabEntry taberu = word("jlpt_n5", "食べる");
		assertEquals("たべる", taberu.kana());
		assertEquals("taberu", taberu.romaji().getFirst());
		Challenge challenge = Challenge.vocab(taberu, Direction.EN_TO_JP, 0, false);
		for (String answer : List.of("taberu", "たべる", "タベル", "食べる")) {
			assertTrue(challenge.isCorrect(answer), answer);
		}
	}

	@Test
	void alternativeSpellingsAreAccepted() {
		VocabEntry iku = word("jlpt_n5", "行く");
		assertTrue(Challenge.vocab(iku, Direction.EN_TO_JP, 0, false).isCorrect("yuku"));
		assertTrue(Challenge.vocab(iku, Direction.JP_TO_EN, 0, false).isCorrect("ゆく"));
	}

	@Test
	void synonymsShareThePromptMeaning() {
		VocabEntry ao = word("jlpt_n5", "青");
		List<VocabEntry> synonyms = repository.synonyms(ao, List.of("jlpt_n5"));
		assertTrue(synonyms.stream().anyMatch(word -> "青い".equals(word.kanji())), "青い should be a synonym of 青");
		assertFalse(synonyms.contains(ao));

		Challenge blue = Challenge.vocab(ao, Direction.EN_TO_JP, 0, false, synonyms);
		assertTrue(blue.isCorrect("aoi"));
		assertTrue(blue.isCorrect("ao"));
	}

	@Test
	void randomWordOnlyFromEnabledDecks() {
		Set<String> n5 = new HashSet<>();
		repository.deck("jlpt_n5").orElseThrow().words().forEach(word -> n5.add(word.id()));
		Random random = new Random(42);
		for (int i = 0; i < 200; i++) {
			assertTrue(n5.contains(repository.randomWord(List.of("jlpt_n5"), random).orElseThrow().id()));
		}
		assertTrue(repository.randomWord(List.of("does_not_exist"), random).isEmpty());
	}

	@Test
	void customDeckLoadsWithGeneratedRomaji(@TempDir Path dir) throws IOException {
		Files.writeString(dir.resolve("food.json"), """
				{"title": "Food", "words": [{"kanji": "寿司", "kana": "すし", "en": ["sushi"]}]}
				""", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("broken.json"), "{ not json", StandardCharsets.UTF_8);

		VocabRepository withCustom = VocabRepository.load(dir);
		Deck food = withCustom.deck("food").orElseThrow();
		assertEquals("Food", food.title());
		assertEquals("sushi", food.words().getFirst().romaji().getFirst());
		assertEquals(1, withCustom.errors().size(), "broken.json should be reported, not crash");
		assertEquals(VocabRepository.BUNDLED_DECKS.size() + 1, withCustom.decks().size());
	}

	@Test
	void rejectsWordWithoutMeaning() {
		assertThrows(IllegalArgumentException.class, () -> VocabRepository.parse(
				new StringReader("{\"words\": [{\"kana\": \"すし\"}]}"), "x"));
	}

	private static VocabEntry word(String deckId, String kanji) {
		return repository.deck(deckId).orElseThrow().words().stream()
				.filter(word -> kanji.equals(word.kanji()))
				.findFirst()
				.orElseThrow(() -> new AssertionError(kanji + " not in " + deckId));
	}
}
