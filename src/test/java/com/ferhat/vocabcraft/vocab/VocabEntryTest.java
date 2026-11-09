package com.ferhat.vocabcraft.vocab;

import com.ferhat.vocabcraft.challenge.Challenge;
import com.ferhat.vocabcraft.challenge.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VocabEntryTest {
	private static final VocabEntry TABERU = new VocabEntry("taberu", "食べる", "たべる", List.of("taberu"), List.of("to eat"));
	private static final VocabEntry PAN = new VocabEntry("pan", null, "パン", List.of("pan"), List.of("bread"));

	@Test
	void kanjiOnlyByDefault() {
		assertEquals("食べる", TABERU.japaneseDisplay(false));
	}

	@Test
	void kanaHintAddsReading() {
		assertEquals("食べる (たべる)", TABERU.japaneseDisplay(true));
	}

	@Test
	void kanaOnlyWordIgnoresHint() {
		assertEquals("パン", PAN.japaneseDisplay(false));
		assertEquals("パン", PAN.japaneseDisplay(true));
	}

	@Test
	void promptAndSolutionFollowKanaOption() {
		Challenge withoutKana = new Challenge(TABERU, Direction.JP_TO_EN, 0, false);
		assertEquals("食べる", withoutKana.prompt());
		assertEquals("食べる = taberu / to eat", withoutKana.solution());

		Challenge withKana = new Challenge(TABERU, Direction.JP_TO_EN, 0, true);
		assertEquals("食べる (たべる)", withKana.prompt());
	}
}
