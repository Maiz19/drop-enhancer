/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display-only wiki rates: multiple rolls and conditional rates must not become invented per-kill probabilities. */
final class WikiDropRates
{
	/** Popup placeholders standing in for a real drop source; neither ever matches a wiki drop table. */
	static final String PREVIEW_SOURCE = "Preview only", UNKNOWN_SOURCE = "Source unavailable";
	static final String VARIES = "Varies", UNKNOWN_RATE = "—";
	private static final String RANGE = " – ";
	/** Below five digits a separator is noise: "1/1000" reads better, "1/10,000" does not. */
	private static final BigDecimal GROUPED = new BigDecimal(10000);
	private static final Pattern FRACTION = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)/(\\d[\\d,]*(?:\\.\\d+)?)");

	/**
	 * Loot the game names otherwise than the wiki: a boss or activity whose rewards the wiki files under a chest or
	 * casket. A Hunllef names its chest's variant: the plain chest would span both, at very different rates.
	 */
	private static final Map<String, String> GAME_SOURCES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	static
	{
		GAME_SOURCES.put("Crystalline Hunllef", "Reward Chest (The Gauntlet)#Regular");
		GAME_SOURCES.put("Corrupted Hunllef", "Reward Chest (The Gauntlet)#Corrupted");
		GAME_SOURCES.put("Barrows", "Chest (Barrows)");
		GAME_SOURCES.put("Chambers of Xeric", "Ancient chest");
		GAME_SOURCES.put("Theatre of Blood", "Monumental chest");
		GAME_SOURCES.put("Tombs of Amascut", "Chest (Tombs of Amascut)");
		GAME_SOURCES.put("Fortis Colosseum", "Rewards Chest (Fortis Colosseum)");
		GAME_SOURCES.put("Fractured Archive", "Chest (The Fractured Archive)");
		GAME_SOURCES.put("Reward cart (Wintertodt)", "Reward Cart");
		GAME_SOURCES.put("Reward pool (Tempoross)", "Reward pool");
		GAME_SOURCES.put("Barbarian Assault high gamble", "Barbarian Assault/Gambles");
		GAME_SOURCES.put("Bryophyta (Chest)", "Chest (Bryophyta's lair)");
		for (String tier : List.of("beginner", "easy", "medium", "hard", "elite", "master"))
		{
			GAME_SOURCES.put("Clue Scroll (" + tier + ")", "Reward casket (" + tier + ")");
		}
		// Mort'ton's shade chests go by the key that opens them: "Gold key red" is the wiki's "Gold Chest (red)".
		for (String metal : List.of("Bronze", "Steel", "Black", "Silver", "Gold"))
		{
			for (String trim : List.of("red", "brown", "crimson", "black", "purple"))
			{
				GAME_SOURCES.put(metal + " key " + trim, metal + " Chest (" + trim + ")");
			}
		}
	}

	private final Map<String, Map<String, String>> sources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	/** Slot aliases kept apart from {@link #sources} so an alias can never shadow an item of the same name. */
	private final Map<String, Map<String, String>> slots = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * @return whether the wiki tables list this source at all. A miss on a listed source is a real
	 *         absence; a source the tables never mention says nothing, and local data still applies.
	 *         A renamed source always counts as listed: local data cannot tell a chest's variants apart.
	 */
	boolean covers(String source)
	{
		if (!knownSource(source))
		{
			return false;
		}
		String key = key(source);
		return GAME_SOURCES.containsValue(key) || sources.containsKey(key) || slots.containsKey(key);
	}

	/** @return the wiki's name for a game loot source. */
	static String key(String source)
	{
		String key = source.replace('_', ' ').trim();
		return GAME_SOURCES.getOrDefault(key, key);
	}

	/** @return whether {@code source} is specific enough to look up in the wiki's drop tables. */
	static boolean knownSource(String source)
	{
		return source != null && !source.isBlank() && !source.equals(PREVIEW_SOURCE) && !source.equals(UNKNOWN_SOURCE);
	}

	boolean isEmpty()
	{
		return sources.isEmpty();
	}

	void addPage(JsonArray rows, Gson gson)
	{
		for (JsonElement element : rows)
		{
			String source, item, rate;
			try
			{
				JsonObject row = element.getAsJsonObject();
				JsonObject drop = gson.fromJson(row.get("drop_json").getAsString(), JsonObject.class);
				source = text(drop, "Dropped from");
				if (source.isBlank())
				{
					// page_name_sub is the page the line sits on, which names the source the row omits.
					source = text(row, "page_name_sub");
				}
				item = text(row, "item_name");
				rate = rateText(drop);
			}
			catch (RuntimeException invalid)
			{
				// A single malformed row out of tens of thousands must not discard the whole dataset.
				continue;
			}
			if (source.isBlank() || item.isBlank())
			{
				continue;
			}
			add(source, item, rate, false);
			int variant = source.indexOf('#');
			if (variant > 0)
			{
				// A game loot event usually lacks the wiki's variant, so the base source spans them all.
				add(source.substring(0, variant), item, rate, true);
			}
			else if (source.endsWith(")") && source.indexOf(" (") > 0)
			{
				// "Skeleton (Shayzien Crypts)": the wiki adds the place to an NPC with several pages; the game does not.
				add(source.substring(0, source.indexOf(" (")), item, rate, true);
			}
		}
	}

	private void add(String source, String item, String rate)
	{
		add(source, item, rate, false);
	}

	private void add(String source, String item, String rate, boolean spansVariants)
	{
		if (rate.equals(UNKNOWN_RATE))
		{
			// An unparsable rarity must not take the key: a stored dash would mask a local rate.
			return;
		}
		String key = source.replace('_', ' ').trim();
		merge(sources, key, item, rate, spansVariants);
		String slot = CollectionLogSlotNames.slotNameOrNull(item);
		if (slot != null)
		{
			merge(slots, key, slot, rate, spansVariants);
		}
	}

	private static void merge(Map<String, Map<String, String>> target, String source, String item, String rate,
							  boolean spansVariants)
	{
		target.computeIfAbsent(source, ignored -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER))
			.merge(item, rate, spansVariants ? WikiDropRates::widen : (a, b) -> a.equals(b) ? a : VARIES);
	}

	/**
	 * Variants of one source that the game cannot tell apart: a bounded range carries more than "Varies".
	 * Rows filed under a single key stay "Varies" - those are separate drops, not the ends of one range.
	 */
	private static String widen(String current, String added)
	{
		if (current.equals(added))
		{
			return current;
		}
		String[] ends = current.split(RANGE, 2);
		String common = ends[0], rare = ends[ends.length - 1];
		double commonest = probability(common), rarest = probability(rare), value = probability(added);
		if (commonest < 0 || rarest < 0 || value < 0)
		{
			return VARIES;
		}
		if (value > commonest)
		{
			common = added;
		}
		else if (value < rarest)
		{
			rare = added;
		}
		String range = compact(common) + RANGE + compact(rare);
		return common.equals(rare) ? common : range.length() > 24 ? VARIES : range;
	}

	/** @return the chance of a plain {@code a/b} rate, or -1 for anything a range must not span. */
	static double probability(String rate)
	{
		Matcher fraction = FRACTION.matcher(rate);
		if (!fraction.matches())
		{
			return -1;
		}
		double denominator = Double.parseDouble(fraction.group(2).replace(",", ""));
		return denominator <= 0 ? -1 : Double.parseDouble(fraction.group(1).replace(",", "")) / denominator;
	}

	/** Two endpoints have to fit the popup's narrow statistic, so a range drops fractional denominators. */
	private static String compact(String rate)
	{
		Matcher fraction = FRACTION.matcher(rate);
		if (!fraction.matches())
		{
			return rate;
		}
		return fraction.group(1) + "/"
			   + number(new BigDecimal(fraction.group(2).replace(",", "")).setScale(0, RoundingMode.HALF_UP));
	}

	/**
	 * @return one source listing {@code item}, or null when none does. The settings preview is a
	 *         synthetic example, so any real source beats spanning every boss that drops the item.
	 */
	String anySource(String item, int pick)
	{
		List<String> found = new ArrayList<>();
		for (Map<String, Map<String, String>> data : List.of(sources, slots))
		{
			for (Map.Entry<String, Map<String, String>> source : data.entrySet())
			{
				// Every variant key has a base key beside it, and the base is the name the game reports.
				if (source.getKey().indexOf('#') < 0 && source.getValue().containsKey(item)
					&& !found.contains(source.getKey()))
				{
					found.add(source.getKey());
				}
			}
		}
		return found.isEmpty() ? null : found.get(Math.floorMod(pick, found.size()));
	}

	String find(String source, String item)
	{
		if (item == null)
		{
			return null;
		}
		if (knownSource(source))
		{
			String key = key(source);
			String rate = rateFrom(sources, key, item);
			return rate != null ? rate : rateFrom(slots, key, item);
		}
		String rate = acrossSources(sources, item);
		return rate != null ? rate : acrossSources(slots, item);
	}

	private static String rateFrom(Map<String, Map<String, String>> data, String source, String item)
	{
		Map<String, String> items = data.get(source);
		return items == null ? null : items.get(item);
	}

	private static String acrossSources(Map<String, Map<String, String>> data, String item)
	{
		String result = null;
		for (Map<String, String> items : data.values())
		{
			String rate = items.get(item);
			if (rate != null)
			{
				if (result != null && !result.equals(rate))
				{
					return VARIES;
				}
				result = rate;
			}
		}
		return result;
	}

	JsonObject toJson(Gson gson)
	{
		// Aliases are derived again on load, so only the wiki's own item names are cached.
		return gson.toJsonTree(sources).getAsJsonObject();
	}

	static WikiDropRates fromJson(JsonObject data) throws IOException
	{
		WikiDropRates result = new WikiDropRates();
		try
		{
			for (Map.Entry<String, JsonElement> source : data.entrySet())
			{
				for (Map.Entry<String, JsonElement> item : source.getValue().getAsJsonObject().entrySet())
				{
					String rate = item.getValue().getAsString();
					if (source.getKey().isBlank() || item.getKey().isBlank() || rate.isBlank() || rate.length() > 48)
					{
						throw new IOException("Invalid cached wiki drop rate");
					}
					result.add(source.getKey(), item.getKey(), rate);
				}
			}
		}
		catch (RuntimeException invalid)
		{
			throw new IOException("Invalid wiki drop cache", invalid);
		}
		if (result.isEmpty())
		{
			throw new IOException("Empty wiki drop data");
		}
		return result;
	}

	static String rateText(JsonObject drop)
	{
		String rarity = text(drop, "Rarity").trim();
		if (rarity.equalsIgnoreCase("varies"))
		{
			return VARIES;
		}
		if (!text(drop, "Rarity Notes").isBlank() || !text(drop, "Name Notes").isBlank()
			|| rarity.equalsIgnoreCase("conditional") || rarity.equalsIgnoreCase("once"))
		{
			return "Conditional";
		}
		// A guaranteed item - a quest, shop or event reward - has no drop rate worth showing.
		if (rarity.equalsIgnoreCase("always"))
		{
			return UNKNOWN_RATE;
		}
		String[] parts = rarity.replace(",", "").split("/", -1);
		if (parts.length != 2 || !parts[0].matches("[0-9]+(?:\\.[0-9]+)?")
			|| !parts[1].matches("[0-9]+(?:\\.[0-9]+)?"))
		{
			return UNKNOWN_RATE;
		}
		BigDecimal numerator = new BigDecimal(parts[0]), denominator = new BigDecimal(parts[1]);
		if (numerator.signum() <= 0 || denominator.compareTo(numerator) <= 0)
		{
			return UNKNOWN_RATE;
		}
		String rate = number(numerator) + "/" + number(denominator);
		String rolls = text(drop, "Rolls");
		if (!rolls.isEmpty() && !rolls.equals("1"))
		{
			if (!rolls.matches("[1-9][0-9]{0,2}"))
			{
				return VARIES;
			}
			rate = rolls + " × " + rate;
		}
		if (text(drop, "Approx").equals("true"))
		{
			rate = "~" + rate;
		}
		return rate.length() > 48 ? VARIES : rate;
	}

	/** @return {@code value} with a thousands separator only once it has five digits or more. */
	static String number(BigDecimal value)
	{
		DecimalFormat format = new DecimalFormat(value.abs().compareTo(GROUPED) < 0 ? "0.########" : "#,##0.########",
												 DecimalFormatSymbols.getInstance(Locale.ROOT));
		return format.format(value);
	}

	private static String text(JsonObject object, String key)
	{
		JsonElement value = object.get(key);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}
}
