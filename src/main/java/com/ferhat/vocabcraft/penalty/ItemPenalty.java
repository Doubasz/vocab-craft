package com.ferhat.vocabcraft.penalty;

import com.ferhat.vocabcraft.challenge.Challenge;
import com.ferhat.vocabcraft.challenge.ItemTarget;
import com.ferhat.vocabcraft.config.VocabCraftConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Takes away the item the challenge was about. Anti-cheese:
 * <ol>
 *   <li>remove from the original slot if it still holds that item;</li>
 *   <li>otherwise from the first other slot (or the cursor) holding it;</li>
 *   <li>if the item is gone (dropped, stored, placed), take a heart instead.</li>
 * </ol>
 */
public final class ItemPenalty implements Penalty {
	private final VocabCraftConfig config;
	private final Penalty fallback;

	public ItemPenalty(VocabCraftConfig config, Penalty fallback) {
		this.config = config;
		this.fallback = fallback;
	}

	@Override
	public Component apply(ServerPlayer player, Challenge challenge) {
		ItemTarget target = challenge.itemTarget();
		if (target == null) {
			return fallback.apply(player, challenge);
		}
		Item item = target.item();
		Component name = new ItemStack(item).getHoverName();

		int removed = switch (config.itemPenaltyMode) {
			case ALL_OF_TYPE -> removeAll(player, item);
			case ONE -> removeFromFirstMatch(player, target, 1);
			case STACK -> removeFromFirstMatch(player, target, Integer.MAX_VALUE);
		};
		if (removed == 0) {
			return fallback.apply(player, challenge);
		}
		player.getInventory().setChanged();
		return Component.literal("-" + removed + " ").append(name);
	}

	/** Removes up to {@code amount} from one stack: the original slot first, then any slot, then the cursor. */
	private static int removeFromFirstMatch(ServerPlayer player, ItemTarget target, int amount) {
		Inventory inventory = player.getInventory();
		int slot = holds(inventory.getItem(target.slot()), target.item()) ? target.slot() : findSlot(inventory, target.item());
		if (slot >= 0) {
			ItemStack stack = inventory.getItem(slot);
			int count = Math.min(amount, stack.getCount());
			inventory.removeItem(slot, count);
			return count;
		}
		ItemStack carried = player.containerMenu.getCarried();
		if (holds(carried, target.item())) {
			int count = Math.min(amount, carried.getCount());
			carried.shrink(count);
			return count;
		}
		return 0;
	}

	private static int removeAll(ServerPlayer player, Item item) {
		Inventory inventory = player.getInventory();
		int removed = 0;
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (holds(stack, item)) {
				removed += stack.getCount();
				inventory.removeItem(slot, stack.getCount());
			}
		}
		ItemStack carried = player.containerMenu.getCarried();
		if (holds(carried, item)) {
			removed += carried.getCount();
			carried.shrink(carried.getCount());
		}
		return removed;
	}

	private static int findSlot(Inventory inventory, Item item) {
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (holds(inventory.getItem(slot), item)) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean holds(ItemStack stack, Item item) {
		return !stack.isEmpty() && stack.getItem() == item;
	}
}
