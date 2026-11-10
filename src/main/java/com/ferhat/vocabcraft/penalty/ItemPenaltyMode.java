package com.ferhat.vocabcraft.penalty;

public enum ItemPenaltyMode {
	/** Remove one item from the stack. */
	ONE,
	/** Remove the whole stack. */
	STACK,
	/** Remove every item of that type in the inventory. */
	ALL_OF_TYPE
}
