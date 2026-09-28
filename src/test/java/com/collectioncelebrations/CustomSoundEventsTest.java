/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.util.List;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class CustomSoundEventsTest
{
	private CustomSoundEvents events;
	@Before
	public void setup()
	{
		events = new CustomSoundEvents();
		events.config = mock(CelebrationConfig.class, CALLS_REAL_METHODS);
		events.soundQueue = mock(SoundQueue.class);
		events.itemManager = mock(ItemManager.class);
		// The real canonicalize maps noted/placeholder ids onto the tradeable item; these tests
		// already use canonical ids, so it is the identity here.
		when(events.itemManager.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
		events.groundItemsConfig = mock(GroundItemsConfig.class);
		when(events.groundItemsConfig.getHighlightItems()).thenReturn("Test item");
		events.startUp();
		when(events.config.highlightSound()).thenReturn(false);
		when(events.config.defaultValueSound()).thenReturn(false);
		when(events.config.lowValueSound()).thenReturn(false);
		when(events.config.mediumValueSound()).thenReturn(false);
		when(events.config.highValueSound()).thenReturn(false);
		when(events.config.highestValueSound()).thenReturn(false);
	}

	@Test
	public void highestOwnTierHasNoUpperLimit()
	{
		when(events.config.dropValueMode()).thenReturn(DropValueMode.GE);
		when(events.config.highestValueSound()).thenReturn(true);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Value test");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		LootReceived loot = new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 2)), 1, null);
		for (int price : new int[]{5000000, 500000000, Integer.MAX_VALUE})
		{
			clearInvocations(events.soundQueue);
			when(events.itemManager.getItemPrice(1)).thenReturn((long) price);
			events.onLootReceived(loot, id -> false);
			verify(events.soundQueue).offerValue("custom-sounds-veryrare.wav", 50, "value test", -1L, PreviewTier.VERY_RARE.ordinal());
			verifyNoMoreInteractions(events.soundQueue);
			org.junit.Assert.assertEquals("custom-sounds-veryrare.wav", events.highlightedRewardFile(1, 2, null));
		}
	}

	@Test
	public void shutdownWithImmutableHighlightListAndRestartWorks()
	{
		events.shutDown();
		events.startUp();
		events.shutDown();
		when(events.groundItemsConfig.getHighlightItems()).thenReturn("");
		events.startUp();
		events.shutDown();
	}
	@Test
	public void configuredRewardFileAndVolumeKeepTheRewardHold()
	{
		when(events.config.highlightSound()).thenReturn(true);
		when(events.config.fileRare()).thenReturn("my-reward.wav");
		when(events.config.volumeRare()).thenReturn(40);
		when(events.config.masterVolume()).thenReturn(50);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Test item");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		events.onLootReceived(new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 1)), 1, null), id -> false);
		verify(events.soundQueue).offerValue("my-reward.wav", 20, "test item", -1L, PreviewTier.RARE.ordinal());
	}

	@Test
	public void independentPriceBasisControlsValueBandIncludingQuantity()
	{
		when(events.config.dropValueMode()).thenReturn(DropValueMode.HIGH_ALCH);
		when(events.config.defaultValueSound()).thenReturn(true);
		when(events.config.defaultStart()).thenReturn(200);
		when(events.config.defaultEnd()).thenReturn(500);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Value test");
		when(item.getHaPrice()).thenReturn(100);
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		when(events.itemManager.getItemPrice(1)).thenReturn(1000L);
		events.onLootReceived(new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 2)), 1, null), id -> false);
		verify(events.soundQueue).offerValue("custom-sounds-common.wav", 50, "value test", -1L, PreviewTier.COMMON.ordinal());
		verify(events.groundItemsConfig, never()).valueCalculationMode();
	}
	@Test
	public void clearingHighlightListStopsHighlightSound()
	{
		when(events.config.highlightSound()).thenReturn(true);
		when(events.config.masterVolume()).thenReturn(50);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Test item");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		LootReceived loot = new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 1)), 1, null);
		events.onLootReceived(loot, id -> false);
		verify(events.soundQueue).offerValue("custom-sounds-rare.wav", 50, "test item", -1L, PreviewTier.RARE.ordinal());
		when(events.groundItemsConfig.getHighlightItems()).thenReturn("");
		ConfigChanged changed = new ConfigChanged();
		changed.setGroup("grounditems");
		changed.setKey("highlightedItems");
		events.onConfigChanged(changed);
		// No value tier is enabled; calculation mode is only used for choosing the numeric price.
		when(events.groundItemsConfig.valueCalculationMode())
			.thenReturn(net.runelite.client.plugins.grounditems.config.ValueCalculationMode.GE);
		clearInvocations(events.soundQueue);
		events.onLootReceived(loot, id -> false);
		verifyNoInteractions(events.soundQueue);
	}
	@Test
	public void collectionItemsNeverGetValueBandAudio()
	{
		when(events.config.defaultValueSound()).thenReturn(true);
		when(events.config.defaultStart()).thenReturn(0);
		when(events.config.defaultEnd()).thenReturn(1000000);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Mole claw");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		events.onLootReceived(new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 1)), 1, null), id -> true);
		verifyNoInteractions(events.soundQueue);
	}

	@Test
	public void hiddenValueSoundIsMutedOnlyWhenFollowingGroundItems()
	{
		when(events.config.defaultValueSound()).thenReturn(true);
		when(events.config.defaultStart()).thenReturn(0);
		when(events.config.defaultEnd()).thenReturn(1000000);
		when(events.groundItemsConfig.getHiddenItems()).thenReturn("Mithril *");
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Mithril sword");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		LootReceived loot = new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 1)), 1, null);
		events.onLootReceived(loot, id -> false);
		verifyNoInteractions(events.soundQueue);
		when(events.config.dropValueMode()).thenReturn(DropValueMode.GE);
		events.onLootReceived(loot, id -> false);
		verify(events.soundQueue).offerValue("custom-sounds-common.wav", 50, "mithril sword", -1L, PreviewTier.COMMON.ordinal());
	}

	@Test
	public void groundItemsUsesLiveThresholdsInsteadOfOwnRanges()
	{
		when(events.groundItemsConfig.lowValuePrice()).thenReturn(100);
		when(events.groundItemsConfig.mediumValuePrice()).thenReturn(200);
		when(events.groundItemsConfig.highValuePrice()).thenReturn(300);
		when(events.groundItemsConfig.insaneValuePrice()).thenReturn(400);
		when(events.config.lowValueSound()).thenReturn(true);
		when(events.config.mediumValueSound()).thenReturn(true);
		when(events.config.highValueSound()).thenReturn(true);
		when(events.config.highestValueSound()).thenReturn(true);
		when(events.config.lowStart()).thenReturn(999999);
		when(events.config.lowEnd()).thenReturn(1000000);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Value test");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		LootReceived loot = new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 2)), 1, null);
		int[] unitPrices = {51, 101, 151, 201};
		String[] files = {"custom-sounds-common.wav", "drop-enhancer-uncommon.wav", "custom-sounds-rare.wav", "custom-sounds-veryrare.wav"};
		for (int n = 0; n < unitPrices.length; n++)
		{
			clearInvocations(events.soundQueue);
			when(events.itemManager.getItemPrice(1)).thenReturn((long) unitPrices[n]);
			events.onLootReceived(loot, id -> false);
			verify(events.soundQueue).offerValue(files[n], 50, "value test", -1L, n);
			verifyNoMoreInteractions(events.soundQueue);
		}
		clearInvocations(events.soundQueue);
		when(events.groundItemsConfig.insaneValuePrice()).thenReturn(500);
		events.onLootReceived(loot, id -> false);
		verify(events.soundQueue).offerValue("custom-sounds-rare.wav", 50, "value test", -1L, PreviewTier.RARE.ordinal());
	}

	@Test
	public void defaultIsOptInAndGroundItemBoundaryIsStrict()
	{
		org.junit.Assert.assertFalse(new CelebrationConfig() {}.defaultValueSound());
		when(events.groundItemsConfig.lowValuePrice()).thenReturn(100);
		when(events.groundItemsConfig.mediumValuePrice()).thenReturn(200);
		when(events.groundItemsConfig.highValuePrice()).thenReturn(300);
		when(events.groundItemsConfig.insaneValuePrice()).thenReturn(400);
		when(events.config.lowValueSound()).thenReturn(true);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Value test");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		when(events.itemManager.getItemPrice(1)).thenReturn(100L);
		LootReceived loot = new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 1)), 1, null);
		events.onLootReceived(loot, id -> false);
		verifyNoInteractions(events.soundQueue);
		when(events.config.defaultValueSound()).thenReturn(true);
		events.onLootReceived(loot, id -> false);
		verify(events.soundQueue).offerValue("custom-sounds-common.wav", 50, "value test", -1L, PreviewTier.COMMON.ordinal());
	}

	@Test
	public void valueDropUsesSharedTierFileVolumeAndMute()
	{
		when(events.config.dropValueMode()).thenReturn(DropValueMode.GE);
		when(events.config.highValueSound()).thenReturn(true);
		when(events.config.highStart()).thenReturn(100);
		when(events.config.highEnd()).thenReturn(1000);
		when(events.config.fileRare()).thenReturn("shared-custom.wav");
		when(events.config.volumeRare()).thenReturn(40);
		when(events.config.masterVolume()).thenReturn(50);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Value test");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		when(events.itemManager.getItemPrice(1)).thenReturn(200L);
		LootReceived loot = new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 1)), 1, null);
		events.onLootReceived(loot, id -> false);
		verify(events.soundQueue)
			.offerValue(TierStyle.file(PreviewTier.RARE, events.config), TierStyle.volume(PreviewTier.RARE, events.config), "value test", -1L,
				PreviewTier.RARE.ordinal());
		verify(events.soundQueue).offerValue("shared-custom.wav", 20, "value test", -1L, PreviewTier.RARE.ordinal());
		clearInvocations(events.soundQueue);
		when(events.config.soundEnabledRare()).thenReturn(false);
		events.onLootReceived(loot, id -> false);
		verify(events.soundQueue).offerValue("shared-custom.wav", 0, "value test", -1L, PreviewTier.RARE.ordinal());
	}

	@Test
	public void highlightedItemCustomSoundOverridesSharedTier()
	{
		when(events.config.highlightSound()).thenReturn(true);
		when(events.config.highlightedFile()).thenReturn("highlight.wav");
		when(events.config.highlightedVolume()).thenReturn(60);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Test item");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		events.onLootReceived(new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 1)), 1, null), id -> false);
		verify(events.soundQueue).offerValue("highlight.wav", 30, "test item", -1L, PreviewTier.RARE.ordinal());
	}
	@Test
	public void highlightedDefaultsToCommonAndHigherValuesWin()
	{
		org.junit.Assert.assertFalse(new CelebrationConfig() {}.highlightSound());
		when(events.config.mediumValueSound()).thenReturn(true);
		when(events.config.highValueSound()).thenReturn(true);
		when(events.config.highestValueSound()).thenReturn(true);
		when(events.groundItemsConfig.mediumValuePrice()).thenReturn(200);
		when(events.groundItemsConfig.highValuePrice()).thenReturn(300);
		when(events.groundItemsConfig.insaneValuePrice()).thenReturn(400);
		when(events.groundItemsConfig.getHighlightItems()).thenReturn("Broken * > 1");
		events.startUp();
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Broken antler");
		when(events.itemManager.getItemComposition(31086)).thenReturn(item);
		LootReceived loot = new LootReceived("Mature custodian stalker", 1, LootRecordType.NPC,
			List.of(new ItemStack(31086, 2)), 1, null);
		int[] unitPrices = {0, 100, 150, 151, 200, 201};
		String[] files = {"custom-sounds-common.wav", "custom-sounds-common.wav", "drop-enhancer-uncommon.wav",
			"custom-sounds-rare.wav", "custom-sounds-rare.wav", "custom-sounds-veryrare.wav"};
		int[] tiers = {0, 0, 1, 2, 2, 3};
		for (int n = 0; n < unitPrices.length; n++)
		{
			clearInvocations(events.soundQueue);
			when(events.itemManager.getItemPrice(31086)).thenReturn((long) unitPrices[n]);
			events.onLootReceived(loot, id -> false);
			verify(events.soundQueue).offerValue(files[n], 50, "broken antler", -1L, tiers[n]);
			org.junit.Assert.assertEquals(files[n], events.highlightedRewardFile(31086, 2, null));
			org.junit.Assert.assertEquals(50, events.highlightedRewardVolume(31086, 2, null));
			verifyNoMoreInteractions(events.soundQueue);
		}
	}

	@Test
	public void highlightedCustomRangesRespectPriceBasisAndMute()
	{
		when(events.config.dropValueMode()).thenReturn(DropValueMode.HIGH_ALCH);
		when(events.config.mediumValueSound()).thenReturn(true);
		when(events.config.highValueSound()).thenReturn(true);
		when(events.config.highStart()).thenReturn(300);
		when(events.config.highEnd()).thenReturn(1000);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Test item");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		when(events.itemManager.getItemPrice(1)).thenReturn(1000000L);
		LootReceived loot = new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 2)), 1, null);
		events.onLootReceived(loot, id -> false);
		verify(events.soundQueue).offerValue("custom-sounds-common.wav", 50, "test item", -1L, PreviewTier.COMMON.ordinal());
		clearInvocations(events.soundQueue);
		when(item.getHaPrice()).thenReturn(150);
		events.onLootReceived(loot, id -> false);
		verify(events.soundQueue).offerValue("custom-sounds-rare.wav", 50, "test item", -1L, PreviewTier.RARE.ordinal());
		clearInvocations(events.soundQueue);
		when(events.config.highValueSound()).thenReturn(false);
		events.onLootReceived(loot, id -> false);
		verifyNoInteractions(events.soundQueue);
	}

	@Test
	public void highlightedCollectionItemHasNoDuplicateStandaloneSound()
	{
		when(events.config.highlightSound()).thenReturn(true);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Test item");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		events.onLootReceived(new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 1)), 1, null), id -> true);
		verifyNoInteractions(events.soundQueue);
	}

	@Test
	public void exactHiddenRuleOutranksWildcardHighlight()
	{
		when(events.groundItemsConfig.getHighlightItems()).thenReturn("Broken *");
		when(events.groundItemsConfig.getHiddenItems()).thenReturn("Broken antler");
		events.startUp();
		org.junit.Assert.assertFalse(events.highlighted("Broken antler", 1));
		when(events.groundItemsConfig.getHighlightItems()).thenReturn("Broken antler");
		events.startUp();
		org.junit.Assert.assertTrue(events.highlighted("Broken antler", 1));
	}

	@Test
	public void highlightedRarityAndValueChooseHigherTierWithoutDefeatingMute()
	{
		ItemComposition item = mock(ItemComposition.class);
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		when(events.groundItemsConfig.mediumValuePrice()).thenReturn(100);
		when(events.groundItemsConfig.highValuePrice()).thenReturn(200);
		when(events.groundItemsConfig.insaneValuePrice()).thenReturn(300);
		org.junit.Assert.assertEquals("custom-sounds-rare.wav", events.highlightedRewardFile(1, 1, PreviewTier.RARE));
		org.junit.Assert.assertEquals(50, events.highlightedRewardVolume(1, 1, PreviewTier.RARE));
		when(events.config.soundEnabledRare()).thenReturn(false);
		org.junit.Assert.assertEquals(0, events.highlightedRewardVolume(1, 1, PreviewTier.RARE));
		when(events.itemManager.getItemPrice(1)).thenReturn(400L);
		org.junit.Assert.assertEquals("custom-sounds-veryrare.wav", events.highlightedRewardFile(1, 1, PreviewTier.UNCOMMON));
		org.junit.Assert.assertEquals("drop-enhancer-pet.wav", events.highlightedRewardFile(1, 1, PreviewTier.PET));
	}

	@Test
	public void clueAndValueSoundsOfOneRewardShareItsGroup()
	{
		when(events.config.dropValueMode()).thenReturn(DropValueMode.GE);
		when(events.config.lowValueSound()).thenReturn(true);
		when(events.config.masterVolume()).thenReturn(50);
		ItemComposition clue = mock(ItemComposition.class);
		when(clue.getName()).thenReturn("Clue scroll (elite)");
		when(events.itemManager.getItemComposition(1)).thenReturn(clue);
		ItemComposition platebody = mock(ItemComposition.class);
		when(platebody.getName()).thenReturn("Rune platebody");
		when(events.itemManager.getItemComposition(2)).thenReturn(platebody);
		when(events.itemManager.getItemPrice(2)).thenReturn(38_000L);
		events.onLootReceived(new LootReceived("Hallowed Sepulchre Grand Coffin", 0, LootRecordType.NPC,
			List.of(new ItemStack(1, 1), new ItemStack(2, 1)), 1, null), id -> false, 5);
		verify(events.soundQueue).offerValue("custom-sounds-veryrare.wav", 50, "clue scroll (elite)", 5L, PreviewTier.VERY_RARE.ordinal());
		verify(events.soundQueue).offerValue("custom-sounds-common.wav", 50, "rune platebody", 5L, PreviewTier.COMMON.ordinal());
		verifyNoMoreInteractions(events.soundQueue);
	}

	@Test
	public void highlightedSoundCanBeDisabledWithoutChangingNormalValueRules()
	{
		org.junit.Assert.assertTrue(new CelebrationConfig() {}.highlightedItemSound());
		when(events.config.highlightedItemSound()).thenReturn(false);
		ItemComposition item = mock(ItemComposition.class);
		when(item.getName()).thenReturn("Test item");
		when(events.itemManager.getItemComposition(1)).thenReturn(item);
		events.onLootReceived(new LootReceived("Test", 1, LootRecordType.NPC, List.of(new ItemStack(1, 1)), 1, null), id -> false);
		verifyNoInteractions(events.soundQueue);
	}

}
