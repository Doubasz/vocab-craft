package com.ferhat.vocabcraft.vocab;

import java.util.List;

/**
 * A list of words, e.g. "jlpt_n5".
 *
 * @param id    deck id, used in the config and commands
 * @param title display name, e.g. "JLPT N5"
 */
public record Deck(String id, String title, List<VocabEntry> words) {
	public Deck {
		words = List.copyOf(words);
	}
}
