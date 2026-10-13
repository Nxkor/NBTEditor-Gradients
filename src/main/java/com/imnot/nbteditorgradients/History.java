package com.imnot.nbteditorgradients;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * The states an item went through, for Ctrl+Z and Ctrl+Y in the display screen. Kept until the player leaves
 * the world or the server.
 */
// ponytail: one history, of the last item edited; editing another item starts it again. A history per item if that is missed.
public final class History {
	/** Changes closer than this are one step: a word typed, not one step per letter. */
	private static final long BURST_MS = 700;

	private static final List<ItemStack> states = new ArrayList<>();
	private static int pos;
	private static long lastTyped;

	private History() {}

	/** Points the history at the item being edited; an item it does not know starts a new history. */
	public static void open(ItemStack item) {
		lastTyped = 0;
		for (int i = 0; i < states.size(); i++) {
			// the current step first: the same state can be in the history twice
			int at = (pos + i) % states.size();
			if (ItemStack.matches(states.get(at), item)) {
				pos = at;
				return;
			}
		}
		states.clear();
		states.add(item.copy());
		pos = 0;
		lastTyped = 0;
	}

	/** A new state of the item of {@link #open}; what had been undone can no longer be redone. */
	public static void record(ItemStack item, boolean typing) {
		if (ItemStack.matches(states.get(pos), item)) return;
		states.subList(pos + 1, states.size()).clear();
		long now = System.currentTimeMillis();
		if (typing && now - lastTyped < BURST_MS) {
			states.set(pos, item.copy());
		} else {
			states.add(item.copy());
			pos++;
			// the limit counts the steps that can be undone, the state they lead to comes on top
			while (pos > Config.undoLimit()) {
				states.remove(0);
				pos--;
			}
		}
		lastTyped = typing ? now : 0;
	}

	/** The state before (-1) or after (+1) the current one, null at either end. */
	public static ItemStack step(int direction) {
		int to = pos + direction;
		if (to < 0 || to >= states.size()) return null;
		pos = to;
		lastTyped = 0;
		return states.get(pos).copy();
	}

	public static void clear() {
		states.clear();
		pos = 0;
	}
}
