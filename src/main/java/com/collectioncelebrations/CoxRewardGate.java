/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.gameval.VarbitID;

/**
 * Holds a Chambers of Xeric unique back until its reward chest is claimed.
 * <p>Chambers announces a unique, in the chat and in the collection log, while the team is still
 * walking to the chest. Every other raid sends its collection log only once the chest is open, so
 * only Chambers needs this. Without it the popup and its sound give away the drop that the walk to
 * the chest exists to reveal.
 */
@Singleton
class CoxRewardGate
{
	@Inject
	Client client;
	@Inject
	CelebrationConfig config;

	/** The Chambers unique table, as the collection log words each item. */
	private static final Set<String> UNIQUES =
		Set.of("dexterous prayer scroll", "arcane prayer scroll", "twisted buckler", "dragon hunter crossbow",
			   "dinh's bulwark", "ancestral hat", "ancestral robe top", "ancestral robe bottom", "dragon claws",
			   "elder maul", "kodai insignia", "twisted bow");

	private final List<MessageNode> censored = new ArrayList<>();
	private final List<String> originals = new ArrayList<>();
	// RuneLite builds a line from its own format text when it has one, so that text is censored too.
	private final List<String> originalFormats = new ArrayList<>();
	private boolean awaitingChest;
	private boolean revealed;
	private boolean wasInside;

	/** @return whether the player is inside the Chambers, which is where the whole reveal plays out. */
	private boolean inRaid()
	{
		return client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) > 0;
	}

	/** Entering a raid starts a fresh reveal; leaving one abandons whatever was still held. */
	void tick()
	{
		boolean inside = inRaid();
		if (inside && !wasInside)
		{
			revealed = false;
		}
		else if (!inside && wasInside)
		{
			noteChestOpened();
		}
		wasInside = inside;
	}

	/** @return whether the option is on and {@code name} is one of the Chambers uniques. */
	boolean covers(String name)
	{
		return config.hideCoxRewards() && name != null && UNIQUES.contains(name.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * Arms the hold from the unlock itself, so it does not depend on catching an announcement or on
	 * the order the chat line and the notification script arrive in.
	 * <p>Deliberately not called from the loot path: that loot arrives with the chest, by which time
	 * the reveal has already happened and re-arming would strand the popup.
	 */
	void arm(String name)
	{
		// Only inside a raid, and never again once this raid's chest has already given it away.
		if (!revealed && inRaid() && covers(name))
		{
			awaitingChest = true;
		}
	}

	/** @return whether a unique is being held right now, so the game's own notification stays hidden. */
	boolean holding()
	{
		return awaitingChest && config.hideCoxRewards();
	}

	/** @return whether this celebration waits for the chest instead of being presented now. */
	boolean holds(Celebration c)
	{
		return awaitingChest && c != null && covers(c.name);
	}

	/**
	 * The four ways a raid names a unique: your own new collection log slot, a team mate's slot
	 * broadcast to the clan or friends chat, the valuable drop line that also fires for a unique you
	 * already own, and the raid's own special loot broadcast.
	 */
	private static final String[] ANNOUNCEMENTS =
		{"New item added to your collection log: ", "Valuable drop: ", "received a new collection log item: ",
		 "received special loot from a raid: "};
	/** The first two are addressed to you and must start the line; the rest name another player first. */
	private static final int SELF = 2;

	/**
	 * Blanks the item out of an announcement and starts holding the drop back.
	 * <p>The hold does not depend on the line being blankable: a node that is missing, already
	 * censored or worded unexpectedly must never let the popup through early.
	 *
	 * @param message the announcement with its tags removed
	 * @return whether it named a Chambers unique
	 */
	boolean hide(String message, MessageNode node)
	{
		// A clan broadcast reaches you wherever you are. Outside a raid there is no chest of yours
		// to wait for, so censoring would strand the line and arm a hold nothing can clear.
		if (message == null || revealed || !inRaid())
		{
			return false;
		}
		for (int i = 0; i < ANNOUNCEMENTS.length; i++)
		{
			// Your own lines start with their prefix; a team mate's is preceded by their name.
			int at = i < SELF ? (message.startsWith(ANNOUNCEMENTS[i]) ? 0 : -1) : message.indexOf(ANNOUNCEMENTS[i]);
			if (at < 0 || !covers(itemIn(message.substring(at + ANNOUNCEMENTS[i].length()))))
			{
				continue;
			}
			awaitingChest = true;
			censor(node, ANNOUNCEMENTS[i]);
			return true;
		}
		return false;
	}

	/** Strips what the game puts after the name: a value in brackets, or a closing full stop. */
	private static String itemIn(String tail)
	{
		int bracket = tail.lastIndexOf('(');
		String name = (bracket > 0 ? tail.substring(0, bracket) : tail).trim();
		while (name.endsWith("."))
		{
			name = name.substring(0, name.length() - 1).trim();
		}
		return name;
	}

	private void censor(MessageNode node, String prefix)
	{
		if (node == null || censored.contains(node))
		{
			return;
		}
		String value = node.getValue();
		int at = value == null ? -1 : value.indexOf(prefix);
		if (at < 0)
		{
			// Tags split the prefix, so the line cannot be edited safely. The hold still stands.
			return;
		}
		censored.add(node);
		originals.add(value);
		String format = node.getRuneLiteFormatMessage();
		originalFormats.add(format);
		node.setValue(value.substring(0, at + prefix.length()) + "???");
		int formatAt = format == null ? -1 : format.indexOf(prefix);
		if (formatAt >= 0)
		{
			node.setRuneLiteFormatMessage(format.substring(0, formatAt + prefix.length()) + "???");
		}
		client.refreshChat();
	}

	/** The reward interface is the moment the player learns what they got. */
	void noteChestOpened()
	{
		awaitingChest = false;
		revealed = true;
		reveal();
	}

	private void reveal()
	{
		if (censored.isEmpty())
		{
			return;
		}
		for (int i = 0; i < censored.size(); i++)
		{
			MessageNode node = censored.get(i);
			node.setValue(originals.get(i));
			if (originalFormats.get(i) != null)
			{
				node.setRuneLiteFormatMessage(originalFormats.get(i));
			}
		}
		censored.clear();
		originals.clear();
		originalFormats.clear();
		client.refreshChat();
	}

	/**
	 * The held celebration is dropped with the rest of the session, exactly as a queued reward
	 * already is. Its chat lines are put back: a world hop keeps the chat, and no chest is coming.
	 */
	void reset()
	{
		reveal();
		awaitingChest = false;
		revealed = false;
		wasInside = false;
	}
}
