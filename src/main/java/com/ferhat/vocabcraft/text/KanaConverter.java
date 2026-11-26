package com.ferhat.vocabcraft.text;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Conversions between romaji, hiragana and katakana.
 * <ul>
 *   <li>{@link #romajiToHiragana}: lenient, accepts Hepburn and Kunrei variants (shi/si, tsu/tu, ja/zya/jya…)</li>
 *   <li>{@link #katakanaToHiragana}: Unicode offset, keeps ー</li>
 *   <li>{@link #toRomaji}: Hepburn romaji for display, e.g. がっこう → gakkou</li>
 * </ul>
 * Characters it does not know (kanji, punctuation, leftover letters) are passed through unchanged.
 */
public final class KanaConverter {
	private static final int KATAKANA_TO_HIRAGANA = 0x60;
	private static final int MAX_ROMAJI_LENGTH = 4;

	/** romaji → hiragana, all accepted spellings. */
	private static final Map<String, String> ROMAJI = new HashMap<>();
	/** hiragana → Hepburn romaji (the first spelling registered for each kana). */
	private static final Map<String, String> HEPBURN = new HashMap<>();

	static {
		// Each line: kana, then accepted romaji spellings; the first one is the Hepburn spelling used for display.
		add("あ", "a"); add("い", "i"); add("う", "u"); add("え", "e"); add("お", "o");
		add("か", "ka"); add("き", "ki"); add("く", "ku"); add("け", "ke"); add("こ", "ko");
		add("きゃ", "kya"); add("きゅ", "kyu"); add("きょ", "kyo");
		add("が", "ga"); add("ぎ", "gi"); add("ぐ", "gu"); add("げ", "ge"); add("ご", "go");
		add("ぎゃ", "gya"); add("ぎゅ", "gyu"); add("ぎょ", "gyo");
		add("さ", "sa"); add("し", "shi", "si"); add("す", "su"); add("せ", "se"); add("そ", "so");
		add("しゃ", "sha", "sya"); add("しゅ", "shu", "syu"); add("しょ", "sho", "syo"); add("しぇ", "she");
		add("ざ", "za"); add("じ", "ji", "zi"); add("ず", "zu"); add("ぜ", "ze"); add("ぞ", "zo");
		add("じゃ", "ja", "zya", "jya"); add("じゅ", "ju", "zyu", "jyu"); add("じょ", "jo", "zyo", "jyo"); add("じぇ", "je");
		add("た", "ta"); add("ち", "chi", "ti"); add("つ", "tsu", "tu"); add("て", "te"); add("と", "to");
		add("ちゃ", "cha", "tya", "cya"); add("ちゅ", "chu", "tyu", "cyu"); add("ちょ", "cho", "tyo", "cyo"); add("ちぇ", "che");
		add("だ", "da"); add("ぢ", "ji", "di"); add("づ", "zu", "du", "dzu"); add("で", "de"); add("ど", "do");
		add("ぢゃ", "ja", "dya"); add("ぢゅ", "ju", "dyu"); add("ぢょ", "jo", "dyo");
		add("な", "na"); add("に", "ni"); add("ぬ", "nu"); add("ね", "ne"); add("の", "no");
		add("にゃ", "nya"); add("にゅ", "nyu"); add("にょ", "nyo");
		add("は", "ha"); add("ひ", "hi"); add("ふ", "fu", "hu"); add("へ", "he"); add("ほ", "ho");
		add("ひゃ", "hya"); add("ひゅ", "hyu"); add("ひょ", "hyo");
		add("ふぁ", "fa"); add("ふぃ", "fi"); add("ふぇ", "fe"); add("ふぉ", "fo"); add("ふゅ", "fyu");
		add("ば", "ba"); add("び", "bi"); add("ぶ", "bu"); add("べ", "be"); add("ぼ", "bo");
		add("びゃ", "bya"); add("びゅ", "byu"); add("びょ", "byo");
		add("ぱ", "pa"); add("ぴ", "pi"); add("ぷ", "pu"); add("ぺ", "pe"); add("ぽ", "po");
		add("ぴゃ", "pya"); add("ぴゅ", "pyu"); add("ぴょ", "pyo");
		add("ま", "ma"); add("み", "mi"); add("む", "mu"); add("め", "me"); add("も", "mo");
		add("みゃ", "mya"); add("みゅ", "myu"); add("みょ", "myo");
		add("や", "ya"); add("ゆ", "yu"); add("よ", "yo");
		add("ら", "ra"); add("り", "ri"); add("る", "ru"); add("れ", "re"); add("ろ", "ro");
		add("りゃ", "rya"); add("りゅ", "ryu"); add("りょ", "ryo");
		add("わ", "wa"); add("を", "o", "wo"); add("うぃ", "wi"); add("うぇ", "we"); add("うぉ", "wo");
		add("ゔ", "vu"); add("ゔぁ", "va"); add("ゔぃ", "vi"); add("ゔぇ", "ve"); add("ゔぉ", "vo");
		add("てぃ", "ti", "thi"); add("でぃ", "di", "dhi"); add("とぅ", "tu", "twu"); add("どぅ", "du", "dwu");
		add("ぁ", "a", "xa", "la"); add("ぃ", "i", "xi", "li"); add("ぅ", "u", "xu", "lu");
		add("ぇ", "e", "xe", "le"); add("ぉ", "o", "xo", "lo");
		add("ゃ", "ya", "xya", "lya"); add("ゅ", "yu", "xyu", "lyu"); add("ょ", "yo", "xyo", "lyo");
		add("っ", "", "xtsu", "ltsu", "xtu", "ltu"); add("ゎ", "wa", "xwa", "lwa");
	}

	private KanaConverter() {
	}

	/**
	 * Registers a kana and its romaji spellings. A romaji spelling already taken keeps its first kana,
	 * so "ti" stays ち and "ji" stays じ for input; the Hepburn display spelling is always the first one.
	 */
	private static void add(String kana, String hepburn, String... alternatives) {
		HEPBURN.putIfAbsent(kana, hepburn);
		if (!hepburn.isEmpty()) {
			ROMAJI.putIfAbsent(hepburn, kana);
		}
		for (String alternative : alternatives) {
			ROMAJI.putIfAbsent(alternative, kana);
		}
	}

	/**
	 * Converts romaji to hiragana. Letters that are not valid romaji are kept as-is.
	 * Handles: double consonants (kitte → きって, matcha → まっちゃ), n/nn/n' (konnichiwa, kin'en),
	 * m before b/m/p (shimbun), "-" as a long vowel mark (ke-ki → けーき), macrons (gakkō → がっこう).
	 */
	public static String romajiToHiragana(String input) {
		String s = expandMacrons(input.toLowerCase(Locale.ROOT)).replaceAll("oh(?![aiueoy])", "ou");
		StringBuilder out = new StringBuilder(s.length());
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			char next = i + 1 < s.length() ? s.charAt(i + 1) : 0;

			if (c == '-') {
				out.append('ー');
				i++;
				continue;
			}
			if (c == 'n') {
				if (next == '\'') {
					out.append('ん');
					i += 2;
					continue;
				}
				if (next == 'n') {
					char after = i + 2 < s.length() ? s.charAt(i + 2) : 0;
					out.append('ん');
					// "konnichiwa": the second n starts "ni"; "kinn" / "konnnichiwa": nn is just ん
					i += isVowel(after) || after == 'y' ? 1 : 2;
					continue;
				}
				if (!isVowel(next) && next != 'y') {
					out.append('ん');
					i++;
					continue;
				}
			}
			if (c == 'm' && (next == 'b' || next == 'm' || next == 'p')) {
				out.append('ん');
				i++;
				continue;
			}
			if (isConsonant(c) && (next == c || (c == 't' && next == 'c' && charAt(s, i + 2) == 'h'))) {
				out.append('っ');
				i++;
				continue;
			}

			boolean matched = false;
			for (int length = Math.min(MAX_ROMAJI_LENGTH, s.length() - i); length > 0; length--) {
				String kana = ROMAJI.get(s.substring(i, i + length));
				if (kana != null) {
					out.append(kana);
					i += length;
					matched = true;
					break;
				}
			}
			if (!matched) {
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}

	/** Converts katakana to hiragana. ー and non-katakana characters are kept. */
	public static String katakanaToHiragana(String input) {
		StringBuilder out = new StringBuilder(input.length());
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			out.append(c >= 'ァ' && c <= 'ヶ' ? (char) (c - KATAKANA_TO_HIRAGANA) : c);
		}
		return out.toString();
	}

	/**
	 * Converts kana (hiragana or katakana) to Hepburn romaji, e.g. がっこう → gakkou, ケーキ → keeki,
	 * きんえん → kin'en. Long vowels are written out (ou, ee), not with macrons.
	 */
	public static String toRomaji(String kana) {
		String s = katakanaToHiragana(kana);
		StringBuilder out = new StringBuilder(s.length() * 2);
		boolean doubleNext = false;
		int i = 0;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == 'っ') {
				doubleNext = true;
				i++;
				continue;
			}
			if (c == '・') {
				out.append(' ');  // ジャック・オ・ランタン → jakku o rantan
				i++;
				continue;
			}
			if (c == 'ー') {
				char vowel = lastVowel(out);
				if (vowel != 0) {
					out.append(vowel);
				}
				i++;
				continue;
			}
			if (c == 'ん') {
				String following = i + 1 < s.length() ? syllableAt(s, i + 1) : null;
				out.append('n');
				if (following != null && !following.isEmpty()
						&& (isVowel(following.charAt(0)) || following.charAt(0) == 'y')) {
					out.append('\'');
				}
				i++;
				continue;
			}
			String two = i + 1 < s.length() ? HEPBURN.get(s.substring(i, i + 2)) : null;
			String romaji = two != null ? two : HEPBURN.get(String.valueOf(c));
			int consumed = two != null ? 2 : 1;
			if (romaji == null) {
				out.append(c);
			} else {
				if (doubleNext && !romaji.isEmpty()) {
					out.append(romaji.startsWith("ch") ? 't' : romaji.charAt(0));
				}
				out.append(romaji);
			}
			doubleNext = false;
			i += consumed;
		}
		return out.toString();
	}

	/** The vowel ending a kana's romaji (か → 'a'), or 0 if it has none (ん, kanji, …). */
	public static char vowelOf(char kana) {
		String romaji = HEPBURN.get(String.valueOf(katakanaToHiragana(String.valueOf(kana)).charAt(0)));
		if (romaji == null || romaji.isEmpty()) {
			return 0;
		}
		char last = romaji.charAt(romaji.length() - 1);
		return isVowel(last) ? last : 0;
	}

	private static String syllableAt(String hiragana, int index) {
		if (index + 1 < hiragana.length()) {
			String two = HEPBURN.get(hiragana.substring(index, index + 2));
			if (two != null) {
				return two;
			}
		}
		return HEPBURN.get(String.valueOf(hiragana.charAt(index)));
	}

	private static char lastVowel(CharSequence romaji) {
		for (int i = romaji.length() - 1; i >= 0; i--) {
			if (isVowel(romaji.charAt(i))) {
				return romaji.charAt(i);
			}
		}
		return 0;
	}

	private static String expandMacrons(String s) {
		return s.replace("ā", "aa").replace("â", "aa")
				.replace("ī", "ii").replace("î", "ii")
				.replace("ū", "uu").replace("û", "uu")
				.replace("ē", "ee").replace("ê", "ee")
				.replace("ō", "ou").replace("ô", "ou");
	}

	private static char charAt(String s, int index) {
		return index < s.length() ? s.charAt(index) : 0;
	}

	static boolean isVowel(char c) {
		return c == 'a' || c == 'i' || c == 'u' || c == 'e' || c == 'o';
	}

	private static boolean isConsonant(char c) {
		return c >= 'a' && c <= 'z' && !isVowel(c) && c != 'n';
	}
}
