package com.ferhat.vocabcraft.challenge;

import com.ferhat.vocabcraft.text.AnswerMatcher;
import com.ferhat.vocabcraft.vocab.VocabEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * An active challenge for one player.
 *
 * @param deadlineTick last server tick on which an answer is still accepted
 * @param showKana     show the kana reading next to kanji
 * @param itemTarget   the inventory item at stake, or {@code null} for a vocab challenge
 */
public record Challenge(VocabEntry entry, Direction direction, long deadlineTick, boolean showKana, ItemTarget itemTarget) {

	public static Challenge vocab(VocabEntry entry, Direction direction, long deadlineTick, boolean showKana) {
		return new Challenge(entry, direction, deadlineTick, showKana, null);
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

	/** The reading: romaji and kana. */
	public List<String> readingForms() {
		List<String> forms = new ArrayList<>(entry.romaji());
		forms.add(entry.kana());
		return forms;
	}

	/** Every Japanese spelling: romaji, kana and kanji. */
	public List<String> japaneseForms() {
		List<String> forms = readingForms();
		if (entry.kanji() != null) {
			forms.add(entry.kanji());
		}
		return forms;
	}

	/**
	 * EN→JP: any Japanese spelling (taberu, たべる, タベル, 食べる).
	 * JP→EN: the English meaning, or the reading in romaji or kana (食べる → "to eat", "eat" or "taberu").
	 * Not the kanji, which would just be copying the prompt.
	 */
	public boolean isCorrect(String answer) {
		return switch (direction) {
			case EN_TO_JP -> AnswerMatcher.matchesJapanese(answer, japaneseForms());
			case JP_TO_EN -> AnswerMatcher.matchesEnglish(answer, entry.en())
					|| AnswerMatcher.matchesJapanese(answer, readingForms());
		};
	}

	/** Full answer shown after the challenge, e.g. "食べる = taberu / to eat". */
	public String solution() {
		return entry.japaneseDisplay(showKana) + " = " + entry.romaji().getFirst() + " / " + entry.en().getFirst();
	}
}
