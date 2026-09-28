/* Derived from Collection Log Popup Enhanced, copyright (c) 2026 SnakeSteak.
 * BSD-2-Clause; full terms are in licenses/enhanced.txt. */
package com.collectioncelebrations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemManager;

/**
 * Resolves a collection log item's rarity tier from completion percentage and value, combined per
 * the configured {@link RarityBasis}.
 * Value uses absolute GP cutoffs; completion and combination use population percentiles.
 * Untradeable items use completion rarity without a value contribution.
 */
@Singleton
public class RarityResolver
{
	private static final String PET_TAB = "All Pets";

	private static final double COMPLETION_WEIGHT = 0.6;
	private static final double VALUE_WEIGHT = 0.4;

	// Percentile cutoffs for the completion and composite scores.
	private static final double UNCOMMON_PERCENTILE = 50;
	private static final double RARE_PERCENTILE = 80;
	private static final double VERY_RARE_PERCENTILE = 95;

	@Inject
	net.runelite.client.plugins.grounditems.GroundItemsConfig groundItemsConfig;
	private final ItemManager itemManager;
	private final CelebrationConfig config;
	private final DropRateResolver dropRateResolver;
	private volatile CompletionData completionData;

	@Inject
	public RarityResolver(ItemManager itemManager, CelebrationConfig config, DropRateResolver dropRateResolver)
	{
		this.itemManager = itemManager;
		this.config = config;
		this.dropRateResolver = dropRateResolver;
		this.completionData = new CompletionData(Map.of());
	}

	/**
	 * Replaces the completion dataset. Safe to call from any thread: readers always see either the
	 * old or the new dataset, never a partial one.
	 */
	void reload(Map<String, CompletionEntry> raw)
	{
		this.completionData = new CompletionData(raw);
	}

	/**
	 * @return the item id the wiki dataset records for {@code name}, or {@code null} if it isn't a
	 *         known collection log item. Where several IDs share a name the lowest wins.
	 */
	public Integer datasetIdForName(String name)
	{
		return completionData.idByItemName.get(name);
	}

	/**
	 * @param tier which non-null tier to pick from
	 * @return a random item id matching {@code tier}, or {@code null} if none is available.
	 *         {@link PreviewTier#PET} draws from the pet name index, since pets never enter
	 *         {@link CompletionData#ids}. Other tiers scan a shuffled list, resolving each until one
	 *         buckets into {@code tier}.
	 */
	public Integer randomItemIdForTier(PreviewTier tier)
	{
		CompletionData data = completionData;

		if (tier == PreviewTier.PET)
		{
			List<Integer> petIds = new ArrayList<>(data.petIdByName.values());
			if (petIds.isEmpty())
			{
				return null;
			}
			return petIds.get(ThreadLocalRandom.current().nextInt(petIds.size()));
		}

		if (data.ids.isEmpty())
		{
			return null;
		}

		List<Integer> shuffled = new ArrayList<>(data.ids);
		Collections.shuffle(shuffled, ThreadLocalRandom.current());

		RarityTier targetTier = RarityTier.valueOf(tier.name());
		// Reuse one distribution across the preview candidates.
		Dataset previewDataset = buildDataset(data);
		for (int itemId : shuffled)
		{
			CompletionEntry entry = data.byId.get(itemId);
			if (entry == null || entry.name == null)
			{
				continue;
			}
			if (resolve(itemId, entry.name, previewDataset).getTier() == targetTier)
			{
				return itemId;
			}
		}
		return null;
	}

	/**
	 * @param itemId resolved item id, or -1 if it could not be resolved. Falls
	 *                back to a value-score-only composite using price 0 in that case, same as an
	 *                item with no GE price at all.
	 */
	public RarityResult resolve(int itemId, String itemName)
	{
		return resolve(itemId, itemName, null);
	}

