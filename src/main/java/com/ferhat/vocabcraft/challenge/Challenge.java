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
 */
public record Challenge(VocabEntry entry, Direction direction, long deadlineTick, boolean showKana) {

	/** What the player is shown. */
	public String prompt() {
		return switch (direction) {
			case EN_TO_JP -> entry.en().getFirst();
			case JP_TO_EN -> entry.japaneseDisplay(showKana);
		};
	}

	public List<String> acceptedAnswers() {
		return switch (direction) {
			case EN_TO_JP -> {
				List<String> answers = new ArrayList<>(entry.romaji());
				answers.add(entry.kana());
				if (entry.kanji() != null) {
					answers.add(entry.kanji());
				}
				yield answers;
			}
			case JP_TO_EN -> entry.en();
		};
	}

	public boolean isCorrect(String answer) {
		return AnswerMatcher.matches(answer, acceptedAnswers());
	}

	/** Full answer shown after the challenge, e.g. "食べる = taberu / to eat". */
	public String solution() {
		return entry.japaneseDisplay(showKana) + " = " + entry.romaji().getFirst() + " / " + entry.en().getFirst();
	}
}
