package com.collectioncelebrations;

import java.util.Map;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MissingWikiDataTest
{
	@Test
	public void valueAndCombinationStillUseGoldWithoutWikiSnapshot()
	{
		ItemManager items = mock(ItemManager.class);
		when(items.getItemComposition(11832)).thenReturn(mock(ItemComposition.class));
		when(items.getItemComposition(11832).isGeTradeable()).thenReturn(true);
		when(items.getItemPrice(11832)).thenReturn(20000000L);
		for (RarityBasis basis : RarityBasis.values())
		{
			CelebrationConfig config = new CelebrationConfig() {
				@Override
				public RarityBasis rarityBasis()
				{
					return basis;
				}
			};
			DropRateResolver drops = new DropRateResolver();
			drops.reload(Map.of());
			RarityResult result = new RarityResolver(items, config, drops).resolve(11832, "Bandos chestplate");
			assertEquals(basis == RarityBasis.RARITY ? RarityTier.COMMON : RarityTier.VERY_RARE, result.getTier());
			assertNull(result.getCompPercent());
		}
	}
	@Test
	public void linkedGroundItemThresholdsUpdateWithoutCopyingSettings()
	{
		ItemManager items = mock(ItemManager.class);
		when(items.getItemPrice(1)).thenReturn(500000L);
		when(items.getItemComposition(1)).thenReturn(mock(ItemComposition.class));
		when(items.getItemComposition(1).isGeTradeable()).thenReturn(true);
		CelebrationConfig config = new CelebrationConfig() {
			@Override
			public RarityBasis rarityBasis()
			{
				return RarityBasis.VALUE;
			}
			@Override
			public boolean groundItemThresholds()
			{
				return true;
			}
		};
		RarityResolver resolver = new RarityResolver(items, config, new DropRateResolver());
		resolver.groundItemsConfig = mock(net.runelite.client.plugins.grounditems.GroundItemsConfig.class);
		when(resolver.groundItemsConfig.mediumValuePrice()).thenReturn(100000);
		when(resolver.groundItemsConfig.highValuePrice()).thenReturn(400000);
		when(resolver.groundItemsConfig.insaneValuePrice()).thenReturn(1000000);
		assertEquals(RarityTier.RARE, resolver.resolve(1, "Example").getTier());
		when(resolver.groundItemsConfig.highValuePrice()).thenReturn(600000);
		assertEquals(RarityTier.UNCOMMON, resolver.resolve(1, "Example").getTier());
	}
}
