package com.ferhat.vocabcraft.text;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KanaConverterTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"taberu, たべる",
			"daiyamondo, だいやもんど",
			// Hepburn and Kunrei variants
			"shi, し", "si, し",
			"chi, ち", "ti, ち",
			"tsu, つ", "tu, つ",
			"fu, ふ", "hu, ふ",
			"ji, じ", "zi, じ",
			"ja, じゃ", "zya, じゃ", "jya, じゃ",
			"sha, しゃ", "sya, しゃ",
			"cha, ちゃ", "tya, ちゃ",
			// double consonants
			"kitte, きって",
			"gakkou, がっこう",
			"matcha, まっちゃ",
			"kocchi, こっち",
			// n
			"konnichiwa, こんにちわ",
			"konnnichiwa, こんにちわ",
			"hon, ほん",
			"shinbun, しんぶん",
			"shimbun, しんぶん",
			"kin'en, きんえん",
			"kinen, きねん",
			"hon'ya, ほんや",
			"honya, ほにゃ",
			"onna, おんな",
			// long vowels
			"gakkō, がっこう",
			"ōkii, おうきい",
			"tōkyō, とうきょう",
			"ohkii, おうきい",
			"ohayou, おはよう",
			"ke-ki, けーき",
			// leftover letters stay
			"eat, えあt"
	})
	void romajiToHiragana(String romaji, String hiragana) {
		assertEquals(hiragana, KanaConverter.romajiToHiragana(romaji));
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"ダイヤモンド, だいやもんど",
			"タベル, たべる",
			"ケーキ, けーき",
			"ヴ, ゔ",
			"食べる, 食べる"
	})
	void katakanaToHiragana(String katakana, String hiragana) {
		assertEquals(hiragana, KanaConverter.katakanaToHiragana(katakana));
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"たべる, taberu",
			"がっこう, gakkou",
			"しんぶん, shinbun",
			"きんえん, kin'en",
			"ほんや, hon'ya",
			"まっちゃ, matcha",
			"きょう, kyou",
			"じてんしゃ, jitensha",
			"ちいさい, chiisai",
			"つくえ, tsukue",
			"ダイヤモンド, daiyamondo",
			"ケーキ, keeki",
			"パーティー, paatii",
			"ふぁいる, fairu"
	})
	void toRomaji(String kana, String romaji) {
		assertEquals(romaji, KanaConverter.toRomaji(kana));
	}
}
