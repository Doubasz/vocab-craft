package com.ferhat.vocabcraft.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerMatcherTest {

	@ParameterizedTest(name = "{0} = {1}")
	@CsvSource({
			"taberu, たべる",
			"たべる, たべる",
			"タベル, たべる",
			"食べる, 食べる",
			"TABERU, たべる",
			"daiyamondo, ダイヤモンド",
			"だいやもんど, ダイヤモンド",
			"kitte, きって",
			"konnichiwa, こんにちは",
			"konnichiha, こんにちは",
			"shinbun, しんぶん",
			"shimbun, しんぶん",
			"gakkou, がっこう",
			"gakkō, がっこう",
			"gakkoo, がっこう",
			"ookii, おおきい",
			"ōkii, おおきい",
			"sensei, せんせい",
			"sensee, せんせい",
			"keeki, ケーキ",
			"kēki, ケーキ",
			"ke-ki, ケーキ",
			"paatii, パーティー",
			"hanaji, はなぢ",
			"tetsu no ken, てつのけん",
			"ｔａｂｅｒｕ, たべる",
			"ﾀﾍﾞﾙ, たべる"
	})
	void japaneseMatches(String input, String expected) {
		assertTrue(AnswerMatcher.matchesJapanese(input, List.of(expected)), input + " should match " + expected);
	}

	@ParameterizedTest(name = "{0} ≠ {1}")
	@CsvSource({
			"tabero, たべる",
			"obasan, おばあさん",
			"okii, おおきい",
			"kinen, きんえん",
			"nomu, たべる"
	})
	void japaneseRejects(String input, String expected) {
		assertFalse(AnswerMatcher.matchesJapanese(input, List.of(expected)), input + " should not match " + expected);
	}

	@ParameterizedTest(name = "{0} = {1}")
	@CsvSource({
			"eat, to eat",
			"to eat, eat",
			"'  To   Eat ', to eat",
			"apple, an apple",
			"the book, book",
			"oh, Oh!",
			"Water., water"
	})
	void englishMatches(String input, String expected) {
		assertTrue(AnswerMatcher.matchesEnglish(input, List.of(expected)), input + " should match " + expected);
	}

	@Test
	void rejectsWrongEmptyAndNull() {
		List<String> eat = List.of("to eat", "eat");
		assertFalse(AnswerMatcher.matchesEnglish("drink", eat));
		assertFalse(AnswerMatcher.matchesEnglish("   ", eat));
		assertFalse(AnswerMatcher.matchesEnglish(null, eat));
		assertFalse(AnswerMatcher.matchesJapanese("", List.of("たべる")));
	}
}
