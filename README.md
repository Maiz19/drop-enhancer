# Drop Enhancer

Configurable popups and sounds for collection log unlocks and other notable drops.

## What it does

- Shows a popup for every new collection log unlock, and optionally for repeat drops.
- Colours each popup by rarity and shows up to five statistics: collection count, kill count, item value, wiki completion and drop rate.
- Plays a sound per rarity tier, plus value-based audio for ground item drops.
- Lets you include or exclude individual items, with wildcard and quantity rules.
- Holds a notification until a Clue Case or Loot Case reel has finished.
- Previews any popup and sound straight from the settings.

## Setup

Turn on the game's collection log notifications (**Chat**, **Pop-up** or **Both**). With **Both**, Drop Enhancer still shows a single unlock notification.

Open pages of your collection log so the plugin can read your item quantities. Those reset on logout or world hop.

## Options worth knowing

**Combination rarity** uses the value tier as a minimum, with Ground Items value thresholds linked by default. Wiki completion can raise the tier of cheaper items, but cannot lower an expensive drop below its value tier. Turn off **Link to Ground Items values** to use your own GP cutoffs. Untradeable items use rarity only; pets keep their Pet tier.

**Custom sounds** — put an uncompressed PCM `.wav` (up to 32 MB) in `.runelite/collection-celebrations/sounds/` and enter its filename in the tier's WAV setting. Keep the default filename for the bundled sound, or leave it blank to mute the tier.

**Refresh wiki data** — **on by default.** The plugin downloads public item lists, completion percentages and drop rates from `oldschool.runescape.wiki`, refreshed weekly on the Thursday after the Wednesday game update. This contacts a third-party server that is not controlled or verified by the RuneLite developers, so the wiki receives your IP address as it would for any web request. No player name, collection contents or other personal data is ever sent. Turn the setting off to stop all wiki requests; existing data is kept if a refresh fails. Wiki data is CC BY-NC-SA 3.0 by OSRS Wiki contributors and is not bundled with the plugin.

**Hide CoX rewards** — on by default. Chambers of Xeric announces a unique while the team is still walking to the chest, so the chat line is censored and the popup and its sound wait until the reward chest is opened. Other raids send their collection log only after the chest and are unaffected.

**Drop rate** is shown for the matching source. Guaranteed drops and items the wiki does not list leave the statistic out; variants the game does not name show a range such as `1/18 – 1/19`. Rates are for display and never change rarity colours or sounds.

## Credits

Based on [Collection Log Popup Enhanced](https://github.com/TimHeessels/collection-log-popup-enhanced) by SnakeSteak and [Custom Sounds](https://github.com/daanbom/Custom-Drop-Sounds) by daanbom. Integration by maiz. RuneLite provides client assets; optional wiki data credits OSRS Wiki contributors.

Source code is [BSD-2-Clause](LICENSE). Rare, Very rare and collection-log sounds are courtesy of **daanbom / Custom Sounds**, used with permission; Common, Uncommon and Pet are original synthesized sounds. Full credits and upstream licenses: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [audio terms](AUDIO-PERMISSION.md).
