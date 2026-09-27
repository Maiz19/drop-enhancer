/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.client.config.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class ConfigVisibilityTest
{
	@Test
	public void everySettingsItemHasAVisibleSection()
	{
		ConfigManager manager = mock(ConfigManager.class, CALLS_REAL_METHODS);
		ConfigDescriptor descriptor = manager.getConfigDescriptor(new CelebrationConfig() {});
		Set<String> sections = descriptor.getSections().stream().map(ConfigSectionDescriptor::getKey).collect(Collectors.toSet());
		assertTrue(sections.contains("valueDrops"));
		assertTrue(sections.contains("clueDrops"));
		assertFalse(sections.contains("specWeapon"));
		assertEquals(6, sections.size());
		for (ConfigItemDescriptor item : descriptor.getItems())
		{
			assertTrue(item.getItem().keyName(), item.getItem().section().isEmpty() || sections.contains(item.getItem().section()));
		}
		assertTrue(descriptor.getItems().size() > 60);
	}
	@Test
	public void settingsInOneSectionNeverShareAPosition()
	{
		// Shared positions fall back to alphabetical order, which split the Pet settings in two.
		ConfigManager manager = mock(ConfigManager.class, CALLS_REAL_METHODS);
		Set<String> seen = new java.util.HashSet<>();
		for (ConfigItemDescriptor item : manager.getConfigDescriptor(new CelebrationConfig() {}).getItems())
		{
			String slot = item.getItem().section() + " #" + item.getItem().position();
			assertTrue(item.getItem().keyName() + " shares " + slot, item.getItem().hidden() || seen.add(slot));
		}
	}
	@Test
	public void oldTextModesCarryOverOnlyTheirOutline()
	{
		for (String[] modes : new String[][] {{"SMOOTH_OUTLINED", "SHADOW"}, {"SMOOTH", "NONE"}, {"CRISP", "NONE"}})
		{
			ConfigManager manager = mock(ConfigManager.class);
			when(manager.getConfiguration("collection-celebrations", "textRenderMode")).thenReturn(modes[0]);
			CelebrationPlugin.migrateTextOutline(manager);
			verify(manager).setConfiguration("collection-celebrations", "textOutline", TextOutline.valueOf(modes[1]));
			verify(manager).unsetConfiguration("collection-celebrations", "textRenderMode");
		}
		// A choice already made in the new setting stays, and without the old setting nothing happens at all.
		ConfigManager chosen = mock(ConfigManager.class);
		when(chosen.getConfiguration("collection-celebrations", "textRenderMode")).thenReturn("CRISP");
		when(chosen.getConfiguration("collection-celebrations", "textOutline")).thenReturn("OUTLINE");
		CelebrationPlugin.migrateTextOutline(chosen);
		verify(chosen, never()).setConfiguration(anyString(), anyString(), any(Object.class));
		verify(chosen).unsetConfiguration("collection-celebrations", "textRenderMode");
		ConfigManager fresh = mock(ConfigManager.class);
		CelebrationPlugin.migrateTextOutline(fresh);
		verify(fresh, never()).unsetConfiguration(anyString(), anyString());
	}
	@Test
	public void wikiNetworkAccessIsOptOutWithPrivacyWarning()
	{
		CelebrationConfig config = new CelebrationConfig() {};
		// On by default, so the popup has rates out of the box; the switch remains the way to stop it.
		assertTrue(config.refreshWikiData());
		ConfigManager manager = mock(ConfigManager.class, CALLS_REAL_METHODS);
		ConfigItem item = manager.getConfigDescriptor(config).getItems().stream()
			.map(ConfigItemDescriptor::getItem).filter(c -> c.keyName().equals("refreshWikiData"))
			.findFirst().orElseThrow();
		assertEquals("This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers", item.warning());
	}

}
