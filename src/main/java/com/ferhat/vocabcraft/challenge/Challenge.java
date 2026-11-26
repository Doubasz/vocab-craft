package com.ferhat.vocabcraft.challenge;

import com.ferhat.vocabcraft.text.AnswerMatcher;
import com.ferhat.vocabcraft.vocab.VocabEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * An active challenge for one player.
 *
 * @param deadlineTick last server tick on which an answer is still accepted
 * @param showKana     show the kana reading next to kanji
 * @param itemTarget   the inventory item at stake, or {@code null} for a vocab challenge
 * @param synonyms     other words with the same meaning, also accepted in EN→JP (青い for "blue" → 青)
 */
public record Challenge(VocabEntry entry, Direction direction, long deadlineTick, boolean showKana,
						ItemTarget itemTarget, List<VocabEntry> synonyms) {
	private static final Pattern KANA_ONLY = Pattern.compile("^[\\u3040-\\u30ffー]+$");

	public Challenge {
		synonyms = synonyms == null ? List.of() : List.copyOf(synonyms);
	}

	public static Challenge vocab(VocabEntry entry, Direction direction, long deadlineTick, boolean showKana) {
		return vocab(entry, direction, deadlineTick, showKana, List.of());
	}

	public static Challenge vocab(VocabEntry entry, Direction direction, long deadlineTick, boolean showKana,
								  List<VocabEntry> synonyms) {
		return new Challenge(entry, direction, deadlineTick, showKana, null, synonyms);
	}

	public static Challenge item(VocabEntry entry, Direction direction, long deadlineTick, boolean showKana,
								 ItemTarget itemTarget) {
		return new Challenge(entry, direction, deadlineTick, showKana, itemTarget, List.of());
	}

	public ChallengeType type() {
		return itemTarget == null ? ChallengeType.VOCAB : ChallengeType.ITEM;
	}

	/** What the player is shown. */
	public String prompt() {
		return switch (direction) {
			case EN_TO_JP -> entry.en().getFirst();
			case JP_TO_EN -> entry.japaneseDisplay(showKana);
		};
	}

	/** The reading of a word: romaji, kana and kana-only alternatives (いく, ゆく). */
	static List<String> readingForms(VocabEntry word) {
		List<String> forms = new ArrayList<>(word.romaji());
		forms.add(word.kana());
		word.alternatives().stream().filter(alt -> KANA_ONLY.matcher(alt).matches()).forEach(forms::add);
		return forms;
	}

	/** Every Japanese spelling of a word: reading, kanji and all alternatives. */
	static List<String> japaneseForms(VocabEntry word) {
		List<String> forms = readingForms(word);
		if (word.kanji() != null) {
			forms.add(word.kanji());
		}
		forms.addAll(word.alternatives());
		return forms;
	}

	/**
	 * EN→JP: any Japanese spelling (taberu, たべる, タベル, 食べる) of the word or of a synonym.
	 * JP→EN: the English meaning, or the reading in romaji or kana (食べる → "to eat", "eat" or "taberu").
	 * Not the kanji, which would just be copying the prompt.
	 */
	public boolean isCorrect(String answer) {
		return switch (direction) {
			case EN_TO_JP -> {
				List<String> forms = japaneseForms(entry);
				synonyms.forEach(synonym -> forms.addAll(japaneseForms(synonym)));
				yield AnswerMatcher.matchesJapanese(answer, forms);
			}
			case JP_TO_EN -> AnswerMatcher.matchesEnglish(answer, entry.en())
					|| AnswerMatcher.matchesJapanese(answer, readingForms(entry));
		};
	}

	/** Full answer shown after the challenge, e.g. "食べる = taberu / to eat". */
	public String solution() {
		return entry.japaneseDisplay(showKana) + " = " + entry.romaji().getFirst() + " / " + entry.en().getFirst();
	}
}
