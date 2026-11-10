package com.ferhat.vocabcraft.vocab;

import com.ferhat.vocabcraft.challenge.Challenge;
import com.ferhat.vocabcraft.challenge.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarterWordsTest {
	@Test
	void idsAreUnique() {
		Set<String> ids = new HashSet<>();
		StarterWords.ALL.forEach(word -> ids.add(word.id()));
		assertEquals(StarterWords.ALL.size(), ids.size());
	}

	@Test
	void everyWordAcceptsItsOwnFormsInBothDirections() {
		for (VocabEntry word : StarterWords.ALL) {
			Challenge toJp = Challenge.vocab(word, Direction.EN_TO_JP, 0, false);
			assertTrue(toJp.isCorrect(word.romaji().getFirst()), word.id());
			assertTrue(toJp.isCorrect(word.kana()), word.id());
			if (word.kanji() != null) {
				assertTrue(toJp.isCorrect(word.kanji()), word.id());
			}

			Challenge toEn = Challenge.vocab(word, Direction.JP_TO_EN, 0, false);
			for (String en : word.en()) {
				assertTrue(toEn.isCorrect(en), word.id() + " / " + en);
			}
		}
	}
}
