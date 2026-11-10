package com.ferhat.vocabcraft.challenge;

import net.minecraft.world.item.Item;

/**
 * The inventory item an item challenge is about, captured when the challenge starts.
 *
 * @param slot  inventory slot the item was in
 * @param item  item type
 * @param count stack size at that moment
 */
public record ItemTarget(int slot, Item item, int count) {
}
