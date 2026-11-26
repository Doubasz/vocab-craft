package com.ferhat.vocabcraft.text;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compares a typed answer with the accepted answers after normalizing both sides the same way.
 *
 * <p>Japanese: romaji, hiragana, katakana and kanji are reduced to one hiragana form, so
 * {@code daiyamondo}, {@code だいやもんど} and {@code ダイヤモンド} are equal, and so are
 * {@code gakkou}, {@code gakkō}, {@code gakkoo}; {@code shinbun} and {@code shimbun}; {@code sensei} and {@code sensee}.
 * Vowel length that changes meaning is kept: {@code obasan} ≠ {@code obaasan}.
 *
 * <p>English: case, punctuation, extra spaces and a leading "to", "a", "an" or "the" are ignored,
 * so {@code eat} = {@code to eat}.
 */
public final class AnswerMatcher {
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	private static final Pattern JAPANESE_PUNCTUATION = Pattern.compile("[\\s.,!?'\"、。・「」！？～~]");
	private static final Pattern ENGLISH_PUNCTUATION = Pattern.compile("[.,!?;:\"()\\[\\]]");
	private static final Pattern ENGLISH_PREFIX = Pattern.compile("^(to|a|an|the) ");

	private AnswerMatcher() {
	}

	/** True if the input matches any form, compared as Japanese (romaji/kana/kanji). */
	public static boolean matchesJapanese(String input, Collection<String> forms) {
		return matches(input, forms, true);
	}

	/** True if the input matches any meaning, compared as English. */
	public static boolean matchesEnglish(String input, Collection<String> meanings) {
		return matches(input, meanings, false);
	}

	private static boolean matches(String input, Collection<String> candidates, boolean japanese) {
		if (input == null) {
			return false;
		}
		String normalized = japanese ? normalizeJapanese(input) : normalizeEnglish(input);
		if (normalized.isEmpty()) {
			return false;
		}
		for (String candidate : candidates) {
			String expected = japanese ? normalizeJapanese(candidate) : normalizeEnglish(candidate);
			if (expected.equals(normalized)) {
				return true;
			}
		}
		return false;
	}

	/** Reduces romaji / kana / kanji text to a canonical hiragana form. */
	static String normalizeJapanese(String s) {
		String text = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		text = WHITESPACE.matcher(text).replaceAll("");
		text = KanaConverter.romajiToHiragana(text);
		text = JAPANESE_PUNCTUATION.matcher(text).replaceAll("");
		text = KanaConverter.katakanaToHiragana(text);
		return canonicalKana(text);
	}

	/**
	 * Smooths over spellings that sound the same:
	 * ー → previous vowel, お段+お → お段+う, え段+い → え段+え, を → お, ぢ → じ, づ → ず,
	 * てぃ → ち, でぃ → じ, and a final は → わ (こんにちは = konnichiwa).
	 */
	private static String canonicalKana(String hiragana) {
		String s = hiragana
				.replace("てぃ", "ち").replace("でぃ", "じ")
				.replace('を', 'お').replace('ぢ', 'じ').replace('づ', 'ず');
		StringBuilder out = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			char previousVowel = out.isEmpty() ? 0 : KanaConverter.vowelOf(out.charAt(out.length() - 1));
			if (c == 'ー') {
				c = vowelKana(previousVowel, c);
			}
			if (c == 'お' && previousVowel == 'o') {
				c = 'う';
			} else if (c == 'い' && previousVowel == 'e') {
				c = 'え';
			}
			out.append(c);
		}
		if (out.length() > 1 && out.charAt(out.length() - 1) == 'は') {
			out.setCharAt(out.length() - 1, 'わ');
		}
		return out.toString();
	}

	private static char vowelKana(char vowel, char fallback) {
		return switch (vowel) {
			case 'a' -> 'あ';
			case 'i' -> 'い';
			case 'u' -> 'う';
			case 'e' -> 'え';
			case 'o' -> 'お';
			default -> fallback;
		};
	}

	/** Lowercase, no punctuation, single spaces, no leading "to", "a", "an" or "the". */
	public static String normalizeEnglish(String s) {
		String text = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		text = ENGLISH_PUNCTUATION.matcher(text).replaceAll(" ");
		text = WHITESPACE.matcher(text.strip()).replaceAll(" ");
		return ENGLISH_PREFIX.matcher(text).replaceFirst("");
	}
}
