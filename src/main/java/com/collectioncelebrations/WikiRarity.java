/* Copyright (c) 2026 maiz. Ranking formula adapted from Collection Log Popup
 * Enhanced, copyright (c) 2026 SnakeSteak. BSD-2-Clause; see licenses/enhanced.txt. */
package com.collectioncelebrations;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Filepath;

/** Wiki population statistics never establish the player's own membership. */
@Singleton
class WikiRarity
{
	@Inject
	Gson gson;
	@Inject
	WikiCompletionClient remote;
	@Inject
	CelebrationConfig config;
	@Inject
	RarityResolver resolver;
	@Inject
	DropRateResolver dropRates;
	private volatile Map<Integer, Entry> entries = Map.of();
	private volatile WikiDropRates wikiDrops = new WikiDropRates();
	/** Raised whenever stored rates gain a new shape, so an older cache is replaced rather than shown. */
	private static final int CACHE_FORMAT = 1;
	private static final long FORCE_INTERVAL = 60000L;
	private volatile long lastForced;
	private ExecutorService loader;
	private volatile int generation;
	/** The plugin directory's data folder: optional local data and the wiki caches. Set by the plugin before start. */
	Filepath dataDirectory;
	static final class Entry
	{
		final int id;
		final String name;
		final Double completion;
		final boolean pet;
		final List<String> tabs;
		Entry(int id, String name, Double completion, boolean pet, List<String> tabs)
		{
			this.id = id;
			this.name = name;
			this.completion = completion;
			this.pet = pet;
			this.tabs = tabs;
		}
	}
	/**
	 * Old School updates land on Wednesday and the wiki catches up within a day, so a cache written
	 * before the most recent Thursday is out of date. Refreshing weekly rather than daily keeps the
	 * wiki's own load to a seventh of what an every-start check would cost.
	 */
	static boolean staleSince(long fetchedAt, long now)
	{
		if (fetchedAt <= 0 || fetchedAt > now)
		{
			return true;
		}
		long thursday = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC)
						   .with(TemporalAdjusters.previousOrSame(DayOfWeek.THURSDAY))
						   .truncatedTo(ChronoUnit.DAYS).toInstant().toEpochMilli();
		return fetchedAt < thursday;
	}

	synchronized void start()
	{
		start(false);
	}

	private synchronized void start(boolean forceRefresh)
	{
		int session = ++generation;
		remote.resume();
		loader = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "collection-wiki-data");
			t.setDaemon(true);
			return t;
		});
		loader.execute(() -> {
			Filepath folder = dataDirectory;
			try (Reader reader = folder.joinSegment("collection-log.json").openBufferedReader())
			{
				Map<Integer, Entry> loaded = parse(gson.fromJson(reader, JsonObject.class));
				installCurrent(loaded, session);
			}
			catch (Exception ignored)
			{ /* Missing/unreadable data is displayed as unavailable. */
			}
			try (Reader reader = folder.joinSegment("drop-rates.json").openBufferedReader())
			{
				Map<String, Map<String, Double>> loaded = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
				gson.fromJson(reader, JsonObject.class).entrySet().forEach(source -> {
					Map<String, Double> values = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
					source.getValue().getAsJsonObject().entrySet().forEach(item -> {
						double p = item.getValue().getAsDouble();
						if (p > 0 && p <= 1)
						{
							values.put(item.getKey(), p);
						}
					});
					loaded.put(source.getKey(), values);
				});
				synchronized (this)
				{
					if (generation == session)
					{
						dropRates.reload(loaded);
					}
				}
			}
			catch (Exception ignored)
			{
			}
			if (generation == session)
			{
				// Switching refreshes off stops requests only; downloaded data stays in use.
				boolean download = config.refreshWikiData();
				refreshCompletion(folder, session, forceRefresh, download);
				refreshDropRates(folder, session, forceRefresh, download);
			}
		});
	}
	synchronized void stop()
	{
		generation++;
		remote.cancel();
		if (loader != null)
		{
			loader.shutdownNow();
			loader = null;
		}
	}
	/** Switching the setting off and on refreshes now, without waiting for the weekly cutoff. */
	synchronized void refreshSetting()
	{
		if (loader == null)
		{
			return;
		}
		// Every forced refresh is a full re-download, so flicking the switch cannot repeat one.
		long now = System.currentTimeMillis();
		boolean force = config.refreshWikiData() && now - lastForced >= FORCE_INTERVAL;
		if (force)
		{
			lastForced = now;
		}
		stop();
		start(force);
	}

	private void refreshCompletion(Filepath folder, int session, boolean forceRefresh, boolean download)
	{
		Filepath cache = folder.joinSegment("wiki-completion-cache.json");
		long now = System.currentTimeMillis();
		try (Reader reader = cache.openBufferedReader())
		{
			JsonObject saved = gson.fromJson(reader, JsonObject.class);
			Map<Integer, Entry> loaded = parse(saved.getAsJsonObject("items"));
			if (loaded.isEmpty())
			{
				throw new IllegalArgumentException("Empty cache");
			}
			if (!installCurrent(loaded, session))
			{
				return;
			}
			if (!forceRefresh && !staleSince(saved.get("fetchedAt").getAsLong(), now))
			{
				return;
			}
		}
		catch (Exception ignored)
		{ /* A stale/local dataset stays usable while refreshing. */
		}
		if (!download || generation != session)
		{
			return;
		}
		try
		{
			JsonObject data = remote.fetch();
			Map<Integer, Entry> loaded = parse(data);
			if (!installCurrent(loaded, session))
			{
				return;
			}
			JsonObject saved = new JsonObject();
			saved.addProperty("fetchedAt", now);
			saved.addProperty("attribution", "OSRS Wiki contributors: Module:Collection_log/data.json and completion.json");
			saved.addProperty("source", "https://oldschool.runescape.wiki/w/Module:Collection_log");
			saved.addProperty("license", "https://creativecommons.org/licenses/by-nc-sa/3.0/");
			saved.addProperty("changes", "Joined item list and completion percentages by item ID");
			saved.add("items", data);
			write(folder, "wiki-completion-", cache, saved, session);
		}
		catch (Exception failure)
		{
			org.slf4j.LoggerFactory.getLogger(WikiRarity.class)
				.debug("Wiki completion refresh unavailable; keeping existing data", failure);
		}
	}

	private void refreshDropRates(Filepath folder, int session, boolean forceRefresh, boolean download)
	{
		if (generation != session)
		{
			return;
		}
		Filepath cache = folder.joinSegment("wiki-drop-rates-cache.json");
		long now = System.currentTimeMillis();
		try (Reader reader = cache.openBufferedReader())
		{
			JsonObject saved = gson.fromJson(reader, JsonObject.class);
			// A cache written by an older build holds differently formatted rates, not merely older
			// ones, so it is left uninstalled: local rates stay in charge until the refetch lands.
			if (saved.get("format") != null && saved.get("format").getAsInt() == CACHE_FORMAT)
			{
				WikiDropRates loaded = WikiDropRates.fromJson(saved.getAsJsonObject("rates"));
				if (!installDropRates(loaded, session))
				{
					return;
				}
				if (!forceRefresh && !staleSince(saved.get("fetchedAt").getAsLong(), now))
				{
					return;
				}
			}
		}
		catch (Exception ignored)
		{ /* Keep the existing rates if a cache is absent or damaged. */
		}
		if (!download || generation != session)
		{
			return;
		}
		try
		{
			WikiDropRates loaded = remote.fetchDropRates();
			if (!installDropRates(loaded, session))
			{
				return;
			}
			JsonObject saved = new JsonObject();
			saved.addProperty("fetchedAt", System.currentTimeMillis());
			saved.addProperty("format", CACHE_FORMAT);
			saved.addProperty("attribution", "OSRS Wiki contributors: Bucket:Dropsline");
			saved.addProperty("source", "https://oldschool.runescape.wiki/w/Bucket:Dropsline");
			saved.addProperty("license", "https://creativecommons.org/licenses/by-nc-sa/3.0/");
			saved.addProperty("changes", "Source and item lookup; formatted rates, roll counts and ambiguity labels");
			saved.add("rates", loaded.toJson(gson));
			write(folder, "wiki-drop-rates-", cache, saved, session);
		}
		catch (Exception failure)
		{
			org.slf4j.LoggerFactory.getLogger(WikiRarity.class)
				.debug("Wiki drop rate refresh unavailable; keeping existing data", failure);
		}
	}

	/** Through a temporary file moved over {@code cache}, only while this session is still the current one. */
	private void write(Filepath folder, String prefix, Filepath cache, JsonObject saved, int session) throws Exception
	{
		folder.createDirectories();
		Filepath temporary = folder.createTempFile(prefix, ".tmp");
		try
		{
			// Streamed, not rendered to a String first: the drop cache runs to megabytes.
			try (Writer writer = temporary.openBufferedWriter())
			{
				gson.toJson(saved, writer);
			}
			synchronized (this)
			{
				if (generation == session)
				{
					temporary.moveTo(cache, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		finally
		{
			temporary.deleteIfExists();
		}
	}

	private synchronized boolean installDropRates(WikiDropRates loaded, int session)
	{
		if (generation != session)
		{
			return false;
		}
		wikiDrops = loaded;
		return true;
	}

	static Map<Integer, Entry> parse(JsonObject root)
	{
		Map<Integer, Entry> result = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> row : root.entrySet())
		{
			JsonObject obj = row.getValue().getAsJsonObject();
			JsonElement itemName = obj.get("name");
			// Wiki snapshots can contain unnamed placeholders. One placeholder must
			// not prevent every valid collection item from being recognized.
			if (itemName == null || itemName.isJsonNull() || !itemName.isJsonPrimitive() ||
				!itemName.getAsJsonPrimitive().isString() || itemName.getAsString().isBlank())
			{
				continue;
			}
			int id = Integer.parseInt(row.getKey());
			Double comp = obj.has("comp") && !obj.get("comp").isJsonNull() ? obj.get("comp").getAsDouble() : null;
			if (id < 0 || (comp != null && (!Double.isFinite(comp) || comp < 0 || comp > 100)))
			{
				continue;
			}
			boolean pet = false;
			List<String> tabs = new ArrayList<>();
			if (obj.has("tabs"))
			{
				for (JsonElement tab : obj.getAsJsonArray("tabs"))
				{
					tabs.add(tab.getAsString());
					pet |= "All Pets".equalsIgnoreCase(tab.getAsString());
				}
			}
			result.put(id, new Entry(id, itemName.getAsString(), comp, pet, List.copyOf(tabs)));
		}
		return Collections.unmodifiableMap(result);
	}
	private synchronized boolean installCurrent(Map<Integer, Entry> loaded, int session)
	{
		if (generation != session)
		{
			return false;
		}
		install(loaded);
		return true;
	}

	private void install(Map<Integer, Entry> loaded)
	{
		Map<String, RarityResolver.CompletionEntry> raw = new LinkedHashMap<>();
		loaded.forEach((id, e) -> {
			RarityResolver.CompletionEntry value = new RarityResolver.CompletionEntry();
			value.name = e.name;
			value.comp = e.completion;
			value.tabs = e.tabs;
			raw.put(String.valueOf(id), value);
		});
		resolver.reload(raw);
		entries = loaded;
	}
	Entry entry(int id, String name)
	{
		Entry e = entries.get(id);
		if (e != null)
		{
			return e;
		}
		Integer mapped = resolver.datasetIdForName(name);
		return mapped == null ? null : entries.get(mapped);
	}
	RarityResult resolve(int id, String name)
	{
		return resolver.resolve(id, name);
	}

	/** @return a wiki source listing {@code name}, for the settings preview, or null when none does. */
	String previewSource(String name, int pick)
	{
		return wikiDrops.anySource(name, pick);
	}

	String dropRateText(String source, String name)
	{
		Double exact = source == null ? null : dropRates.dropProbability(source, name);
		if (exact != null)
		{
			return probabilityText(exact);
		}
		String online = wikiDrops.find(source, name);
		if (online != null)
		{
			return online;
		}
		// Withhold the name-only rate only where the wiki actually lists this source, so an item it
		// does not carry stays blank rather than borrowing another source's number. For a source the
		// tables never mention - an unloaded dataset, or a name the game words differently - the
		// local dataset remains the best answer there is.
		if (wikiDrops.covers(source))
		{
			return null;
		}
		List<DropRateResolver.SourceRate> matches = dropRates.dropRatesByItemName(name);
		if (matches.size() == 1)
		{
			return probabilityText(matches.get(0).getProbability());
		}
		if (matches.size() > 1)
		{
			return "Varies";
		}
		return null;
	}
	private static String probabilityText(double value)
	{
		return "1/" + WikiDropRates.number(java.math.BigDecimal.valueOf(Math.round(1 / value)));
	}

	Entry example(PreviewTier tier)
	{
		Integer id = resolver.randomItemIdForTier(tier);
		return id == null ? null : entries.get(id);
	}
}