	private RarityResult resolve(int itemId, String itemName, Dataset preparedDataset)
	{
		CompletionData data = completionData;
		int price = getPrice(itemId);
		boolean highAlch = isHighAlchPrice(itemId);
		int alchPrice = itemId >= 0 ? itemManager.getItemComposition(itemId).getHaPrice() : 0;

		if (data.petIdByName.containsKey(itemName))
		{
			return new RarityResult(RarityTier.PET, itemId, price, highAlch, compPercent(data, itemId), null, 0, 100, 0, 0, 0, alchPrice);
		}

		Double compPercent = compPercent(data, itemId);
		boolean untradeable = itemId >= 0 && CelebrationPlugin.untradeable(itemManager.getItemComposition(itemId));
		RarityBasis basis = untradeable ? RarityBasis.RARITY : config.rarityBasis();
		Double completionScore = compPercent != null ? 1 - (compPercent / 100.0) : null;
		double valueScore = untradeable ? 0 : valueScore(itemId);

		if (basis == RarityBasis.VALUE)
		{
			// Returns before buildDataset(): absolute cutoffs need no distribution to rank against.
			// A price of 0 lands in COMMON, so no drop-rate fallback is needed here.
			return new RarityResult(bucketByThreshold(price), itemId, price, highAlch, compPercent, completionScore, valueScore, 0, 0, 0, 0,
									alchPrice);
		}

		Dataset dataset = preparedDataset == null ? buildDataset(data) : preparedDataset;
		double score;
		double[] distribution;

		if (basis == RarityBasis.RARITY)
		{
			if (completionScore != null)
			{
				score = completionScore;
				distribution = dataset.completionScores;
			}
			else
			{
				Double dropScore = untradeable ? dropRarityScore(itemName) : null;
				if (dropScore == null || dataset.dropRateScores.length == 0)
				{
					return new RarityResult(RarityTier.COMMON, itemId, price, highAlch, null, null, valueScore, 0,
						dataset.compositeScores.length, 0, 0, alchPrice);
				}
				score = dropScore;
				distribution = dataset.dropRateScores;
			}
		}
		else if (completionScore != null)
		{
			score = COMPLETION_WEIGHT * completionScore + VALUE_WEIGHT * valueScore;
			distribution = dataset.compositeScores;
		}
		else if (price > 0)
		{
			// Same absolute cutoffs the VALUE basis uses, so an identical price can't tier one way
			// here and another way there.
			RarityTier tier = bucketByThreshold(price);
			return new RarityResult(tier, itemId, price, highAlch, compPercent, completionScore, valueScore, 0,
									dataset.compositeScores.length, 0, 0, alchPrice);
		}
		else
		{
			// Neither signal available - fall back to per-kill drop probability.
			Double dropRateScore = dropRarityScore(itemName);
			if (dropRateScore != null && dataset.dropRateScores.length > 0)
			{
				score = dropRateScore;
				distribution = dataset.dropRateScores;
			}
			else
			{
				return new RarityResult(RarityTier.COMMON, itemId, price, highAlch, null, null, valueScore, 0,
										dataset.compositeScores.length, 0, 0, alchPrice);
			}
		}

		double percentile = percentileRank(distribution, score);
		RarityTier tier = bucketByPercentile(percentile);
		if (basis == RarityBasis.COMBINATION)
		{
			// Completion can promote a cheap drop, but must never demote its value tier.
			RarityTier valueTier = bucketByThreshold(price);
			if (valueTier.ordinal() > tier.ordinal())
			{
				tier = valueTier;
			}
		}
		return new RarityResult(tier, itemId, price, highAlch, compPercent, completionScore, valueScore, percentile,
								dataset.compositeScores.length, 0, 0, alchPrice);
	}

	private static Double compPercent(CompletionData data, int itemId)
	{
		if (itemId < 0)
		{
			return null;
		}
		CompletionEntry entry = data.byId.get(itemId);
		return entry != null ? entry.comp : null;
	}

	/**
	 * GE price where available; falls back to high alch value for items with no GE price, since an
	 * alch value is still a better rarity signal than 0.
	 */
	private int getPrice(int itemId)
	{
		if (itemId < 0)
		{
			return 0;
		}
		// Prices are longs since RuneLite 1.13 (Jagex raised the cash stack limit).
		long gePrice = itemManager.getItemPrice(itemId);
		if (gePrice > 0)
		{
			return (int) Math.min(Integer.MAX_VALUE, gePrice);
		}
		return itemManager.getItemComposition(itemId).getHaPrice();
	}

