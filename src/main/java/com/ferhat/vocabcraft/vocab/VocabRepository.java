package com.ferhat.vocabcraft.vocab;

import com.ferhat.vocabcraft.text.AnswerMatcher;
import com.ferhat.vocabcraft.text.KanaConverter;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * All known decks: the bundled JLPT decks plus custom decks from config/vocabcraft/decks/*.json.
 *
 * <p>Deck JSON: {@code {"id", "title", "words": [{"id", "kanji"?, "kana", "alt"?, "romaji"?, "en": [...]}]}}.
 * Romaji is generated from the kana when missing.
 */
public final class VocabRepository {
	public static final List<String> BUNDLED_DECKS = List.of("jlpt_n5", "jlpt_n4", "jlpt_n3", "jlpt_n2", "jlpt_n1");
	private static final String BUNDLED_PATH = "/vocabcraft/decks/%s.json";
	private static final Pattern NOTES = Pattern.compile("\\s*\\([^)]*\\)");

	private final Map<String, Deck> decks = new LinkedHashMap<>();
	/** Per deck: normalized English meaning → words with that meaning. */
	private final Map<String, Map<String, List<VocabEntry>>> meaningIndex = new HashMap<>();
	private final List<String> errors = new ArrayList<>();

	/** Loads the bundled decks, then every *.json in {@code customDir} (if it exists). */
	public static VocabRepository load(Path customDir) {
		VocabRepository repository = new VocabRepository();
		for (String id : BUNDLED_DECKS) {
			try (InputStream in = VocabRepository.class.getResourceAsStream(BUNDLED_PATH.formatted(id))) {
				if (in == null) {
					repository.errors.add("Missing bundled deck " + id);
					continue;
				}
				repository.add(parse(new InputStreamReader(in, StandardCharsets.UTF_8), id));
			} catch (IOException | RuntimeException e) {
				repository.errors.add("Bundled deck " + id + ": " + e.getMessage());
			}
		}
		if (customDir != null && Files.isDirectory(customDir)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(customDir, "*.json")) {
				for (Path file : files) {
					String fallbackId = file.getFileName().toString().replaceFirst("\\.json$", "");
					try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
						repository.add(parse(reader, fallbackId));
					} catch (IOException | RuntimeException e) {
						// A broken custom deck must not stop the mod: skip it and report.
						repository.errors.add(file.getFileName() + ": " + e.getMessage());
					}
				}
			} catch (IOException e) {
				repository.errors.add("Could not list " + customDir + ": " + e.getMessage());
			}
		}
		return repository;
	}

	/**
	 * Parses one deck.
	 *
	 * @param fallbackId id used when the file has none (the file name for custom decks)
	 * @throws JsonParseException       on malformed JSON
	 * @throws IllegalArgumentException on a word without kana or English meaning
	 */
	public static Deck parse(Reader reader, String fallbackId) {
		DeckJson json = new Gson().fromJson(reader, DeckJson.class);
		if (json == null || json.words == null) {
			throw new IllegalArgumentException("deck has no \"words\" list");
		}
		String id = json.id != null ? json.id : fallbackId;
		List<VocabEntry> words = new ArrayList<>(json.words.size());
		for (WordJson word : json.words) {
			if (word.kana == null || word.en == null || word.en.isEmpty()) {
				throw new IllegalArgumentException("word " + word.id + " needs \"kana\" and \"en\"");
			}
			List<String> romaji = word.romaji != null && !word.romaji.isEmpty()
					? word.romaji : List.of(KanaConverter.toRomaji(word.kana));
			String wordId = word.id != null ? word.id : (word.kanji != null ? word.kanji : word.kana) + "|" + word.kana;
			words.add(new VocabEntry(wordId, word.kanji, word.kana, romaji, word.en, word.alt));
		}
		return new Deck(id, json.title != null ? json.title : id, words);
	}

	private void add(Deck deck) {
		decks.put(deck.id(), deck);
		Map<String, List<VocabEntry>> index = new HashMap<>();
		for (VocabEntry word : deck.words()) {
			for (String meaning : word.en()) {
				index.computeIfAbsent(meaningKey(meaning), key -> new ArrayList<>()).add(word);
			}
		}
		meaningIndex.put(deck.id(), index);
	}

	public Optional<Deck> deck(String id) {
		return Optional.ofNullable(decks.get(id));
	}

	public Collection<Deck> decks() {
		return decks.values();
	}

	/** Problems found while loading (broken or missing decks), to log or show once. */
	public List<String> errors() {
		return errors;
	}

	/**
	 * Picks a random word: first a random enabled deck, then a random word in it, so a small level
	 * is not drowned out by a big one (N5 + N1 → about half the words from each).
	 */
	public Optional<VocabEntry> randomWord(Collection<String> enabledDecks, RandomGenerator random) {
		List<Deck> candidates = enabledDecks.stream()
				.map(decks::get)
				.filter(deck -> deck != null && !deck.words().isEmpty())
				.toList();
		if (candidates.isEmpty()) {
			return Optional.empty();
		}
		List<VocabEntry> words = candidates.get(random.nextInt(candidates.size())).words();
		return Optional.of(words.get(random.nextInt(words.size())));
	}

	/**
	 * Other words in the enabled decks that share the word's prompt meaning, e.g. 青い for 青 ("blue").
	 * They are accepted too when the player is asked to say the meaning in Japanese.
	 */
	public List<VocabEntry> synonyms(VocabEntry word, Collection<String> enabledDecks) {
		String key = meaningKey(word.en().getFirst());
		Set<VocabEntry> result = new LinkedHashSet<>();
		for (String deckId : enabledDecks) {
			Map<String, List<VocabEntry>> index = meaningIndex.get(deckId);
			if (index != null) {
				result.addAll(index.getOrDefault(key, List.of()));
			}
		}
		result.remove(word);
		return List.copyOf(result);
	}

	/** "(my) older sister (humble)" → "older sister": notes dropped, then normalized like an answer. */
	static String meaningKey(String meaning) {
		return AnswerMatcher.normalizeEnglish(NOTES.matcher(meaning).replaceAll(""));
	}

	private static final class DeckJson {
		String id;
		String title;
		List<WordJson> words;
	}

	private static final class WordJson {
		String id;
		String kanji;
		String kana;
		List<String> alt;
		List<String> romaji;
		List<String> en;
	}
}
