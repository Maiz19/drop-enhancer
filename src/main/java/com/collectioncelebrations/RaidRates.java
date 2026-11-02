/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * The rate table a reward chest uses for the raid as it was played: the Chambers' mode, the Tombs'
 * raid level, the waves of a Colosseum run. The game names only the chest, so without this a rate
 * spans every mode, level or wave.
 */
@Singleton
class RaidRates
{
	static final String CHAMBERS = "Chambers of Xeric", TOMBS = "Tombs of Amascut", COLOSSEUM = "Fortis Colosseum";
	/** Wave 12 always ends with a quiver in the chest; no wave before it does. */
	static final int LAST_WAVE = 12;
	private static final Pattern WAVE_DONE = Pattern.compile("Wave (\\d+) Completed!");
	// The frames whose title names the wave just completed: between waves, and the run's summary.
	private static final int[] WAVE_TITLES = {InterfaceID.ColosseumIntermission2.FRAME, InterfaceID.ColosseumIntermission.FRAME};

	/** The Tombs unique table's weights below raid level 300; fang and lightbearer lose weight above it. */
	private static final String FANG = "Osmumten's fang", LIGHTBEARER = "Lightbearer";
	private static final Map<String, Integer> TOMBS_UNIQUES =
		Map.of("Tumeken's shadow (uncharged)", 10, "Masori mask", 20, "Masori body", 20, "Masori chaps", 20,
			   "Elidinis' ward", 30, FANG, 70, LIGHTBEARER, 70);
	/** Below the level a unique belongs to, rolling it takes a further 1/50 roll. */
	private static final int LOW_LEVEL_ODDS = 50;

	/** How a finished raid was played, read as its chest opened. */
	static final class Played
	{
		final String source;
		// Challenge mode as 1 or 0, the raid level, or the waves completed.
		final int value;

		Played(String source, int value)
		{
			this.source = source;
			this.value = value;
		}
	}

	@Inject
	Client client;

	// The waves completed in the current Colosseum run, by the last title that named one.
	private int waves;

	void tick()
	{
		for (int frame : WAVE_TITLES)
		{
			Widget widget = client.getWidget(frame);
			Integer done = widget == null || widget.isHidden() ? null : waveIn(widget, 3);
			if (done != null)
			{
				waves = done;
			}
		}
	}

	private static Integer waveIn(Widget widget, int depth)
	{
		Matcher title = widget.getText() == null ? null : WAVE_DONE.matcher(widget.getText());
		if (title != null && title.matches())
		{
			return Integer.valueOf(title.group(1));
		}
		if (depth > 0)
		{
			for (Widget[] children : new Widget[][] {widget.getDynamicChildren(), widget.getStaticChildren()})
			{
				for (Widget child : children == null ? new Widget[0] : children)
				{
					Integer done = child == null ? null : waveIn(child, depth - 1);
					if (done != null)
					{
						return done;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Reads the raid behind one loot event, once: a run's waves are spent by its chest.
	 *
	 * @param quiver whether the chest holds Dizana's quiver
	 * @return how the raid was played, or null for any other source or a run whose waves went unseen
	 */
	Played capture(String source, boolean quiver)
	{
		if (CHAMBERS.equals(source))
		{
			return new Played(source, client.getVarbitValue(VarbitID.RAIDS_CHALLENGE_MODE) > 0 ? 1 : 0);
		}
		if (TOMBS.equals(source))
		{
			return new Played(source, client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL));
		}
		if (COLOSSEUM.equals(source))
		{
			int done = quiver ? LAST_WAVE : waves;
			waves = 0;
			return done > 0 ? new Played(source, done) : null;
		}
		return null;
	}

	/**
	 * @param rates the wiki's rate of an item from a source, as {@link WikiDropRates#find} gives it
	 * @return the item's rate in the raid as played, or null where the wiki's own rate has to do
	 */
	static String rate(Played played, String item, BinaryOperator<String> rates)
	{
		if (played == null || item == null)
		{
			return null;
		}
		String chest = WikiDropRates.key(played.source);
		switch (played.source)
		{
			case CHAMBERS:
				// The wiki gives each mode's unique table as a variant of the chest.
				return rates.apply(chest + (played.value > 0 ? "#Challenge Mode" : "#Normal Mode"), item);
			case TOMBS:
				return tombsRate(played.value, item);
			case COLOSSEUM:
				return runRate(chest, played.value, item, rates);
			default:
				return null;
		}
	}

	/** @return the item's share of a Tombs unique at this raid level, or null when it is not one. */
	static String tombsRate(int level, String item)
	{
		Integer base = TOMBS_UNIQUES.get(item);
		if (base == null)
		{
			return null;
		}
		int fang = fangWeight(level), lightbearer = lightbearerWeight(level);
		int weight = FANG.equals(item) ? fang : LIGHTBEARER.equals(item) ? lightbearer : base;
		// The five other uniques weigh 100 together at every level.
		int total = 100 + fang + lightbearer;
		boolean belowItsLevel = level < (base == 70 ? 50 : 150);
		return fraction(weight, total * (belowItsLevel ? LOW_LEVEL_ODDS : 1));
	}

	private static int fangWeight(int level)
	{
		return level <= 300 ? 70 : level <= 350 ? 70 - (level - 300) / 5 : level <= 400 ? 60 - (level - 350) * 2 / 5
			: level <= 450 ? 40 : level <= 500 ? 40 - (level - 450) / 5 : 30;
	}

	private static int lightbearerWeight(int level)
	{
		return level <= 300 ? 70 : level <= 350 ? 70 - (level - 300) / 5 : level <= 400 ? 60 - (level - 350) / 5
			: level <= 450 ? 50 - (level - 400) / 5 : level <= 500 ? 40 - (level - 450) / 10 : 35;
	}

	private static String fraction(int numerator, int denominator)
	{
		int common = BigInteger.valueOf(numerator).gcd(BigInteger.valueOf(denominator)).intValue();
		return numerator / common + "/" + WikiDropRates.number(BigDecimal.valueOf(denominator / common));
	}

	/** @return the chance that a run of this many waves banks the item, from each wave's own roll. */
	private static String runRate(String chest, int waves, String item, BinaryOperator<String> rates)
	{
		double none = 1;
		boolean listed = false;
		for (int wave = 1; wave <= Math.min(waves, LAST_WAVE); wave++)
		{
			String rate = rates.apply(chest + "#Wave " + wave, item);
			if (rate == null)
			{
				continue;
			}
			double chance = WikiDropRates.probability(rate);
			if (chance < 0)
			{
				// A conditional or varying rate cannot be added up.
				return null;
			}
			none *= 1 - chance;
			listed = true;
		}
		return listed ? "1/" + WikiDropRates.number(BigDecimal.valueOf(Math.round(1 / (1 - none)))) : null;
	}
}