	/**
	 * @return whether getPrice(itemId) fell back to the high alch value rather than a real GE price.
	 */
	private boolean isHighAlchPrice(int itemId)
	{
		return itemId >= 0 && itemManager.getItemPrice(itemId) <= 0;
	}

	/**
	 * @return a 0-1 rarity score (rarer = closer to 1) from {@code itemName}'s per-kill drop
	 *         probability, or {@code null} if unavailable or ambiguous across sources (see
	 *         {@link DropRateResolver#dropProbabilityByItemName}). Drop probabilities are already
	 *         bounded 0-1, so unlike price they need no log-scaling before ranking.
	 */
	private Double dropRarityScore(String itemName)
	{
		Double probability = dropRateResolver.dropProbabilityByItemName(itemName);
		return probability != null ? 1 - probability : null;
	}

	/**
	 * The value half of the COMBINATION blend, on the same 0-1 scale as completionScore. The four
	 * steps are coarse on purpose - see the resolver fallback below.
	 */
	private double valueScore(int itemId)
	{
		return valueScoreForPrice(getPrice(itemId));
	}

	private double valueScoreForPrice(int price)
	{
		switch (bucketByThreshold(price))
		{
		case VERY_RARE:
			return 1.0;
		case RARE:
			return 2 / 3.0;
		case UNCOMMON:
			return 1 / 3.0;
		default:
			return 0.0;
		}
	}

	// Reads config on every call, so an edited threshold takes effect on the next unlock.
	private RarityTier bucketByThreshold(int price)
	{
		int[] thresholds = thresholds();
		if (price >= thresholds[2])
		{
			return RarityTier.VERY_RARE;
		}
		if (price >= thresholds[1])
		{
			return RarityTier.RARE;
		}
		if (price >= thresholds[0])
		{
			return RarityTier.UNCOMMON;
		}
		return RarityTier.COMMON;
	}

	/**
	 * The three configured cutoffs as {uncommon, rare, veryRare}, clamped non-negative and sorted
	 * ascending - a user who types Rare=5m and Very rare=1m still gets monotonic tiers instead of a
	 * band that can never be reached.
	 */
	private int[] thresholds()
	{
		boolean linked = config.groundItemThresholds() && groundItemsConfig != null;
		int[] thresholds = {
			Math.max(0, linked ? groundItemsConfig.mediumValuePrice() : config.uncommonAt()),
			Math.max(0, linked ? groundItemsConfig.highValuePrice() : config.rareAt()),
			Math.max(0, linked ? groundItemsConfig.insaneValuePrice() : config.veryRareAt()),
		};
		Arrays.sort(thresholds);
		return thresholds;
	}

	private static RarityTier bucketByPercentile(double percentile)
	{
		if (percentile >= VERY_RARE_PERCENTILE)
		{
			return RarityTier.VERY_RARE;
		}
		if (percentile >= RARE_PERCENTILE)
		{
			return RarityTier.RARE;
		}
		if (percentile >= UNCOMMON_PERCENTILE)
		{
			return RarityTier.UNCOMMON;
		}
		return RarityTier.COMMON;
	}

	private static double percentileRank(double[] sortedAscending, double value)
	{
		int idx = Arrays.binarySearch(sortedAscending, value);
		int countLessOrEqual;
		if (idx >= 0)
		{
			countLessOrEqual = idx + 1;
			while (countLessOrEqual < sortedAscending.length && sortedAscending[countLessOrEqual] == value)
			{
				countLessOrEqual++;
			}
		}
		else
		{
			countLessOrEqual = -idx - 1;
		}
		return 100.0 * countLessOrEqual / sortedAscending.length;
	}

