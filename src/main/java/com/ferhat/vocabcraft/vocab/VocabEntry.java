package com.ferhat.vocabcraft.vocab;

import java.util.List;
import java.util.Objects;

/**
 * One vocabulary word.
 *
 * @param id     stable identifier, e.g. "taberu"
 * @param kanji  kanji spelling, or {@code null} for kana-only words
 * @param kana   kana spelling
 * @param romaji accepted romaji spellings; the first one is shown in solutions
 * @param en     accepted English translations; the first one is used in prompts
 */
public record VocabEntry(String id, String kanji, String kana, List<String> romaji, List<String> en) {
	public VocabEntry {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kana, "kana");
		romaji = List.copyOf(romaji);
		en = List.copyOf(en);
		if (romaji.isEmpty() || en.isEmpty()) {
			throw new IllegalArgumentException("VocabEntry '" + id + "' needs at least one romaji and one English form");
		}
	}

	/**
	 * How the word is written in Japanese.
	 *
	 * @param withKana add the kana reading after kanji, e.g. "食べる (たべる)"; ignored for kana-only words
	 */
	public String japaneseDisplay(boolean withKana) {
		if (kanji == null) {
			return kana;
		}
		return withKana ? kanji + " (" + kana + ")" : kanji;
	}
}
