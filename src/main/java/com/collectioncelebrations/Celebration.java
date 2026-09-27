/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

final class Celebration
{
	final String name;
	int itemId;
	int dropQuantity;
	String source;
	boolean newSlot;
	boolean extraItem;
	boolean presented;
	boolean upgradeOnly;
	final long sequence;
	final long created;
	final long due;
	// Before an official quantity arrives: 1 means observed logged, 0 means unconfirmed.
	int provisionalTotal;
	Integer confirmedTotal;
	Integer lastSyncedTotal;
	String kc;
	int lootTick = -1;
	// Game tick of the collection log message that created this popup, before any loot joined it.
	int unlockTick = -1;
	PreviewTier previewTier;
	PreviewTier tier;
	long value;
	boolean untradeable;
	Double wikiCompletion;
	String dropRateText;
	long audioGroup;
	boolean audible;
	Celebration(String name, int itemId, int dropQuantity, String source, boolean newSlot, long sequence, long now, long delay)
	{
		this.name = name;
		this.itemId = itemId;
		this.dropQuantity = dropQuantity;
		this.source = source;
		this.audioGroup = sequence;
		this.newSlot = newSlot;
		this.sequence = sequence;
		this.created = now;
		this.due = now + delay;
	}
}
