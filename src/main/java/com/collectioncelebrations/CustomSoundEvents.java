/* Derived from Custom Sounds, copyright (c) 2023 daanbom.
 * BSD-2-Clause; full terms are in licenses/custom-sounds.txt. */
package com.collectioncelebrations;

import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.plugins.loottracker.LootReceived;

import javax.inject.Inject;
import java.util.Locale;

@javax.inject.Singleton
public class CustomSoundEvents
{
	@Inject
	CelebrationConfig config;

	@Inject
	SoundQueue soundQueue;

	@Inject
	GroundItemsConfig groundItemsConfig;

	@Inject
	ItemManager itemManager;

	private static final String HIGHLIGHTED_SOUND_FILE = "highlighted_sound.wav";
	private static final String BEGINNER_CLUE_SOUND_FILE = "beginner_clue_sound.wav";
	private static final String EASY_CLUE_SOUND_FILE = "easy_clue_sound.wav";
	private static final String MEDIUM_CLUE_SOUND_FILE = "medium_clue_sound.wav";
	private static final String HARD_CLUE_SOUND_FILE = "hard_clue_sound.wav";
	private static final String ELITE_CLUE_SOUND_FILE = "elite_clue_sound.wav";
	private static final String MASTER_CLUE_SOUND_FILE = "master_clue_sound.wav";
	private static final String LOW_SOUND_FILE = "low_sound.wav";
	private static final String MEDIUM_SOUND_FILE = "medium_sound.wav";

	private static final String HIGH_SOUND_FILE = "high_sound.wav";
	private static final String HIGHEST_SOUND_FILE = "highest_sound.wav";
	private static final String DEFAULT_SOUND_FILE = "default_sound.wav";

	private String highlightedItems = "";

	public void startUp()
	{
		updateHighlightedItemsList();
	}

