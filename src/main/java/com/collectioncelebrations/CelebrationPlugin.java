/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE.
 * Collection unlock matching and native popup paint hiding adapted from SnakeSteak's
 * Collection Log Popup Enhanced; see licenses/enhanced.txt. */
package com.collectioncelebrations;

import com.google.inject.Provides;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.ScriptID;
import net.runelite.api.MenuAction;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.grounditems.GroundItemsConfig;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.Text;
import net.runelite.api.widgets.Widget;
import net.runelite.http.api.loottracker.LootRecordType;

@PluginDescriptor(name = "Drop Enhancer", description = "Collection log, repeat drop popups and custom sounds",
				  conflicts = {"Collection Log Popup Enhanced", "Custom Sounds"}, internalName = "drop-enhancer",
				  // Users' own sounds and the wiki caches, moved into the plugin directory once.
				  legacyDataDirectory = "collection-celebrations")
public class CelebrationPlugin extends Plugin
{
	// collection_delayed_transmit; no named constant is exposed by RuneLite ScriptID.
	private static final int COLLECTION_DELAYED_TRANSMIT = 4100;
	private static final String UNLOCK = "New item added to your collection log: ";
	private static final Set<ChatMessageType> ANNOUNCEMENTS =
		Set.of(ChatMessageType.GAMEMESSAGE, ChatMessageType.SPAM, ChatMessageType.CLAN_MESSAGE,
			   ChatMessageType.CLAN_GIM_MESSAGE, ChatMessageType.CLAN_GUEST_MESSAGE,
			   ChatMessageType.FRIENDSCHATNOTIFICATION);
	private static final int[] PAINT = {InterfaceID.NotificationDisplay.BACKGROUND, InterfaceID.NotificationDisplay.FRAME,
										InterfaceID.NotificationDisplay.TITLE,		InterfaceID.NotificationDisplay.TITLE_TEXT,
										InterfaceID.NotificationDisplay.MAIN,		InterfaceID.NotificationDisplay.MAIN_TEXT};
	@Inject
	Client client;
	@Inject
	ConfigManager configManager;
	@Inject
	ClientThread clientThread;
	private volatile boolean running;
	private final RevealQueue<Celebration> previews = new RevealQueue<>();
	@Inject
	EventBus events;
	@Inject
	OverlayManager overlays;
	@Inject
	CelebrationOverlay overlay;
	@Inject
	CelebrationConfig config;
	@Inject
	ItemManager items;
	@Inject
	CaseGate gate;
	@Inject
	CoxRewardGate cox;
	@Inject
	SoundQueue sounds;
	@Inject
	CustomSoundEvents custom;
	@Inject
	KillCountTracker kills;
	@Inject
	WikiRarity wiki;
	private final CollectionLedger ledger = new CollectionLedger();
	// Saved in the RuneLite profile, which RuneLite keeps per account and world type.
	private static final String COUNTS_KEY = "collectionCounts";
	// The profile whose counts the ledger holds; null until the next event loads them.
	private String countsProfile;
	private final RevealQueue<Celebration> pending = new RevealQueue<>();
	private final Map<String, Integer> names = new HashMap<>();
	// Binary membership from genuine unlocks; never an inferred lifetime quantity.
	private final Set<String> knownLoggedNames = new HashSet<>();
	private final Set<Widget> hiddenPaint = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<String, Celebration> recentLoot = new HashMap<>();
	private static final long REWARD_MATCH_MS = 5000;
	private long sequence;
	private boolean notificationStarted;
	private String replacedNotification;
	private String replacedItem;
	private final Set<String> notifiedUnlocks = new HashSet<>();
	private int lastKcTick = -1;
	private long lastAudioGroup = -1;
	private String lastLootSource;
	private int lastLootTick = -1;
	private long lastLootGroup = -1;
	long now()
	{
		return System.currentTimeMillis();
	}

