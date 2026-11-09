package com.ferhat.vocabcraft.text;

import java.util.Collection;
import java.util.Locale;

/**
 * Compares a typed answer with the accepted answers.
 * Phase 1: exact match, ignoring case and surrounding/repeated spaces.
 * Phase 3 adds romaji/kana normalization and English article stripping.
 */
public final class AnswerMatcher {
	private AnswerMatcher() {
	}

	public static boolean matches(String input, Collection<String> accepted) {
		if (input == null) {
			return false;
		}
		String normalized = normalize(input);
		if (normalized.isEmpty()) {
			return false;
		}
		for (String candidate : accepted) {
			if (normalize(candidate).equals(normalized)) {
				return true;
			}
		}
		return false;
	}

	static String normalize(String s) {
		return s.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}
}