	/**
	 * Rebuilt on every call rather than cached: ~1700 in-memory lookups, unlocks are rare, and
	 * recomputing avoids stale cutoffs from a price snapshot taken before ItemManager finished its
	 * own background refresh.
	 */
	private Dataset buildDataset(CompletionData data)
	{
		// Items pending a wiki score (comp == null) have nothing to rank a percentile against.
		List<Map.Entry<Integer, CompletionEntry>> scored =
			data.byId.entrySet().stream().filter(entry -> entry.getValue().comp != null).collect(Collectors.toList());

		int n = scored.size();
		double[] compositeScores = new double[n];
		double[] completionScores = new double[n];

		int i = 0;
		for (Map.Entry<Integer, CompletionEntry> entry : scored)
		{
			// Must be the same threshold-derived score resolve() uses on the live item - ranking
			// against a differently-built distribution would skew every tier.
			double valueScore = valueScoreForPrice(getPrice(entry.getKey()));
			double completionScore = 1 - (entry.getValue().comp / 100.0);
			compositeScores[i] = COMPLETION_WEIGHT * completionScore + VALUE_WEIGHT * valueScore;
			completionScores[i] = completionScore;
			i++;
		}

		Arrays.sort(compositeScores);
		Arrays.sort(completionScores);

		// Built from the comp-less population, the same one the drop-rate fallback exists for -
		// scored items never need it and would dilute the distribution's rarity range.
		double[] dropRateScores = data.byId.values()
									  .stream()
									  .filter(entry -> entry.comp == null && entry.name != null)
									  .map(entry -> dropRarityScore(entry.name))
									  .filter(Objects::nonNull)
									  .mapToDouble(Double::doubleValue)
									  .sorted()
									  .toArray();

		return new Dataset(compositeScores, completionScores, dropRateScores);
	}

	/**
	 * One item's entry in collection-log.json. comp is null for items the wiki has not scored yet.
	 */
	static final class CompletionEntry
	{
		String name;
		List<String> tabs;
		Double comp;
	}

	// Bundled so reload() can swap all three atomically in one volatile write - readers never see
	// one updated while the others are still the old dataset.
	private static final class CompletionData
	{
		private final Map<Integer, CompletionEntry> byId;
		private final List<Integer> ids;
		private final Map<String, Integer> petIdByName;
		private final Map<String, Integer> idByItemName;

		private CompletionData(Map<String, CompletionEntry> raw)
		{
			Map<Integer, CompletionEntry> parsed = new TreeMap<>();
			if (raw != null)
			{
				raw.forEach((id, entry) -> parsed.put(Integer.parseInt(id), entry));
			}
			this.byId = Collections.unmodifiableMap(parsed);
			// Preview candidates need a completion score; pets use their separate index.
			this.ids = parsed.entrySet()
						   .stream()
						   .filter(entry -> entry.getValue().comp != null)
						   .map(Map.Entry::getKey)
						   .collect(Collectors.toList());

			Map<String, Integer> pets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			Map<String, Integer> idsByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			// parsed is a TreeMap keyed by id, so putIfAbsent keeps the lowest id for names shared by
			// several variants (see datasetIdForName).
			parsed.forEach((id, entry) -> {
				if (entry.name == null)
				{
					return;
				}
				boolean isPet = entry.tabs != null && entry.tabs.stream().anyMatch(tab -> tab.equalsIgnoreCase(PET_TAB));
				// Indexed under the log's slot name as well as the item name where the two differ
				// (see CollectionLogSlotNames) - every lookup here is keyed on the chat message's wording.
				for (String name : new String[] {entry.name, CollectionLogSlotNames.slotNameOrNull(entry.name)})
				{
					if (name == null)
					{
						continue;
					}
					idsByName.putIfAbsent(name, id);
					if (isPet)
					{
						pets.put(name, id);
					}
				}
			});
			this.petIdByName = Collections.unmodifiableMap(pets);
			this.idByItemName = Collections.unmodifiableMap(idsByName);
		}
	}

	private static final class Dataset
	{
		private final double[] compositeScores;
		private final double[] completionScores;
		private final double[] dropRateScores;

		private Dataset(double[] compositeScores, double[] completionScores, double[] dropRateScores)
		{
			this.compositeScores = compositeScores;
			this.completionScores = completionScores;
			this.dropRateScores = dropRateScores;
		}
	}
}
