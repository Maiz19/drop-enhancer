/* Copyright (c) 2026 maiz; incorporates Custom Sounds configuration,
 * copyright (c) 2023 daanbom. BSD-2-Clause; see licenses/custom-sounds.txt. */
package com.collectioncelebrations;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("collection-celebrations")
public interface CelebrationConfig extends Config
{
	@ConfigSection(name = "Collection & timing", description = "New unlocks, repeats, reward audio and case delays", position = 0,
				   closedByDefault = true)
	String celebrations = "celebrations";

	@ConfigItem(keyName = "hideCoxRewards", name = "Hide CoX rewards",
				description = "Chambers of Xeric announces a unique before the team reaches the chest. Censor that chat line and "
							  + "hold the popup and its sound until the reward chest is opened, so the walk back still reveals it. "
							  + "Other raids send their collection log only after the chest, so they are unaffected.",
				section = celebrations, position = 3)
	default boolean hideCoxRewards()
	{
		return true;
	}

	@ConfigSection(name = "Popup & effects", description = "Position, animation effects, text, colours and statistics", position = 1,
				   closedByDefault = true)
	String appearance = "appearance";

	@ConfigSection(name = "Rarity & collection sounds", description = "Rarity basis and each tier colour, threshold, WAV and volume",
				   position = 2, closedByDefault = true)
	String rarity = "rarity";

	@ConfigSection(name = "Drop sounds by value", description = "Five GP bands for other item drops", position = 4, closedByDefault = true)
	String valueDrops = "valueDrops";

	@ConfigSection(name = "Clue scroll drops", description = "Sounds when a clue scroll drops", position = 5, closedByDefault = true)
	String clueDrops = "clueDrops";

	@ConfigSection(name = "Test popup", description = "Synthetic new unlock and repeat examples", position = 6, closedByDefault = true)
	String preview = "preview";

	@ConfigItem(keyName = "refreshWikiData", name = "Refresh wiki data",
				description = "Fetch item lists, completion percentages and drop rates from the OSRS Wiki. Refreshed weekly, after the "
							  + "Wednesday game update; switch off and on to update now. Switch off to stop all wiki requests. Sends no "
							  + "player data. Wiki data: CC BY-NC-SA 3.0, OSRS Wiki contributors.",
				warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
				section = rarity, position = -2)
	default boolean refreshWikiData()
	{
		return true;
	}

	@ConfigItem(keyName = "showDropRate", name = "Show extra drop rate",
				description = "Show the source-specific drop rate above the item, keeping all four selected statistics. Items with no "
							  + "rate, including guaranteed drops, leave it out.",
				section = appearance, position = 17)
	default boolean showDropRate()
	{
		return true;
	}

	@ConfigItem(keyName = "previewKind", name = "Test notification type",
				description = "New unlock adds a gold effect around the rarity frame; repeat keeps the rarity frame", section = preview,
				position = 1)
	default PreviewKind previewKind()
	{
		return PreviewKind.NEW_UNLOCK;
	}

	@ConfigItem(
		keyName = "previewSelection", name = "Preview tier",
		description =
			"Changing tier or notification type queues a complete test without cutting off the current one. Off stops previews.",
		section = preview, position = 0)
	default PreviewSelection previewSelection()
	{
		return PreviewSelection.OFF;
	}

