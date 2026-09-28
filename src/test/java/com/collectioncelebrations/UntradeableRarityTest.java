/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class UntradeableRarityTest
{
	private ItemManager items;
	private CelebrationConfig config;
	private DropRateResolver drops;
	private RarityResolver resolver;
	private ItemComposition target;
	private Map<String, RarityResolver.CompletionEntry> data;

	@Before
	public void setup()
	{
		items = mock(ItemManager.class);
		config = mock(CelebrationConfig.class, CALLS_REAL_METHODS);
		drops = mock(DropRateResolver.class);
		ItemComposition other = mock(ItemComposition.class);
		when(other.isGeTradeable()).thenReturn(true);
		when(items.getItemComposition(anyInt())).thenReturn(other);
		target = mock(ItemComposition.class);
		when(items.getItemComposition(20)).thenReturn(target);
		resolver = new RarityResolver(items, config, drops);
		data = new HashMap<>();
		for (int id = 1; id <= 20; id++)
		{
			RarityResolver.CompletionEntry entry = new RarityResolver.CompletionEntry();
			entry.name = "Item " + id;
			entry.comp = 100.0 - id * 4;
			entry.tabs = List.of();
			data.put(String.valueOf(id), entry);
		}
		resolver.reload(data);
	}

	@Test
	public void everyBasisUsesCompletionForUntradeablesRegardlessOfPrice()
	{
		for (RarityBasis basis : RarityBasis.values())
		{
			when(config.rarityBasis()).thenReturn(basis);
			for (int price : new int[] {0, 100, 100000000})
			{
				when(items.getItemPrice(20)).thenReturn((long) price);
				when(target.getHaPrice()).thenReturn(price);
				RarityResult result = resolver.resolve(20, "Item 20");
				assertEquals(RarityTier.VERY_RARE, result.getTier());
				assertEquals(100.0, result.getPercentile(), 0.001);
				assertEquals(0.0, result.getValueScore(), 0.0);
			}
		}
	}

	@Test
	public void tradeableItemsStillRespectValueMode()
	{
		when(target.isGeTradeable()).thenReturn(true);
		when(config.rarityBasis()).thenReturn(RarityBasis.VALUE);
		assertEquals(RarityTier.COMMON, resolver.resolve(20, "Item 20").getTier());
		when(items.getItemPrice(20)).thenReturn(100000000L);
		assertEquals(RarityTier.VERY_RARE, resolver.resolve(20, "Item 20").getTier());
	}

	@Test
	public void missingCompletionUsesAvailableDropRarityInsteadOfAlch()
	{
		data.values().forEach(entry -> entry.comp = null);
		resolver.reload(data);
		when(drops.dropProbabilityByItemName(anyString())).thenReturn(0.5);
		when(drops.dropProbabilityByItemName("Item 20")).thenReturn(0.001);
		when(target.getHaPrice()).thenReturn(1);
		assertEquals(RarityTier.VERY_RARE, resolver.resolve(20, "Item 20").getTier());
	}

	@Test
	public void missingRarityDoesNotInventATierFromHighAlch()
	{
		resolver.reload(Map.of());
		when(target.getHaPrice()).thenReturn(100000000);
		when(config.rarityBasis()).thenReturn(RarityBasis.VALUE);
		assertEquals(RarityTier.COMMON, resolver.resolve(20, "Item 20").getTier());
	}
}
