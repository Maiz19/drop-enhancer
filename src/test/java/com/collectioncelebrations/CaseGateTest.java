/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.awt.Rectangle;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CaseGateTest
{
	private static class Gate extends CaseGate
	{
		long time = 1000;
		@Override
		long now()
		{
			return time;
		}
	}
	private Gate gate;
	private Overlay overlay;
	@Before
	public void setup()
	{
		gate = new Gate();
		gate.client = mock(Client.class);
		gate.overlays = mock(OverlayManager.class);
		overlay = mock(Overlay.class);
		when(gate.overlays.anyMatch(any())).thenAnswer(i -> ((Predicate<Overlay>)i.getArgument(0)).test(overlay));
	}
	@Test
	public void clueReleasesWhenSpinEndsEvenIfResultStaysOpen()
	{
		when(overlay.getName()).thenReturn("ClueCaseOverlay");
		gate.refresh();
		Widget w = mock(Widget.class);
		when(gate.client.getWidget(InterfaceID.TrailRewardscreen.ITEMS)).thenReturn(w);
		when(w.isSelfHidden()).thenReturn(true);
		assertTrue(gate.blocked());
		gate.time += 6000;
		assertTrue(gate.blocked());
		gate.time += 600;
		assertFalse(gate.blocked());
	}
	@Test
	public void clueDismissedEarlyReleasesImmediately()
	{
		when(overlay.getName()).thenReturn("ClueCaseOverlay");
		gate.refresh();
		Widget w = mock(Widget.class);
		when(gate.client.getWidget(InterfaceID.TrailRewardscreen.ITEMS)).thenReturn(w);
		when(w.isSelfHidden()).thenReturn(true);
		gate.noteReward("Clue Scroll (Hard)");
		assertTrue(gate.blocked());
		gate.time += 1000;
		when(w.isSelfHidden()).thenReturn(false);
		assertFalse(gate.blocked());
	}
	@Test
	public void lootHoldsOnlyWhileReelSpins()
	{
		when(overlay.getName()).thenReturn("LootCaseOpeningOverlay");
		Rectangle bounds = new Rectangle();
		when(overlay.getBounds()).thenReturn(bounds);
		gate.refresh();
		gate.noteReward("Barrows");
		assertTrue(gate.blocked());
		bounds.setSize(620, 130);
		gate.time += 3000;
		assertTrue(gate.blocked());
		// Reveal panel stays until dismissed, but the spin is over.
		bounds.setSize(320, 360);
		assertFalse(gate.blocked());
		bounds.setSize(0, 0);
		assertFalse(gate.blocked());
	}
	@Test
	public void lootReelHoldsEvenBeforeRewardIsNoted()
	{
		when(overlay.getName()).thenReturn("LootCaseOpeningOverlay");
		when(overlay.getBounds()).thenReturn(new Rectangle(0, 0, 620, 130));
		gate.refresh();
		assertTrue(gate.blocked());
		gate.time += 11000;
		assertFalse(gate.blocked());
	}
	@Test
	public void unrelatedNpcDoesNotWaitForACase()
	{
		when(overlay.getName()).thenReturn("LootCaseOpeningOverlay");
		when(overlay.getBounds()).thenReturn(new Rectangle());
		gate.refresh();
		gate.noteReward("General Graardor");
		assertFalse(gate.blocked());
		gate.noteReward("Barrows");
		assertTrue(gate.blocked());
		gate.time += 2500;
		assertFalse(gate.blocked());
	}
	@Test
	public void hiddenUiAfterTheSpinDoesNotHoldRewards()
	{
		when(overlay.getName()).thenReturn("LootCaseOpeningOverlay");
		when(overlay.getBounds()).thenReturn(new Rectangle(0, 0, 320, 360));
		gate.refresh();
		Widget w = mock(Widget.class);
		when(w.isSelfHidden()).thenReturn(true);
		when(gate.client.getWidget(anyInt())).thenReturn(w);
		assertFalse(gate.blocked());
	}

	@Test
	public void removingCasePluginReleasesQueue()
	{
		when(overlay.getName()).thenReturn("LootCaseOpeningOverlay");
		gate.refresh();
		gate.noteReward("Barrows");
		assertTrue(gate.blocked());
		when(overlay.getName()).thenReturn("UnrelatedOverlay");
		gate.refresh();
		assertFalse(gate.blocked());
	}
}