	public void shutDown()
	{
		highlightedItems = "";
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{

		if (configChanged.getGroup().equals("grounditems") && configChanged.getKey().equals("highlightedItems"))
		{
			updateHighlightedItemsList();
		}
	}

	public void onLootReceived(LootReceived lootReceived, java.util.function.IntPredicate collectionItem)
	{
		onLootReceived(lootReceived, collectionItem, -1);
	}

	public void onLootReceived(LootReceived lootReceived, java.util.function.IntPredicate collectionItem, long group)
	{
		for (ItemStack stack : lootReceived.getItems())
		{
			// Noted and placeholder ids resolve to the tradeable item, so name, price and the
			// collection check below all describe the same item the popup shows.
			int id = itemManager.canonicalize(stack.getId());
			handleItem(id, stack.getQuantity(), collectionItem.test(id), group);
		}
	}

	private void handleItem(int id, int quantity, boolean collectionItem, long group)
	{
		final ItemComposition itemComposition = itemManager.getItemComposition(id);
		if (itemComposition == null || itemComposition.getName() == null || quantity <= 0)
		{
			return;
		}
		final String name = itemComposition.getName().toLowerCase(Locale.ROOT);

		if (!collectionItem && config.highlightedItemSound() && config.highlightSound() && highlighted(name, quantity))
		{
			playSound(HIGHLIGHTED_SOUND_FILE);
			return;
		}

		final int gePrice = (int)Math.min(Integer.MAX_VALUE, (long)Math.max(0, itemManager.getItemPrice(id)) * quantity);
		final int haPrice = (int)Math.min(Integer.MAX_VALUE, (long)Math.max(0, itemComposition.getHaPrice()) * quantity);
		final int value = getValueByMode(gePrice, haPrice);

		if (config.beginnerClueSound() && name.contains("clue scroll (beginner)"))
		{
			playSound(BEGINNER_CLUE_SOUND_FILE);
		}
		if (config.easyClueSound() && name.contains("clue scroll (easy)"))
		{
			playSound(EASY_CLUE_SOUND_FILE);
		}
		if (config.mediumClueSound() && name.contains("clue scroll (medium)"))
		{
			playSound(MEDIUM_CLUE_SOUND_FILE);
		}
		if (config.hardClueSound() && name.contains("clue scroll (hard)"))
		{
			playSound(HARD_CLUE_SOUND_FILE);
		}

		if (config.eliteClueSound() && name.contains("clue scroll (elite)"))
		{
			playSound(ELITE_CLUE_SOUND_FILE);
		}
		if (config.masterClueSound() && name.contains("clue scroll (master)"))
		{
			playSound(MASTER_CLUE_SOUND_FILE);
		}

		if (collectionItem)
		{
			return;
		}
		if (config.highlightedItemSound() && highlighted(name, quantity))
		{
			String file = highlightedValueFile(value);
			if (valueSoundEnabled(file))
			{
				playValueSound(file, name, group);
			}
			return;
		}
		if (config.dropValueMode() == DropValueMode.GROUND_ITEMS &&
			GroundItemSoundFilter.hidden(groundItemsConfig, name, quantity, gePrice, haPrice, itemComposition.isTradeable()))
		{
			return;
		}

		if (config.dropValueMode() == DropValueMode.GROUND_ITEMS)
		{
			// Ground Items colours use strict > comparisons at each configured price boundary.
			if (value > groundItemsConfig.insaneValuePrice())
			{
				if (config.highestValueSound())
				{
					playValueSound(HIGHEST_SOUND_FILE, name, group);
				}
			}
			else if (value > groundItemsConfig.highValuePrice())
			{
				if (config.highValueSound())
				{
					playValueSound(HIGH_SOUND_FILE, name, group);
				}
			}
			else if (value > groundItemsConfig.mediumValuePrice())
			{
				if (config.mediumValueSound())
				{
					playValueSound(MEDIUM_SOUND_FILE, name, group);
				}
			}
			else if (value > groundItemsConfig.lowValuePrice())
			{
				if (config.lowValueSound())
				{
					playValueSound(LOW_SOUND_FILE, name, group);
				}
			}
			else if (config.defaultValueSound())
			{
				playValueSound(DEFAULT_SOUND_FILE, name, group);
			}
			return;
		}

		if (config.defaultValueSound() && value >= config.defaultStart() && value < config.defaultEnd())
		{
			playValueSound(DEFAULT_SOUND_FILE, name, group);
		}
		if (config.lowValueSound() && value >= config.lowStart() && value < config.lowEnd())
		{
			playValueSound(LOW_SOUND_FILE, name, group);
		}
		if (config.mediumValueSound() && value >= config.mediumStart() && value < config.mediumEnd())
		{
			playValueSound(MEDIUM_SOUND_FILE, name, group);
		}
		if (config.highValueSound() && value >= config.highStart() && value < config.highEnd())
		{
			playValueSound(HIGH_SOUND_FILE, name, group);
		}
		if (config.highestValueSound() && value >= config.highestStart())
		{
			playValueSound(HIGHEST_SOUND_FILE, name, group);
		}
	}

	boolean highlighted(String name, int quantity)
	{
		if (name == null)
		{
			return false;
		}
		int priority = GroundItemSoundFilter.match(highlightedItems, name, quantity);
		return priority > 0 && (config.dropValueMode() != DropValueMode.GROUND_ITEMS ||
			GroundItemSoundFilter.match(groundItemsConfig.getHiddenItems(), name, quantity) <= priority);
	}

	String highlightedRewardFile(int id, int quantity, PreviewTier rarity)
	{
		return eventFile(highlightedRewardEvent(id, quantity, rarity));
	}

	int highlightedRewardVolume(int id, int quantity, PreviewTier rarity)
	{
		String event = highlightedRewardEvent(id, quantity, rarity);
		boolean collectionTier = rarity != null && rarity.ordinal() >= eventTier(event).ordinal();
		return event.equals(HIGHLIGHTED_SOUND_FILE) || collectionTier || valueSoundEnabled(event) ? eventVolume(event) : 0;
	}

	private String highlightedRewardEvent(int id, int quantity, PreviewTier rarity)
	{
		if (config.highlightSound())
		{
			return HIGHLIGHTED_SOUND_FILE;
		}
		ItemComposition item = itemManager.getItemComposition(id);
		int ge = (int)Math.min(Integer.MAX_VALUE, (long)Math.max(0, itemManager.getItemPrice(id)) * quantity);
		int ha = item == null ? 0 : (int)Math.min(Integer.MAX_VALUE, (long)Math.max(0, item.getHaPrice()) * quantity);
		String valueEvent = highlightedValueFile(getValueByMode(ge, ha));
		if (rarity == null || rarity.ordinal() <= eventTier(valueEvent).ordinal())
		{
			return valueEvent;
		}
		switch (rarity)
		{
		case PET:
			return "pet_sound.wav";
		case VERY_RARE:
			return HIGHEST_SOUND_FILE;
		case RARE:
			return HIGH_SOUND_FILE;
		case UNCOMMON:
			return MEDIUM_SOUND_FILE;
		default:
			return DEFAULT_SOUND_FILE;
		}
	}

	private String highlightedValueFile(int value)
	{
		boolean ground = config.dropValueMode() == DropValueMode.GROUND_ITEMS;
		if (ground ? value > groundItemsConfig.insaneValuePrice() : value >= config.highestStart())
		{
			return HIGHEST_SOUND_FILE;
		}
		if (ground ? value > groundItemsConfig.highValuePrice() : value >= config.highStart() && value < config.highEnd())
		{
			return HIGH_SOUND_FILE;
		}
		if (ground ? value > groundItemsConfig.mediumValuePrice() : value >= config.mediumStart() && value < config.mediumEnd())
		{
			return MEDIUM_SOUND_FILE;
		}
		return DEFAULT_SOUND_FILE;
	}

	private boolean valueSoundEnabled(String file)
	{
		return file.equals(HIGHEST_SOUND_FILE) ? config.highestValueSound() :
			file.equals(HIGH_SOUND_FILE) ? config.highValueSound() :
			file.equals(MEDIUM_SOUND_FILE) ? config.mediumValueSound() : true;
	}

	private void playValueSound(String f, String itemName, long group)
	{
		soundQueue.offerValue(eventFile(f), eventVolume(f), itemName, group);
	}
	private void playSound(String f)
	{
		soundQueue.offer(eventFile(f), eventVolume(f), 0, true);
	}

	private PreviewTier eventTier(String file)
	{
		switch (file)
		{
		case "pet_sound.wav":
			return PreviewTier.PET;
		case "highest_sound.wav":
		case "elite_clue_sound.wav":
		case "master_clue_sound.wav":
			return PreviewTier.VERY_RARE;
		case "high_sound.wav":
		case "highlighted_sound.wav":
		case "hard_clue_sound.wav":
			return PreviewTier.RARE;
		case "medium_sound.wav":
		case "medium_clue_sound.wav":
			return PreviewTier.UNCOMMON;
		default:
			return PreviewTier.COMMON;
		}
	}

	private String eventFile(String file)
	{
		if (file.equals(HIGHLIGHTED_SOUND_FILE) && config.highlightedFile() != null && !config.highlightedFile().isBlank())
		{
			return config.highlightedFile();
		}
		return TierStyle.file(eventTier(file), config);
	}

	private int eventVolume(String file)
	{
		if (file.equals(HIGHLIGHTED_SOUND_FILE) && config.highlightedFile() != null && !config.highlightedFile().isBlank())
		{
			return Math.max(0, Math.min(100, config.highlightedVolume())) * Math.max(0, Math.min(100, config.masterVolume())) / 100;
		}
		return TierStyle.volume(eventTier(file), config);
	}

	private int getValueByMode(int gePrice, int haPrice)
	{
		DropValueMode mode = config.dropValueMode();
		if (mode == DropValueMode.GE)
		{
			return gePrice;
		}
		if (mode == DropValueMode.HIGH_ALCH)
		{
			return haPrice;
		}
		if (mode == DropValueMode.HIGHEST)
		{
			return Math.max(gePrice, haPrice);
		}
		if (groundItemsConfig.valueCalculationMode() == null)
		{
			return Math.max(gePrice, haPrice);
		}
		switch (groundItemsConfig.valueCalculationMode())
		{
		case GE:
			return gePrice;
		case HA:
			return haPrice;
		default: // Highest
			return Math.max(gePrice, haPrice);
		}
	}

	private void updateHighlightedItemsList()
	{
		highlightedItems = groundItemsConfig.getHighlightItems();
	}
}
