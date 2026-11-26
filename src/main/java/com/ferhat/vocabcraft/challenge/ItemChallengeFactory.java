package com.ferhat.vocabcraft.challenge;

import com.ferhat.vocabcraft.config.VocabCraftConfig;
import com.ferhat.vocabcraft.vocab.ItemDictionary;
import com.ferhat.vocabcraft.vocab.ItemNames;
import com.ferhat.vocabcraft.vocab.VocabEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Picks a random inventory item that has a Japanese name in the dictionary chosen by {@code itemNames}.
 */
public final class ItemChallengeFactory {
	private final VocabCraftConfig config;
	private final ItemDictionary curated;
	private final ItemDictionary all;

	public ItemChallengeFactory(VocabCraftConfig config, ItemDictionary curated, ItemDictionary all) {
		this.config = config;
		this.curated = curated;
		this.all = all;
	}

	/** A candidate: the item at stake and the word for it. */
	public record Pick(ItemTarget target, ItemStack stack, VocabEntry entry) {
	}

	/** Returns a random eligible item from hotbar, main inventory, armor and offhand, or empty if there is none. */
	public Optional<Pick> pick(ServerPlayer player) {
		ItemDictionary dictionary = config.itemNames == ItemNames.CURATED ? curated : all;
		Inventory inventory = player.getInventory();
		List<Pick> candidates = new ArrayList<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			int finalSlot = slot;
			dictionary.get(itemId).ifPresent(entry -> candidates.add(new Pick(
					new ItemTarget(finalSlot, stack.getItem(), stack.getCount()), stack.copy(), entry)));
		}
		if (candidates.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())));
	}
}
