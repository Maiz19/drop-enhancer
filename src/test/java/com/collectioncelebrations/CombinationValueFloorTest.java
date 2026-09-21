package com.collectioncelebrations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CombinationValueFloorTest
{
	private ItemManager items;
	private CelebrationConfig config;
	private RarityResolver resolver;
	private Map<String, RarityResolver.CompletionEntry> data;
	private ItemComposition target;

	@Before
	public void setup()
	{
		items = mock(ItemManager.class);
		config = mock(CelebrationConfig.class, CALLS_REAL_METHODS);
		ItemComposition tradeable = mock(ItemComposition.class);
		when(tradeable.isGeTradeable()).thenReturn(true);
		when(items.getItemComposition(anyInt())).thenReturn(tradeable);
		target = mock(ItemComposition.class);
		when(target.isGeTradeable()).thenReturn(true);
		when(items.getItemComposition(1)).thenReturn(target);
		when(items.getItemPrice(anyInt())).thenReturn(100_000_000);
		data = new HashMap<>();
		for (int id = 1; id <= 100; id++)
		{
			RarityResolver.CompletionEntry entry = new RarityResolver.CompletionEntry();
			entry.name = id == 1 ? "Tormented synapse" : "Example " + id;
			entry.comp = id == 1 ? 100.0 : 0.0;
			entry.tabs = List.of();
			data.put(String.valueOf(id), entry);
		}
		resolver = new RarityResolver(items, config, new DropRateResolver());
		resolver.groundItemsConfig = mock(GroundItemsConfig.class);
		when(resolver.groundItemsConfig.mediumValuePrice()).thenReturn(100_000);
		when(resolver.groundItemsConfig.highValuePrice()).thenReturn(1_000_000);
		when(resolver.groundItemsConfig.insaneValuePrice()).thenReturn(10_000_000);
		resolver.reload(data);
	}

	private RarityTier tier(int price)
	{
		when(items.getItemPrice(1)).thenReturn(price);
		return resolver.resolve(1, "Tormented synapse").getTier();
	}

	@Test
	public void expensiveSynapseCannotBeDemotedByHighCompletion()
	{
		assertTrue(config.groundItemThresholds());
		assertEquals(RarityBasis.COMBINATION, config.rarityBasis());
		assertEquals(RarityTier.VERY_RARE, tier(38_000_000));
		assertEquals(RarityTier.VERY_RARE, tier(40_000_000));
	}

	@Test
	public void floorFollowsGroundItemsBoundariesAndEdits()
	{
		assertEquals(RarityTier.COMMON, tier(99_999));
		assertEquals(RarityTier.UNCOMMON, tier(100_000));
		assertEquals(RarityTier.UNCOMMON, tier(999_999));
		assertEquals(RarityTier.RARE, tier(1_000_000));
		assertEquals(RarityTier.RARE, tier(9_999_999));
		assertEquals(RarityTier.VERY_RARE, tier(10_000_000));
		when(resolver.groundItemsConfig.insaneValuePrice()).thenReturn(50_000_000);
		assertEquals(RarityTier.RARE, tier(40_000_000));
	}

	@Test
	public void explicitUnlinkedCutoffsRemainUsable()
	{
		when(config.groundItemThresholds()).thenReturn(false);
		when(config.veryRareAt()).thenReturn(50_000_000);
		assertEquals(RarityTier.RARE, tier(40_000_000));
		assertEquals(RarityTier.VERY_RARE, tier(50_000_000));
	}

	@Test
	public void wikiCanStillPromoteCheapItems()
	{
		when(items.getItemPrice(anyInt())).thenReturn(1000);
		data.values().forEach(entry -> entry.comp = 100.0);
		data.get("1").comp = 0.1;
		resolver.reload(data);
		assertEquals(RarityTier.VERY_RARE, tier(1000));
	}

	@Test
	public void wikiOnlyUntradeablesAndPetsKeepTheirOwnRules()
	{
		when(config.rarityBasis()).thenReturn(RarityBasis.RARITY);
		assertEquals(RarityTier.COMMON, tier(40_000_000));
		when(config.rarityBasis()).thenReturn(RarityBasis.COMBINATION);
		when(target.isGeTradeable()).thenReturn(false);
		assertEquals(RarityTier.COMMON, tier(40_000_000));
		data.get("1").tabs = List.of("All Pets");
		resolver.reload(data);
		assertEquals(RarityTier.PET, tier(40_000_000));
	}
}
