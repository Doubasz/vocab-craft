package com.ferhat.vocabcraft.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerMatcherTest {
	private static final List<String> EAT = List.of("to eat", "eat");

	@Test
	void exactMatch() {
		assertTrue(AnswerMatcher.matches("eat", EAT));
		assertTrue(AnswerMatcher.matches("to eat", EAT));
	}

	@Test
	void ignoresCaseAndSurroundingSpaces() {
		assertTrue(AnswerMatcher.matches("  To Eat ", EAT));
		assertTrue(AnswerMatcher.matches("to   eat", EAT));
	}

	@Test
	void matchesJapanese() {
		assertTrue(AnswerMatcher.matches("食べる", List.of("taberu", "たべる", "食べる")));
		assertTrue(AnswerMatcher.matches("たべる", List.of("taberu", "たべる", "食べる")));
	}

	@Test
	void rejectsWrongEmptyAndNull() {
		assertFalse(AnswerMatcher.matches("drink", EAT));
		assertFalse(AnswerMatcher.matches("   ", EAT));
		assertFalse(AnswerMatcher.matches(null, EAT));
	}
}
