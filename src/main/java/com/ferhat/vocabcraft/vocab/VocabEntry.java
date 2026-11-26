package com.ferhat.vocabcraft.vocab;

import java.util.List;
import java.util.Objects;

/**
 * One vocabulary word.
 *
 * @param id           stable identifier, e.g. "食べる|たべる"
 * @param kanji        main written form, or {@code null} for kana-only words
 * @param kana         main reading
 * @param romaji       accepted romaji spellings; the first one is shown in solutions
 * @param en           English meanings; the first one is the prompt, all of them are accepted
 * @param alternatives other accepted spellings or readings, e.g. 脚 for 足, ゆく for 行く
 */
public record VocabEntry(String id, String kanji, String kana, List<String> romaji, List<String> en,
						 List<String> alternatives) {
	public VocabEntry {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(kana, "kana");
		romaji = List.copyOf(romaji);
		en = List.copyOf(en);
		alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
		if (romaji.isEmpty() || en.isEmpty()) {
			throw new IllegalArgumentException("VocabEntry '" + id + "' needs at least one romaji and one English form");
		}
	}

	public VocabEntry(String id, String kanji, String kana, List<String> romaji, List<String> en) {
		this(id, kanji, kana, romaji, en, List.of());
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
