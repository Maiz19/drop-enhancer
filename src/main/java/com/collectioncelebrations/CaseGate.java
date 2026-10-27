/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.awt.Rectangle;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Holds rewards only while a case reel is spinning. The reveal panel that follows can be
 * dismissed whenever the player likes, so it no longer delays the popup or its sound.
 */
@Singleton
class CaseGate
{
	@Inject
	Client client;
	@Inject
	OverlayManager overlays;
	// Time for a case to appear after its reward before the queue gives up waiting.
	private static final long START_GRACE_MILLIS = 2000;
	// Loot Case decides on the loot event itself and draws its reel on the next frame, and only for
	// an item in its own table: a raid without a purple never spins. A few frames tell the two apart.
	private static final int LOOT_START_FRAMES = 3;
	// Clue Case: casket drop (900) + opening (1450) + reel (4000). Its overlay reports no size.
	private static final long CLUE_SPIN_MILLIS = 6500;
	// Safety cap in case the Loot Case reel never reports that it stopped.
	private static final long LOOT_SPIN_LIMIT_MILLIS = 10000;
	private boolean clue;
	private boolean loot;
	private Overlay lootOverlay;
	private long graceUntil;
	private long spinStarted;
	private int framesToWait;
	private static final Set<String> LOOT_SOURCES =
		Set.of("corrupted hunllef", "crystalline hunllef", "theatre of blood", "chambers of xeric", "tombs of amascut", "lunar chest",
			   "barrows", "hallowed sepulchre grand coffin", "elven crystal chest", "chest (moon key)", "larran's big chest",
			   "zombie pirate's locker", "unsired", "doom of mokhaiotl");
	long now()
	{
		return System.currentTimeMillis();
	}
	void reset()
	{
		graceUntil = 0;
		spinStarted = 0;
		framesToWait = 0;
	}
	void noteReward(String source)
	{
		String name = source == null ? "" : source.toLowerCase(Locale.ROOT);
		// Doom's case starts from its reward interface, not from this event.
		if ((clue && name.startsWith("clue scroll")) || (loot && name.equals("doom of mokhaiotl")))
		{
			awaitCase();
		}
		else if (loot && LOOT_SOURCES.contains(name) && spinStarted == 0)
		{
			framesToWait = LOOT_START_FRAMES;
		}
	}
	/** Called once per rendered frame, before anything asks whether the queue is blocked. */
	void frame()
	{
		if (framesToWait > 0)
		{
			framesToWait--;
		}
	}
	/** Loot Case reads Doom's reward from this interface on a later tick, so it gets the timed grace. */
	void noteDoomInterface()
	{
		if (loot)
		{
			awaitCase();
		}
	}
	private void awaitCase()
	{
		if (spinStarted == 0)
		{
			graceUntil = Math.max(graceUntil, now() + START_GRACE_MILLIS);
		}
	}

	void refresh()
	{
		// Identify case plugins by their public overlay names.
		clue = overlays.anyMatch(o -> "ClueCaseOverlay".equals(o.getName()));
		lootOverlay = null;
		loot = overlays.anyMatch(o -> {
			if (!"LootCaseOpeningOverlay".equals(o.getName()))
			{
				return false;
			}
			lootOverlay = o;
			return true;
		});
	}
	boolean installed()
	{
		return (clue || loot);
	}
	long delayMillis()
	{
		return 0;
	}
	boolean blocked()
	{
		if (!installed())
		{
			return false;
		}
		long now = now();
		boolean clueSpin = clue && (hidden(InterfaceID.TrailRewardscreen.UNIVERSE) || hidden(InterfaceID.TrailRewardscreen.ITEMS));
		boolean lootSpin = loot && lootReelVisible();
		if (!clueSpin && !lootSpin)
		{
			spinStarted = 0;
			return now < graceUntil || (loot && framesToWait > 0);
		}
		if (spinStarted == 0)
		{
			spinStarted = now;
			graceUntil = 0;
			framesToWait = 0;
		}
		// Clue Case keeps the reward screen hidden until the result is dismissed, so its spin is timed.
		return now - spinStarted < (lootSpin ? LOOT_SPIN_LIMIT_MILLIS : CLUE_SPIN_MILLIS);
	}
	private boolean lootReelVisible()
	{
		// RuneLite sizes an overlay from its last render: the reel is a wide strip, the reveal
		// panel is taller than wide, and a closed case renders nothing.
		Rectangle bounds = lootOverlay == null ? null : lootOverlay.getBounds();
		return bounds != null && bounds.width > bounds.height;
	}
	private boolean hidden(int component)
	{
		Widget widget = client.getWidget(component);
		return widget != null && widget.isSelfHidden();
	}
}
