package com.ferhat.vocabcraft.vocab;

import java.util.List;

/**
 * Hardcoded words for the Phase 1 MVP. Replaced by JSON decks in Phase 3.
 */
public final class StarterWords {
	public static final List<VocabEntry> ALL = List.of(
			new VocabEntry("taberu", "食べる", "たべる", List.of("taberu"), List.of("to eat", "eat")),
			new VocabEntry("nomu", "飲む", "のむ", List.of("nomu"), List.of("to drink", "drink")),
			new VocabEntry("miru", "見る", "みる", List.of("miru"), List.of("to see", "see", "to look", "look", "to watch", "watch")),
			new VocabEntry("iku", "行く", "いく", List.of("iku"), List.of("to go", "go")),
			new VocabEntry("mizu", "水", "みず", List.of("mizu"), List.of("water")),
			new VocabEntry("inu", "犬", "いぬ", List.of("inu"), List.of("dog")),
			new VocabEntry("neko", "猫", "ねこ", List.of("neko"), List.of("cat")),
			new VocabEntry("ookii", "大きい", "おおきい", List.of("ookii"), List.of("big", "large")),
			new VocabEntry("chiisai", "小さい", "ちいさい", List.of("chiisai"), List.of("small", "little")),
			new VocabEntry("hon", "本", "ほん", List.of("hon"), List.of("book"))
	);

	private StarterWords() {
	}
}