	@Provides
	CelebrationConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(CelebrationConfig.class);
	}

	/**
	 * Ground Items' settings (hidden items, value tiers and colours), read as they are saved. Since RuneLite 1.13 a
	 * {@code @PluginDependency} must expose services, which Ground Items does not.
	 */
	@Provides
	GroundItemsConfig provideGroundItemsConfig(ConfigManager manager)
	{
		return manager.getConfig(GroundItemsConfig.class);
	}
	@Override
	protected void startUp() throws IOException
	{
		migrateTextRenderMode(configManager);
		migratePopupScale(configManager);
		Filepath folder = getPluginDirectory();
		wiki.dataDirectory = folder.joinSegment("data");
		sounds.soundDirectory = folder.joinSegment("sounds");
		reset();
		wiki.start();
		sounds.start();
		custom.startUp();
		events.register(custom);
		overlays.add(overlay);
		gate.refresh();
		running = true;
	}
	@Override
	protected void shutDown()
	{
		running = false;
		wiki.stop();
		events.unregister(custom);
		custom.shutDown();
		sounds.stop();
		overlays.remove(overlay);
		reset();
		restorePaint();
	}
	/**
	 * The old text mode chose both the smoothing and the outline. RuneLite saves the new settings' defaults before
	 * startUp, so while the old setting is still there, it decides both.
	 */
	static void migrateTextRenderMode(ConfigManager configManager)
	{
		String legacy = configManager.getConfiguration("collection-celebrations", "textRenderMode");
		if (legacy == null)
		{
			return;
		}
		configManager.setConfiguration("collection-celebrations", "textOutline",
			"SMOOTH_OUTLINED".equals(legacy) ? TextOutline.SHADOW : TextOutline.NONE);
		configManager.setConfiguration("collection-celebrations", "popupRendering",
			"CRISP".equals(legacy) ? PopupRendering.HARD : PopupRendering.SOFT);
		configManager.unsetConfiguration("collection-celebrations", "textRenderMode");
	}
	/**
	 * Popup scale (%) became Popup size (%), whose 100% is the old 64%: small, with the item pixel for pixel. RuneLite
	 * saves the new setting's default before startUp, so while the old setting is still there, it decides the size.
	 */
	static void migratePopupScale(ConfigManager configManager)
	{
		String legacy = configManager.getConfiguration("collection-celebrations", "popupScale");
		if (legacy == null)
		{
			return;
		}
		try
		{
			long size = Math.round(Integer.parseInt(legacy.trim()) / CelebrationOverlay.BASE_SCALE);
			configManager.setConfiguration("collection-celebrations", "popupSize", (int)Math.max(90, Math.min(250, size)));
		}
		catch (NumberFormatException ignored)
		{
			// Unreadable: the default size stays.
		}
		configManager.unsetConfiguration("collection-celebrations", "popupScale");
	}
	private void reset()
	{
		previews.clear();
		// Saved to the profile they came from, and loaded again on the next event after login or a hop.
		saveCounts();
		ledger.clear();
		countsProfile = null;
		names.clear();
		knownLoggedNames.clear();
		pending.clear();
		kills.reset();
		overlay.clear();
		sounds.reset();
		gate.reset();
		cox.reset();
		recentLoot.clear();
		sequence = 0;
		notificationStarted = false;
		replacedNotification = null;
		replacedItem = null;
		notifiedUnlocks.clear();
		lastKcTick = -1;
		lastAudioGroup = -1;
		lastLootSource = null;
		lastLootTick = -1;
		lastLootGroup = -1;
	}
	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING ||
			e.getGameState() == GameState.CONNECTION_LOST || e.getGameState() == GameState.LOGGING_IN)
		{
			reset();
			hiddenPaint.clear();
		}
	}
	@Subscribe
	public void onGameTick(GameTick e)
	{
		gate.refresh();
		cox.tick();
		recentLoot.values().removeIf(c -> now() - c.created > REWARD_MATCH_MS);
		// Once a tick at most: opening the log reads hundreds of items in one go.
		saveCounts();
	}

	/** Puts the logged-in account's own counts in the ledger, so none cross to another account or world type. */
	private void followProfile()
	{
		String profile = configManager.getRSProfileKey();
		if (Objects.equals(profile, countsProfile))
		{
			return;
		}
		saveCounts();
		ledger.load(profile == null ? null : configManager.getConfiguration("collection-celebrations", profile, COUNTS_KEY));
		countsProfile = profile;
	}

	private void saveCounts()
	{
		String counts = countsProfile == null ? null : ledger.changes();
		if (counts != null)
		{
			configManager.setConfiguration("collection-celebrations", countsProfile, COUNTS_KEY, counts);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() == InterfaceID.DOM_END_LEVEL_UI)
		{
			gate.noteDoomInterface();
		}
		// The chest is the intended reveal, but private storage and the bank mean the player has
		// moved on without it; leaving a unique hidden for the rest of the session helps nobody.
		if (e.getGroupId() == InterfaceID.RAIDS_REWARDS || e.getGroupId() == InterfaceID.RAIDS_STORAGE_PRIVATE
			|| e.getGroupId() == InterfaceID.BANKMAIN)
		{
			cox.noteChestOpened();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired e)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			if (e.getScriptId() == ScriptID.NOTIFICATION_START)
			{
				notificationStarted = true;
				replacedNotification = null;
				replacedItem = null;
				restorePaint();
			}
			else if (e.getScriptId() == ScriptID.NOTIFICATION_DELAY && notificationStarted)
			{
				notificationStarted = false;
				String title = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
				String main = client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN);
				String text = main == null ? "" : Text.removeTags(main).trim();
				if ("Collection log".equalsIgnoreCase(title) && text.startsWith("New item:"))
				{
					String name = text.substring("New item:".length()).trim();
					if (!name.isEmpty())
					{
						unlock(name);
						replacedNotification = main;
						replacedItem = name;
					}
				}
			}
		}
		if (e.getScriptId() != COLLECTION_DELAYED_TRANSMIT || client.getGameState() != GameState.LOGGED_IN ||
			client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1)
		{
			return;
		}
		Object[] args = e.getScriptEvent() == null ? null : e.getScriptEvent().getArguments();
		if (args == null || args.length < 3 || !(args[1] instanceof Integer) || !(args[2] instanceof Integer))
		{
			return;
		}
		int id = (Integer)args[1];
		int quantity = (Integer)args[2];
		if (id < 0 || quantity < 0)
		{
			return;
		}
		followProfile();
		ledger.observe(id, quantity);
		ItemComposition definition = items.getItemComposition(id);
		String name = definition == null ? null : definition.getName();
		if (name != null)
		{
			String key = name.toLowerCase(Locale.ROOT);
			names.put(key, id);
			if (quantity > 0)
			{
				knownLoggedNames.add(key);
			}
			else
			{
				knownLoggedNames.remove(key);
			}
		}
		for (Celebration c : pending.items())
		{
			if (c.itemId == id || c.name.equalsIgnoreCase(name))
			{
				c.itemId = id;
				c.confirmedTotal = quantity;
			}
		}
	}
	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		long revision = kills.revision();
		kills.onChatMessage(e);
		if (kills.revision() != revision)
		{
			lastKcTick = client.getTickCount();
			for (Celebration c : recentLoot.values())
			{
				attachKc(c);
			}
		}
		// A raid names its unique in the game chat, in the valuable drop line and to the clan or
		// friends chat when a team mate gets it. Only server-sent types, so nobody can fake one.
		if (ANNOUNCEMENTS.contains(e.getType()))
		{
			cox.hide(Text.removeTags(e.getMessage()), e.getMessageNode());
		}
		if (e.getType() != ChatMessageType.GAMEMESSAGE && e.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String message = Text.removeTags(e.getMessage());
		if (!message.startsWith(UNLOCK))
		{
			return;
		}
		unlock(message.substring(UNLOCK.length()).trim());
	}

	private void unlock(String name)
	{
		// Armed before the repeat guard: a second notification for the same slot must still hold.
		cox.arm(name);
		if (name.isEmpty() || !notifiedUnlocks.add(name.toLowerCase(Locale.ROOT)))
		{
			return;
		}
		knownLoggedNames.add(name.toLowerCase(Locale.ROOT));
		sounds.cancelValue(name);
		long now = now();
		for (Celebration c : pending.items())
		{
			if (c.name.equalsIgnoreCase(name) && now - c.created < REWARD_MATCH_MS)
			{
				c.newSlot = true;
				c.extraItem = false;
				countFirstCopies(c);
				return;
			}
		}
		Celebration recent = recentLoot.get(name.toLowerCase(Locale.ROOT));
		if (recent != null && now - recent.created < REWARD_MATCH_MS)
		{
			if (!recent.newSlot)
			{
				recent.newSlot = true;
				recent.extraItem = false;
				countFirstCopies(recent);
				if (!recent.presented)
				{
					pending.add(recent);
				}
				else
				{
					recent.upgradeOnly = true;
					// Promote the existing reward without restarting an already finished popup.
					queueUnlockJingle(recent);
				}
			}
			return;
		}
		int id = names.getOrDefault(name.toLowerCase(Locale.ROOT), -1);
		Celebration unlocked = new Celebration(name, id, 1, null, true, ++sequence, now, gate.delayMillis());
		unlocked.unlockTick = client.getTickCount();
		pending.add(unlocked);
		// Keep the unlock available for a late loot event even after its popup has started.
		recentLoot.put(name.toLowerCase(Locale.ROOT), unlocked);
	}

	/** A new slot means the log held none before this drop, whatever an older reading said. */
	private void countFirstCopies(Celebration c)
	{
		if (c.lootTick >= 0 && c.itemId >= 0)
		{
			followProfile();
			ledger.observe(c.itemId, 0);
			c.countedTotal = ledger.add(c.itemId, c.dropQuantity);
		}
	}
	@Subscribe
	public void onLootReceived(LootReceived e)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		// LootTracker emits this once for its NPC/chest/activity records. Do not additionally
		// subscribe to NpcLootReceived and double-handle the same reward.
		gate.noteReward(e.getName());
		followProfile();

		long now = now();
		long rewardSequence = ++sequence;
		// A chest can arrive as several loot events on one tick; they still share one sound.
		int tick = client.getTickCount();
		boolean sameReward = tick == lastLootTick && e.getName() != null && e.getName().equals(lastLootSource);
		long audioGroup = sameReward ? lastLootGroup : rewardSequence;
		lastLootSource = e.getName();
		lastLootTick = tick;
		lastLootGroup = audioGroup;
		Map<Integer, Integer> stacks = new LinkedHashMap<>();
		for (ItemStack stack : e.getItems())
		{
			int id = items.canonicalize(stack.getId());
			if (stack.getQuantity() > 0)
			{
				stacks.merge(id, stack.getQuantity(), (a, b) -> (int)Math.min(Integer.MAX_VALUE, (long)a + b));
			}
		}
		custom.onLootReceived(e, rawId -> {
			int id = items.canonicalize(rawId);
			ItemComposition item = items.getItemComposition(id);
			String name = item == null ? "" : item.getName();
			return ledger.get(id) != null || (name != null && knownLoggedNames.contains(name.toLowerCase(Locale.ROOT))) ||
				wiki.entry(id, name) != null || popupIncluded(name, stacks.getOrDefault(id, 0));
		}, audioGroup);
		for (Map.Entry<Integer, Integer> stack : stacks.entrySet())
		{
			int id = stack.getKey();
			ItemComposition composition = items.getItemComposition(id);
			if (composition == null)
			{
				continue;
			}
			String name = composition.getName();
			if (name == null)
			{
				continue;
			}
			id = names.getOrDefault(name.toLowerCase(Locale.ROOT), id);
			names.putIfAbsent(name.toLowerCase(Locale.ROOT), id);
			Celebration target = null;
			for (Celebration c : pending.items())
			{
				if (c.newSlot && c.lootTick < 0 && c.name.equalsIgnoreCase(name))
				{
					target = c;
					break;
				}
			}
			if (target == null)
			{
				Celebration recent = recentLoot.get(name.toLowerCase(Locale.ROOT));
				if (recent != null && recent.newSlot && recent.lootTick < 0 && now - recent.created < REWARD_MATCH_MS)
				{
					// Attaching the source consumes this match; another real drop gets its own popup.
					target = recent;
				}
			}
			if (target == null)
			{
				target = new Celebration(name, id, stack.getValue(), e.getName(), false, rewardSequence, now, gate.delayMillis());
				boolean knownOwned = ledger.obtained(id) || knownLoggedNames.contains(name.toLowerCase(Locale.ROOT));
				boolean collectionType = ledger.get(id) != null || wiki.entry(id, name) != null;
				target.extraItem = !knownOwned && !collectionType && ledger.get(id) == null;
				if (popupIncluded(name, stack.getValue()) || ((knownOwned || collectionType) &&
					(config.repeatDrops() || (config.highlightedItemSound() && custom.highlighted(name, stack.getValue())))))
				{
					// A received collection-item drop can be presented before personal quantities sync.
					// Ownership and exact totals still require authoritative collection-log data.
					pending.add(target);
				}
			}
			if (target != null)
			{
				recentLoot.put(name.toLowerCase(Locale.ROOT), target);
				target.audioGroup = audioGroup;
				target.lootTick = tick;
				target.itemId = id;
				target.source = e.getName();
				target.dropQuantity = stack.getValue();
				if (target.newSlot)
				{
					// The unlock came first: the log held none before this drop.
					ledger.observe(id, 0);
				}
				// Loot from another player never enters the collection log.
				target.countedTotal = e.getType() == LootRecordType.PLAYER ? ledger.get(id) : ledger.add(id, stack.getValue());
				attachKc(target);
				if (target.presented)
				{
					// Late source/item details update the visible popup without replaying it or its sound.
					preparePresentation(target);
					if (target.audible)
					{
						sounds.cancelValueGroup(audioGroup);
					}
				}
			}
		}
	}
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (MenuAction.of(event.getType()) != MenuAction.EXAMINE_ITEM_GROUND || !client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			return;
		}
		ItemComposition item = items.getItemComposition(items.canonicalize(event.getIdentifier()));
		if (item == null || item.getName() == null)
		{
			return;
		}
		String name = item.getName();
		Menu submenu = client.getMenu().createMenuEntry(-1)
			.setOption("Drop Enhancer")
			.setTarget(event.getTarget())
			.setType(MenuAction.RUNELITE)
			.createSubMenu();
		boolean included = popupItems(config.includedPopupItems()).stream().anyMatch(name::equalsIgnoreCase);
		boolean excluded = popupItems(config.excludedPopupItems()).stream().anyMatch(name::equalsIgnoreCase);
		if (!excluded)
		{
			submenu.createMenuEntry(-1)
				.setOption(included ? "Remove popup inclusion" : "Include in popups")
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(entry -> setPopupIncluded(name, !included));
		}
		if (!included || excluded)
		{
			submenu.createMenuEntry(-1)
				.setOption(excluded ? "Remove popup exclusion" : "Exclude from popups")
				.setTarget(event.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(entry -> {
					// Older profiles may contain the exact name in both lists.
					if (included && excluded)
					{
						setPopupIncluded(name, false);
					}
					setPopupExcluded(name, !excluded);
				});
		}
	}

	private List<String> popupItems(String csv)
	{
		return new ArrayList<>(Text.fromCSV(csv == null ? "" : csv));
	}

	void setPopupIncluded(String name, boolean included)
	{
		if (included)
		{
			setPopupItem("excludedPopupItems", config.excludedPopupItems(), name, false);
		}
		setPopupItem("includedPopupItems", config.includedPopupItems(), name, included);
	}

	void setPopupExcluded(String name, boolean excluded)
	{
		if (excluded)
		{
			setPopupItem("includedPopupItems", config.includedPopupItems(), name, false);
		}
		setPopupItem("excludedPopupItems", config.excludedPopupItems(), name, excluded);
	}

	private void setPopupItem(String key, String csv, String name, boolean enabled)
	{
		List<String> entries = popupItems(csv);
		entries.removeIf(name::equalsIgnoreCase);
		if (enabled)
		{
			entries.add(name);
		}
		configManager.setConfiguration("collection-celebrations", key, Text.toCSV(entries));
	}

	private void removeOppositePopupRules(String changedKey)
	{
		boolean inclusion = "includedPopupItems".equals(changedKey);
		List<String> selected = popupItems(inclusion ? config.includedPopupItems() : config.excludedPopupItems());
		List<String> opposite = popupItems(inclusion ? config.excludedPopupItems() : config.includedPopupItems());
		if (opposite.removeIf(rule -> selected.stream().anyMatch(rule::equalsIgnoreCase)))
		{
			configManager.setConfiguration("collection-celebrations", inclusion ? "excludedPopupItems" : "includedPopupItems",
				Text.toCSV(opposite));
		}
	}

	private boolean popupIncluded(String name, int quantity)
	{
		return name != null && (GroundItemSoundFilter.match(config.includedPopupItems(), name, quantity) > 0 ||
			(config.highlightPopup() && custom.highlighted(name, quantity)));
	}

	private boolean popupExcluded(Celebration c)
	{
		return c.previewTier == null && GroundItemSoundFilter.match(config.excludedPopupItems(), c.name, c.dropQuantity) > 0;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"collection-celebrations".equals(event.getGroup()))
		{
			return;
		}
		if ("hideCoxRewards".equals(event.getKey()))
		{
			// Switching the option off must put back every line it blanked, chest or no chest.
			// This arrives on the Swing thread, and the chat may only be touched on the client one.
			// Switching it on mid-raid must not count as the chest, or this raid would never hold.
			if (!config.hideCoxRewards())
			{
				clientThread.invoke(cox::noteChestOpened);
			}
			return;
		}
		if ("refreshWikiData".equals(event.getKey()))
		{
			wiki.refreshSetting();
			return;
		}
		if ("includedPopupItems".equals(event.getKey()) || "excludedPopupItems".equals(event.getKey()))
		{
			removeOppositePopupRules(event.getKey());
			return;
		}
		if (!"previewSelection".equals(event.getKey()) && !"previewKind".equals(event.getKey()))
		{
			return;
		}
		PreviewSelection selection = config.previewSelection();
		PreviewKind kind = config.previewKind();
		clientThread.invokeLater(() -> {
			if (!running)
			{
				return;
			}
			updatePreview(selection, kind);
		});
	}

	void updatePreview(PreviewSelection selection, PreviewKind kind)
	{
		if (selection == null || selection == PreviewSelection.OFF)
		{
			previews.clear();
			overlay.clearPreview();
			sounds.cancelPreview();
		}
		else
		{
			appendPreview(selection.tier, kind);
		}
	}
	void queuePreview(PreviewTier tier)
	{
		appendPreview(tier);
	}
	private void appendPreview(PreviewTier tier)
	{
		appendPreview(tier, config.previewKind());
	}
	private void appendPreview(PreviewTier tier, PreviewKind kind)
	{
		if (tier == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		WikiRarity.Entry example = wiki.example(tier);
		String name = example == null ? tier.itemName : example.name;
		// A real source makes the test rate concrete instead of spanning every boss that drops the item.
		String source = wiki.previewSource(name, ThreadLocalRandom.current().nextInt());
		Celebration c = new Celebration(name, example == null ? tier.itemId : example.id, 1,
										source == null ? WikiDropRates.PREVIEW_SOURCE : source, true, 0, now(), 0);
		c.newSlot = kind != PreviewKind.REPEAT_DROP;
		c.previewTier = tier;
		c.tier = tier;
		c.confirmedTotal = c.newSlot ? 1 : 2;
		c.kc = "Example KC: 123";
		previews.add(c);
	}
	private int rarityPriority(Celebration c)
	{
		if (c.previewTier != null)
		{
			return c.previewTier.ordinal();
		}
		RarityResult result = wiki.resolve(c.itemId, c.name);
		return result == null ? 0 : PreviewTier.valueOf(result.getTier().name()).ordinal();
	}

	private long valuePriority(Celebration c)
	{
		WikiRarity.Entry entry = wiki.entry(c.itemId, c.name);
		int id = entry != null ? entry.id : c.itemId >= 0 ? c.itemId : names.getOrDefault(c.name.toLowerCase(Locale.ROOT), -1);
		ItemComposition item = id < 0 ? null : items.getItemComposition(id);
		return item == null || untradeable(item) ? 0 : itemValue(id, item, c.dropQuantity);
	}

	/** Only an item that neither the Grand Exchange nor another player will take has no value at all. */
	static boolean untradeable(ItemComposition item)
	{
		return !item.isGeTradeable() && !item.isTradeable();
	}

	private long itemValue(int id, ItemComposition item, int quantity)
	{
		long ge = Math.max(0, items.getItemPrice(id));
		long ha = Math.max(0, item.getHaPrice());
		long unit = config.valueMode() == ValueMode.HIGH_ALCH ? ha
			: config.valueMode() == ValueMode.HIGHEST ? Math.max(ge, ha) : ge;
		return unit * Math.max(1, quantity);
	}

	private void preparePresentation(Celebration c)
	{
		c.dropRateText = wiki.dropRateText(c.source, c.name);
		WikiRarity.Entry entry = wiki.entry(c.itemId, c.name);
		if (entry != null)
		{
			c.itemId = entry.id;
			c.extraItem = false;
			c.wikiCompletion = entry.completion;
		}
		if (c.itemId >= 0)
		{
			ItemComposition item = items.getItemComposition(c.itemId);
			if (item != null)
			{
				c.untradeable = untradeable(item);
				c.value = itemValue(c.itemId, item, c.dropQuantity);
				RarityResult resolved = wiki.resolve(c.itemId, c.name);
				if (resolved != null)
				{
					c.wikiCompletion = resolved.getCompPercent();
					if (c.previewTier == null)
					{
						c.tier = PreviewTier.valueOf(resolved.getTier().name());
					}
				}
			}
		}
	}

	private void attachKc(Celebration c)
	{
		if (c.kc != null || c.source == null || c.lootTick != lastKcTick || lastKcTick < 0 || now() - c.created > 5000)
		{
			return;
		}
		List<String> sourceNames = new ArrayList<>();
		sourceNames.add(c.source);
		if (c.source.toLowerCase(Locale.ROOT).startsWith("clue scroll"))
		{
			for (String tier : List.of("beginner", "easy", "medium", "hard", "elite", "master"))
			{
				if (c.source.toLowerCase(Locale.ROOT).contains(tier))
				{
					sourceNames.add(tier + " Treasure Trails");
				}
			}
		}
		// Some counts name the collection log tab instead of the loot event, like the Sepulchre coffins.
		WikiRarity.Entry entry = wiki.entry(c.itemId, c.name);
		if (entry != null)
		{
			sourceNames.addAll(entry.tabs);
		}
		KillCountTracker.RecentKill kill = kills.killCountFor(sourceNames);
		if (kill != null)
		{
			c.kc = kill.getKind().labelFor(kill.getSource()) + kill.getKillCount();
		}
	}
	@Subscribe
	public void onBeforeRender(BeforeRender e)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		long now = now();
		boolean hold = gate.blocked();
		overlay.advance(now, hold);
		boolean presentationStarted = false;
		for (Celebration c : pending.items())
		{
			attachKc(c);
		}
		// This runs on every frame, so nothing is allocated while both queues are empty.
		if (overlay.idle() && !hold && (pending.size() > 0 || previews.size() > 0) && !sounds.busy())
		{
			Celebration c = null;
			if (pending.size() > 0)
			{
				Map<Celebration, Integer> priorities = new IdentityHashMap<>();
				Map<Celebration, Long> values = new IdentityHashMap<>();
				Comparator<Celebration> order = Comparator.comparing((Celebration n) -> n.newSlot)
					.thenComparingInt(n -> priorities.computeIfAbsent(n, this::rarityPriority))
					.thenComparingLong(n -> values.computeIfAbsent(n, this::valuePriority));
				// A Chambers unique waits for its chest; everything else behind it still goes through.
				c = pending.poll(n -> now >= n.due && !cox.holds(n) && !awaitingLoot(n), order);
			}
			if (c == null && pending.size() == 0)
			{
				c = previews.poll(n -> true, Comparator.comparingInt(this::rarityPriority));
			}
			if (c != null)
			{
				if (c.previewTier == null)
				{
					if (c.itemId < 0)
					{
						c.itemId = names.getOrDefault(c.name.toLowerCase(Locale.ROOT), -1);
					}
					followProfile();
					if (ledger.get(c.itemId) != null)
					{
						c.extraItem = false;
					}
					// An unlock without loot, like a quest reward, is the first copy.
					if (c.newSlot && c.countedTotal == null)
					{
						c.countedTotal = c.dropQuantity;
					}
				}
				preparePresentation(c);
				if (!popupExcluded(c) && (c.previewTier != null || c.newSlot || popupIncluded(c.name, c.dropQuantity) ||
					(!c.extraItem && config.repeatDrops() && TierStyle.repeats(c.tier, config))))
				{
					presentationStarted = true;
					c.presented = true;
					overlay.show(c, now);
					if (config.collectionAudio() &&
						(c.upgradeOnly || c.previewTier != null || !config.bulkUnlockSfx() || c.audioGroup != lastAudioGroup))
					{
						String sound = TierStyle.file(c.tier, config);
						int volume = TierStyle.volume(c.tier, config);
						if (c.previewTier == null && config.highlightedItemSound() && custom.highlighted(c.name, c.dropQuantity))
						{
							sound = custom.highlightedRewardFile(c.itemId, c.dropQuantity, c.tier);
							volume = custom.highlightedRewardVolume(c.itemId, c.dropQuantity, c.tier);
						}
						int unlockVolume = c.newSlot ? unlockVolume(c.tier) : 0;
						if (c.previewTier != null)
						{
							if (unlockVolume > 0 && SoundQueue.validName(config.unlockFile()))
							{
								sounds.playPreview(sound, volume, config.unlockFile(), unlockVolume);
							}
							else
							{
								sounds.playPreview(sound, volume);
							}
						}
						else
						{
							if (!c.upgradeOnly && SoundQueue.validName(sound))
							{
								sounds.playNow(sound.trim(), volume);
							}
							if (unlockVolume > 0 && SoundQueue.validName(config.unlockFile()))
							{
								sounds.playNow(config.unlockFile().trim(), unlockVolume);
							}
							lastAudioGroup = c.audioGroup;
							c.audible = true;
							// The popup sound covers the whole reward; other items in it must not
							// replay a drop sound once this one finishes.
							sounds.cancelValueGroup(c.audioGroup);
						}
					}
				}
				else if (!popupExcluded(c) && c.previewTier == null && config.collectionAudio() &&
					config.highlightedItemSound() && custom.highlighted(c.name, c.dropQuantity))
				{
					custom.offerHighlightedReward(c.itemId, c.dropQuantity, c.tier, c.name, c.audioGroup);
				}
			}
		}
		// Ready collection notifications own the first available audio slot. A stream
		// of standalone value sounds must not keep their popup waiting indefinitely.
		if (!presentationStarted)
		{
			sounds.tick(now);
		}

		if (client.getWidget(InterfaceID.NotificationDisplay.FRAME) == null)
		{
			hiddenPaint.clear();
		}
		// Only replace collection-log paint; never change game popup settings or the root used by case plugins.
		// A held Chambers unique stays hidden even with our own popups off, or the game's would spoil it.
		boolean heldUnique = cox.holding() && cox.covers(replacedItem);
		if ((config.showPopups() || heldUnique) && replacedNotification != null &&
			replacedNotification.equals(client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN)) &&
			"Collection log".equalsIgnoreCase(client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE)))
		{
			for (int component : PAINT)
			{
				Widget w = client.getWidget(component);
				if (w != null)
				{
					hide(w);
					if (w.getDynamicChildren() != null)
					{
						for (Widget child : w.getDynamicChildren())
						{
							if (child != null)
							{
								hide(child);
							}
						}
					}
				}
			}
		}
		else
		{
			restorePaint();
		}
	}
	/**
	 * The collection log message can come a tick before the loot event that starts a case reel, so
	 * with a case plugin installed an unlock waits for that tick instead of sounding before the hold.
	 */
	private boolean awaitingLoot(Celebration c)
	{
		return c.lootTick < 0 && c.unlockTick >= 0 && client.getTickCount() <= c.unlockTick && gate.installed();
	}
	int unlockVolume(PreviewTier tier)
	{
		int requested = Math.max(0, Math.min(100, config.unlockVolume())) * Math.max(0, Math.min(100, config.masterVolume())) / 100;
		// Keep the extra accent below the selected drop sound, including quiet tiers.
		return Math.min(requested, TierStyle.volume(tier, config) * 35 / 100);
	}

	private void queueUnlockJingle(Celebration c)
	{
		if (popupExcluded(c) || !config.collectionAudio() || TierStyle.volume(c.tier, config) <= 0)
		{
			return;
		}
		int volume = unlockVolume(c.tier);
		if (SoundQueue.validName(config.unlockFile()))
		{
			sounds.offer(config.unlockFile().trim(), volume, 0, true);
		}
	}

	private void hide(Widget w)
	{
		if (!w.isSelfHidden())
		{
			hiddenPaint.add(w);
			w.setHidden(true);
		}
	}
	private void restorePaint()
	{
		for (Widget w : hiddenPaint)
		{
			w.setHidden(false);
		}
		hiddenPaint.clear();
	}
}