	@ConfigItem(keyName = "showPopups", name = "Show popups", description = "Show new slots and repeat drops", section = celebrations,
				position = 0)
	default boolean showPopups()
	{
		return true;
	}
	@ConfigItem(keyName = "repeatDrops", name = "Repeat collection items",
				description = "Recognize repeat items from your manually synchronized collection log", section = celebrations, position = 1)
	default boolean repeatDrops()
	{
		return true;
	}
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "displaySeconds", name = "Popup duration", description = "Seconds visible after release", section = appearance,
				position = 3)
	default int displaySeconds()
	{
		return 4;
	}
	@ConfigItem(keyName = "accent", name = "Accent", description = "Popup accent colour", section = appearance, position = 23)
	default Color accent()
	{
		return new Color(205, 164, 82);
	}
	@ConfigItem(keyName = "collectionAudio", name = "Collection audio",
				description = "Play the configured shared tier sound when the popup is released", section = celebrations, position = 2)
	default boolean collectionAudio()
	{
		return true;
	}

	@Range(max = 100)
	@ConfigItem(keyName = "masterVolume", name = "Master Volume", description = "Master volume for collection and drop sounds",
				position = 4, section = celebrations)
	default int masterVolume()
	{
		return 50;
	}

	@ConfigItem(keyName = "highlightedItemSound", section = valueDrops, name = "Highlighted item sound",
				description = "Play at least Common for highlighted drops; scale up with value and collection rarity.", position = 28)
	default boolean highlightedItemSound()
	{
		return true;
	}

	@ConfigItem(keyName = "highlightPopup", section = valueDrops, name = "Highlight popup",
				description = "Show popups for highlighted drops, including non-collection items. Overrides repeat switches; exclusions and Show popups still apply.", position = 29)
	default boolean highlightPopup()
	{
		return false;
	}

	@ConfigItem(keyName = "highlightSound", section = valueDrops, name = "Custom highlighted sound",
				description = "Override value-tier audio for highlighted items. Off uses at least Common, scaling up with value and collection rarity.",
				position = 30)
	default boolean highlightSound()
	{
		return false;
	}

	@ConfigItem(keyName = "highlightedFile", name = "Highlighted item WAV",
				description = "Optional custom sound for highlighted items. Blank uses High / Rare.",
				section = valueDrops, position = 31)
	default String highlightedFile()
	{
		return "";
	}
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "highlightedVolume", name = "Highlighted item volume (%)",
				description = "Volume for a custom highlighted-item WAV, multiplied by master volume. Blank WAV uses the Rare tier volume.",
				section = valueDrops, position = 32)
	default int highlightedVolume()
	{
		return 100;
	}

	@ConfigItem(keyName = "beginnerClueSound", name = "Beginner Clue Sound",
				description = "Configure whether or not to play a sound when a beginner clue item appears", position = 0,
				section = clueDrops)

	default boolean beginnerClueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "easyClueSound", name = "Easy Clue Sound",
				description = "Configure whether or not to play a sound when a easy clue item appears", position = 3, section = clueDrops)
	default boolean easyClueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "mediumClueSound", name = "Medium Clue Sound",
				description = "Configure whether or not to play a sound when a medium clue item appears", position = 6, section = clueDrops)
	default boolean mediumClueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "hardClueSound", name = "Hard Clue Sound",
				description = "Configure whether or not to play a sound when a hard clue item appears", position = 9, section = clueDrops)
	default boolean hardClueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "eliteClueSound", name = "Elite Clue Sound",
				description = "Configure whether or not to play a sound when a elite clue item appears", position = 12, section = clueDrops)
	default boolean eliteClueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "masterClueSound", name = "Master Clue Sound",
				description = "Configure whether or not to play a sound when a master clue item appears", position = 15,
				section = clueDrops)
	default boolean masterClueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "defaultValueSound", name = "Default Sound",
				description = "Configure whether or not to play a sound when a default-tier item appears", position = 1,
				section = valueDrops)
	default boolean defaultValueSound()
	{
		return false;
	}

	@ConfigItem(keyName = "defaultStart", name = "Default starting GP (own mode)",
				description = "Inclusive start in independent price modes. Ignored when following Ground Items.", position = 2,
				section = valueDrops)
	default int defaultStart()
	{
		return 0;
	}
	@ConfigItem(keyName = "defaultEnd", name = "Default ending GP (own mode)",
				description = "Exclusive end in independent price modes. Ignored when following Ground Items.", position = 3,
				section = valueDrops)
	default int defaultEnd()
	{
		return 20000;
	}

	@ConfigItem(keyName = "lowValueSound", name = "Low Value Sound",
				description = "Configure whether or not to play a sound when a low valued item appears", position = 6, section = valueDrops)
	default boolean lowValueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "lowStart", name = "Low Value starting GP (own mode)",
				description = "Inclusive start in independent price modes. Ignored when following Ground Items.", position = 7,
				section = valueDrops)
	default int lowStart()
	{
		return 20000;
	}
	@ConfigItem(keyName = "lowEnd", name = "Low Value ending GP (own mode)",
				description = "Exclusive end in independent price modes. Ignored when following Ground Items.", position = 8,
				section = valueDrops)
	default int lowEnd()
	{
		return 100000;
	}

	@ConfigItem(keyName = "mediumValueSound", name = "Medium Value Sound",
				description = "Configure whether or not to play a sound when a medium valued item appears", position = 11,
				section = valueDrops)
	default boolean mediumValueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "mediumStart", name = "Medium Value starting GP (own mode)",
				description = "Inclusive start in independent price modes. Ignored when following Ground Items.", position = 12,
				section = valueDrops)
	default int mediumStart()
	{
		return 100000;
	}
	@ConfigItem(keyName = "mediumEnd", name = "Medium Value ending GP (own mode)",
				description = "Exclusive end in independent price modes. Ignored when following Ground Items.", position = 13,
				section = valueDrops)
	default int mediumEnd()
	{
		return 1000000;
	}

	@ConfigItem(keyName = "highValueSound", name = "High Value Sound",
				description = "Configure whether or not to play a sound when a high valued item appears", position = 16,
				section = valueDrops)
	default boolean highValueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "highStart", name = "High Value starting GP (own mode)",
				description = "Inclusive start in independent price modes. Ignored when following Ground Items.", position = 17,
				section = valueDrops)
	default int highStart()
	{
		return 1000000;
	}
	@ConfigItem(keyName = "highEnd", name = "High Value ending GP (own mode)",
				description = "Exclusive end in independent price modes. Ignored when following Ground Items.", position = 18,
				section = valueDrops)
	default int highEnd()
	{
		return 10000000;
	}

	@ConfigItem(keyName = "highestValueSound", name = "Highest Value Sound",
				description = "Configure whether or not to play a sound when a highest valued item appears", position = 21,
				section = valueDrops)
	default boolean highestValueSound()
	{
		return true;
	}

	@ConfigItem(keyName = "highestStart", name = "Highest Value starting GP (own mode)",
				description = "Inclusive start with no upper limit in independent price modes. Ignored when following Ground Items.", position = 22,
				section = valueDrops)
	default int highestStart()
	{
		return 10000000;
	}

	@Range(min = 60, max = 200)
	@ConfigItem(keyName = "popupScale", name = "Popup scale (%)",
				description = "Scale vector panel and text without enlarging a low-resolution panel bitmap", section = appearance,
				position = 2)
	default int popupScale()
	{
		return 75;
	}

	@ConfigItem(keyName = "showItemIcon", name = "Show item image", description = "Show the RuneLite item sprite", section = appearance,
				position = 7)
	default boolean showItemIcon()
	{
		return true;
	}

	@ConfigItem(keyName = "showKc", name = "Show kill count", description = "Show source-matched KC when available", section = appearance,
				position = 14)
	default boolean showKc()
	{
		return true;
	}

	@ConfigItem(keyName = "showValue", name = "Show item value", description = "Show value using the selected price basis",
				section = appearance, position = 15)
	default boolean showValue()
	{
		return true;
	}

	@ConfigItem(keyName = "showTotal", name = "Show collection count",
				description = "Show official or clearly labelled temporary collection quantity", section = appearance, position = 13)
	default boolean showTotal()
	{
		return true;
	}

	@ConfigItem(keyName = "valueMode", name = "Value basis", description = "Grand Exchange, high alchemy or highest of both",
				section = rarity, position = 2)
	default ValueMode valueMode()
	{
		return ValueMode.GE;
	}

	@Range(min = 0, max = 2147483647)
	@ConfigItem(keyName = "uncommonAt", name = "Own Uncommon GP (link off)",
				description = "Inactive when Link to Ground Items values is enabled. This saved cutoff is used only when the link is off.",
				section = rarity, position = 12)
	default int uncommonAt()
	{
		return 100000;
	}

	@Range(min = 0, max = 2147483647)
	@ConfigItem(keyName = "rareAt", name = "Own Rare GP (link off)",
				description = "Inactive when Link to Ground Items values is enabled. This saved cutoff is used only when the link is off.",
				section = rarity, position = 18)
	default int rareAt()
	{
		return 1000000;
	}

	@Range(min = 0, max = 2147483647)
	@ConfigItem(keyName = "veryRareAt", name = "Own Very rare GP (link off)",
				description = "Inactive when Link to Ground Items values is enabled. This saved cutoff is used only when the link is off.",
				section = rarity, position = 24)
	default int veryRareAt()
	{
		return 10000000;
	}

	@ConfigItem(keyName = "colourCommon", name = "Common colour", description = "Custom colour when Ground Items colours is disabled",
				section = rarity, position = 8)
	default Color colourCommon()
	{
		return new Color(0x66B2FF);
	}

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "volumeCommon", name = "Low / Common volume (%)",
				description = "Multiplied by master volume; zero mutes this tier", section = rarity, position = 11)
	default int volumeCommon()
	{
		return 100;
	}

	@ConfigItem(keyName = "fileCommon", name = "Low / Common WAV",
				description = "Shared by collection and drop sounds at this tier. Bundled or local WAV filename; blank means silent", section = rarity,
				position = 10)
	default String fileCommon()
	{
		return "custom-sounds-common.wav";
	}

	@ConfigItem(keyName = "colourUncommon", name = "Uncommon colour", description = "Custom colour when Ground Items colours is disabled",
				section = rarity, position = 14)
	default Color colourUncommon()
	{
		return new Color(0x99FF99);
	}

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "volumeUncommon", name = "Medium / Uncommon volume (%)",
				description = "Multiplied by master volume; zero mutes this tier", section = rarity, position = 17)
	default int volumeUncommon()
	{
		return 100;
	}

	@ConfigItem(keyName = "fileUncommon", name = "Medium / Uncommon WAV",
				description = "Shared by collection and drop sounds at this tier. Bundled or local WAV filename; blank means silent", section = rarity,
				position = 16)
	default String fileUncommon()
	{
		return "drop-enhancer-uncommon.wav";
	}

	@ConfigItem(keyName = "colourRare", name = "Rare colour", description = "Custom colour when Ground Items colours is disabled",
				section = rarity, position = 20)
	default Color colourRare()
	{
		return new Color(0xFF9600);
	}

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "volumeRare", name = "High / Rare volume (%)", description = "Multiplied by master volume; zero mutes this tier",
				section = rarity, position = 23)
	default int volumeRare()
	{
		return 100;
	}

	@ConfigItem(keyName = "fileRare", name = "High / Rare WAV",
				description = "Shared by collection and drop sounds at this tier. Bundled or local WAV filename; blank means silent", section = rarity,
				position = 22)
	default String fileRare()
	{
		return "custom-sounds-rare.wav";
	}

	@ConfigItem(keyName = "colourVeryRare", name = "VeryRare colour", description = "Custom colour when Ground Items colours is disabled",
				section = rarity, position = 26)
	default Color colourVeryRare()
	{
		return new Color(0xFF66B2);
	}

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "volumeVeryRare", name = "Highest / Very rare volume (%)",
				description = "Multiplied by master volume; zero mutes this tier", section = rarity, position = 29)
	default int volumeVeryRare()
	{
		return 100;
	}

	@ConfigItem(keyName = "fileVeryRare", name = "Highest / Very rare WAV",
				description = "Shared by collection and drop sounds at this tier. Bundled or local WAV filename; blank means silent", section = rarity,
				position = 28)
	default String fileVeryRare()
	{
		return "custom-sounds-veryrare.wav";
	}

	@ConfigItem(keyName = "colourPet", name = "Pet colour", description = "Custom colour when Ground Items colours is disabled",
				section = rarity, position = 31)
	default Color colourPet()
	{
		return new Color(0x64FFE0);
	}

	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "volumePet", name = "Pet volume (%)", description = "Multiplied by master volume; zero mutes this tier",
				section = rarity, position = 34)
	default int volumePet()
	{
		return 100;
	}

	@ConfigItem(keyName = "filePet", name = "Pet WAV file",
				description = "Shared by collection and drop sounds at this tier. Bundled or local WAV filename; blank means silent", section = rarity,
				position = 33)
	default String filePet()
	{
		return "drop-enhancer-pet.wav";
	}

	@ConfigItem(
		keyName = "dropValueMode", name = "Drop sound values",
		description = "Follow Ground Items uses its price basis, low/medium/high/insane thresholds and hidden-item rules. Default is "
					  + "below Low and disabled by default. Other modes use your own ranges. Collection audio remains independent.",
		section = valueDrops, position = 0)
	default DropValueMode dropValueMode()
	{
		return DropValueMode.GROUND_ITEMS;
	}

	@ConfigItem(keyName = "rarityBasis", name = "Rarity based on",
				description = "Combination blends wiki completion and value 60/40, with the value tier as a minimum. Untradeable items use rarity only.", section = rarity,
				position = 1)
	default RarityBasis rarityBasis()
	{
		return RarityBasis.COMBINATION;
	}
	@ConfigItem(keyName = "showWiki", name = "Show wiki completion",
				description = "Population completion percentage; not your personal log progress", section = appearance, position = 16)
	default boolean showWiki()
	{
		return true;
	}

	@ConfigItem(keyName = "bulkUnlockSfx", name = "One sound per reward",
				description = "Play one collection sound for multiple items from the same loot event", section = rarity, position = 3)
	default boolean bulkUnlockSfx()
	{
		return false;
	}

	@ConfigItem(keyName = "stat1", name = "Top left statistic",
				description = "Statistic shown in position 1; unknown drop rates fall back to value", section = appearance, position = 9)
	default PopupStat stat1()
	{
		return PopupStat.COLLECTION_COUNT;
	}

	@ConfigItem(keyName = "stat2", name = "Top right statistic",
				description = "Statistic shown in position 2; unknown drop rates fall back to value", section = appearance, position = 10)
	default PopupStat stat2()
	{
		return PopupStat.KILL_COUNT;
	}

	@ConfigItem(keyName = "stat3", name = "Bottom left statistic",
				description = "Statistic shown in position 3; unknown drop rates fall back to value", section = appearance, position = 11)
	default PopupStat stat3()
	{
		return PopupStat.VALUE;
	}

	@ConfigItem(keyName = "stat4", name = "Bottom right statistic",
				description = "Statistic shown in position 4; unknown drop rates fall back to value", section = appearance, position = 12)
	default PopupStat stat4()
	{
		return PopupStat.WIKI_COMPLETION;
	}

	@ConfigItem(keyName = "textOutline", name = "Text outline",
				description = "Dark edge behind the popup text. The popup is always drawn on whole pixels in the RuneScape font, "
							  + "so it stays sharp in stretched mode.",
				section = appearance, position = 6)
	default TextOutline textOutline()
	{
		return TextOutline.SHADOW;
	}
	@Range(min = 10, max = 60)
	@ConfigItem(keyName = "backgroundDarkness", name = "Background brightness (%)",
				description = "Brightness of the tier-tinted background", section = appearance, position = 18)
	default int backgroundDarkness()
	{
		return 18;
	}
	@ConfigItem(keyName = "colourCaption", name = "Caption text", description = "Collection log caption colour", section = appearance,
				position = 19)
	default Color colourCaption()
	{
		return new Color(0xE7BE66);
	}
	@ConfigItem(keyName = "colourStatLabel", name = "Statistic labels", description = "Colour of statistic labels", section = appearance,
				position = 21)
	default Color colourStatLabel()
	{
		return new Color(0xB7BEC9);
	}
	@ConfigItem(keyName = "colourStatValue", name = "Statistic values", description = "Colour of statistic values", section = appearance,
				position = 22)
	default Color colourStatValue()
	{
		return new Color(0xF3E7CE);
	}
	@ConfigItem(keyName = "colourItemName", name = "Item name", description = "Colour of the item name", section = appearance,
				position = 20)
	default Color colourItemName()
	{
		return new Color(0xF5F3ED);
	}

	@ConfigItem(keyName = "soundEnabledCommon", name = "Low / Common audio",
				description = "Shared mute for collection and drop sounds at this tier", section = rarity, position = 9)
	default boolean soundEnabledCommon()
	{
		return true;
	}

	@ConfigItem(keyName = "soundEnabledUncommon", name = "Medium / Uncommon audio",
				description = "Shared mute for collection and drop sounds at this tier", section = rarity, position = 15)
	default boolean soundEnabledUncommon()
	{
		return true;
	}

	@ConfigItem(keyName = "soundEnabledRare", name = "High / Rare audio",
				description = "Shared mute for collection and drop sounds at this tier", section = rarity, position = 21)
	default boolean soundEnabledRare()
	{
		return true;
	}

	@ConfigItem(keyName = "soundEnabledVeryRare", name = "Highest / Very rare audio",
				description = "Shared mute for collection and drop sounds at this tier", section = rarity, position = 27)
	default boolean soundEnabledVeryRare()
	{
		return true;
	}

	@ConfigItem(keyName = "soundEnabledPet", name = "Pet audio enabled",
				description = "Shared mute for collection and drop sounds at this tier", section = rarity, position = 32)
	default boolean soundEnabledPet()
	{
		return true;
	}

	@ConfigItem(keyName = "tierEffects", name = "High-tier effects",
				description = "Rare: orbiting sparks. Very rare and pet: additional rays and particles in the rarity colour",
				section = appearance, position = 4)
	default boolean tierEffects()
	{
		return true;
	}
	@ConfigItem(
		keyName = "unlockEffects", name = "New unlock gold effect",
		description = "Gold banner, pulsing illumination and starburst for a new collection log slot; rarity border remains unchanged",
		section = appearance, position = 5)
	default boolean unlockEffects()
	{
		return true;
	}
	@ConfigItem(
		keyName = "repeatCommon", name = "Common repeat logs",
		description = "Show repeat collection drops and play collection audio for this rarity; new unlocks and explicit tests still show",
		section = rarity, position = 7)
	default boolean repeatCommon()
	{
		return false;
	}

	@ConfigItem(
		keyName = "repeatUncommon", name = "Uncommon repeat logs",
		description = "Show repeat collection drops and play collection audio for this rarity; new unlocks and explicit tests still show",
		section = rarity, position = 13)
	default boolean repeatUncommon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "repeatRare", name = "Rare repeat logs",
		description = "Show repeat collection drops and play collection audio for this rarity; new unlocks and explicit tests still show",
		section = rarity, position = 19)
	default boolean repeatRare()
	{
		return true;
	}

	@ConfigItem(
		keyName = "repeatVeryRare", name = "Very rare repeat logs",
		description = "Show repeat collection drops and play collection audio for this rarity; new unlocks and explicit tests still show",
		section = rarity, position = 25)
	default boolean repeatVeryRare()
	{
		return true;
	}

	@ConfigItem(
		keyName = "repeatPet", name = "Pet repeat logs",
		description = "Show repeat collection drops and play collection audio for this rarity; new unlocks and explicit tests still show",
		section = rarity, position = 30)
	default boolean repeatPet()
	{
		return true;
	}

	@ConfigItem(keyName = "includedPopupItems", name = "Included popup items",
		description = "Comma-separated names, * wildcards and stack quantity rules. Includes extra items and overrides global/per-tier repeat switches. Exclusions take priority; popup and audio master settings still apply.",
		section = rarity, position = 36)
	default String includedPopupItems()
	{
		return "";
	}

	@ConfigItem(keyName = "excludedPopupItems", name = "Excluded popup items",
		description = "Comma-separated names; supports * wildcards and quantities, e.g. Mystic *, Rune arrow < 100. Suppresses new/repeat popups and their collection audio. Tests still show. Shift-right-click ground items to add/remove exact names.",
		section = rarity, position = 35)
	default String excludedPopupItems()
	{
		return "";
	}

	@ConfigItem(keyName = "unlockFile", name = "New log jingle WAV",
				description = "Extra short sound only for a new unlock, on top of tier audio; blank disables the jingle", section = rarity,
				position = 5)
	default String unlockFile()
	{
		return "custom-sounds-unlock.wav";
	}
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "unlockVolume", name = "New log jingle volume",
				description = "Relative to master volume, capped at 35% of tier volume to keep drop audio prominent; zero disables the jingle", section = rarity, position = 6)
	default int unlockVolume()
	{
		return 80;
	}
	@ConfigItem(keyName = "followGroundItemColours", name = "Ground Items colours",
				description = "Common uses low, Uncommon medium, Rare high, Very rare insane; pets use a separate turquoise colour. "
							  + "Disable to use the custom tier colours below.",
				section = rarity, position = 0)
	default boolean followGroundItemColours()
	{
		return true;
	}
	@ConfigItem(keyName = "groundItemThresholds", name = "Link to Ground Items values",
				description = "Use Ground Items medium/high/insane prices for Uncommon/Rare/Very rare. Value mode uses these directly; "
							  + "Combination can raise the value tier, never lower it. Price remains per-item GE with alch fallback.",
				section = rarity, position = -1)
	default boolean groundItemThresholds()
	{
		return true;
	}
}
