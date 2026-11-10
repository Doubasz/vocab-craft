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

	/**
	 * EN→JP: romaji, kana or kanji.
	 * JP→EN: the English meaning, or the reading in romaji or kana (e.g. 食べる → "to eat" or "taberu").
	 */
	public List<String> acceptedAnswers() {
		List<String> answers = new ArrayList<>(entry.romaji());
		answers.add(entry.kana());
		switch (direction) {
			case EN_TO_JP -> {
				if (entry.kanji() != null) {
					answers.add(entry.kanji());
				}
			}
			case JP_TO_EN -> answers.addAll(entry.en());
		}
		return answers;
	}

	public boolean isCorrect(String answer) {
		return AnswerMatcher.matches(answer, acceptedAnswers());
	}

	/** Full answer shown after the challenge, e.g. "食べる = taberu / to eat". */
	public String solution() {
		return entry.japaneseDisplay(showKana) + " = " + entry.romaji().getFirst() + " / " + entry.en().getFirst();
	}
}
