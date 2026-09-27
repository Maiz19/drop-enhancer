/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
public class PopupImageTest
{
	@Test
	public void fifthStatisticKeepsAllFourExistingStatisticsAndCanBeHidden()
	{
		Celebration c = new Celebration("Example", -1, 1, "Boss", false, 0, 0, 0);
		c.tier = PreviewTier.COMMON;
		c.confirmedTotal = 3;
		c.kc = "Kills: 347";
		c.value = 120000;
		c.wikiCompletion = 12.5;
		c.dropRateText = "1/512";
		CelebrationConfig off = new CelebrationConfig() {
			@Override public boolean showDropRate() { return false; }
		};
		int[] before = effectPixels(c, off, 1200);
		int[] after = effectPixels(c, new CelebrationConfig() {}, 1200);
		for (int y = 100; y < 180; y++)
		{
			for (int x = 25; x < 490; x++)
			{
				if (x < 195 || x > 305)
				{
					assertEquals("Existing statistic changed", before[y * 510 + x], after[y * 510 + x]);
				}
			}
		}
		assertFalse(java.util.Arrays.equals(before, after));
		c.dropRateText = "1/1024";
		assertArrayEquals(before, effectPixels(c, off, 1200));
		assertFalse(java.util.Arrays.equals(after, effectPixels(c, new CelebrationConfig() {}, 1200)));

		// A guaranteed or unlisted item has no rate, so the statistic is left out rather than dashed.
		c.dropRateText = null;
		assertArrayEquals(effectPixels(c, off, 1200), effectPixels(c, new CelebrationConfig() {}, 1200));
	}

	@Test
	public void anEnabledKillCountHoldsItsSlotWithoutACount()
	{
		Celebration c = new Celebration("Example", -1, 1, "Boss", false, 0, 0, 0);
		c.tier = PreviewTier.COMMON;
		c.confirmedTotal = 3;
		c.value = 120000;
		c.wikiCompletion = 12.5;
		c.dropRateText = "1/512";
		CelebrationConfig config = new CelebrationConfig() {};
		c.kc = "Kills: 347";
		int[] counted = effectPixels(c, config, 1200);
		c.kc = null;
		int[] uncounted = effectPixels(c, config, 1200);
		assertFalse("Without a count the slot must read differently", java.util.Arrays.equals(counted, uncounted));
		// The panel is drawn at x=10,y=10, so the top-right slot covers image x 307-463, y 111-141.
		for (int y = 0; y < 260; y++)
		{
			for (int x = 0; x < 510; x++)
			{
				if (x >= 300 && x <= 470 && y >= 105 && y <= 150)
				{
					continue;
				}
				assertEquals("The other statistics must not move", counted[y * 510 + x], uncounted[y * 510 + x]);
			}
		}
	}

	@Test
	public void switchedOffStatisticsLeaveNothingBehind()
	{
		Celebration c = new Celebration("Example", -1, 1, "Boss", false, 0, 0, 0);
		c.tier = PreviewTier.COMMON;
		c.confirmedTotal = 3;
		c.kc = "Kills: 347";
		c.value = 120000;
		c.wikiCompletion = 12.5;
		CelebrationConfig shown = new CelebrationConfig() {
			@Override public boolean showDropRate() { return false; }
		};
		CelebrationConfig unselected = new CelebrationConfig() {
			@Override public boolean showDropRate() { return false; }
			@Override public PopupStat stat1() { return PopupStat.NONE; }
			@Override public PopupStat stat2() { return PopupStat.NONE; }
			@Override public PopupStat stat3() { return PopupStat.NONE; }
			@Override public PopupStat stat4() { return PopupStat.NONE; }
		};
		// Switching every statistic off must look exactly like selecting none of them: no labelled dashes.
		assertArrayEquals(effectPixels(c, unselected, 1200), effectPixels(c, shown, 1200, false));
		assertFalse(java.util.Arrays.equals(effectPixels(c, shown, 1200), effectPixels(c, shown, 1200, false)));
	}

	@Test
	public void unlockAddsOuterGoldButPreservesRarityBorder()
	{
		Celebration c = new Celebration("Mole claw", 7416, 1, "Giant Mole", true, 0, 0, 0);
		c.tier = PreviewTier.COMMON;
		java.awt.Color rarity = new java.awt.Color(0x45B9D7);
		BufferedImage unlock = panel(c, rarity);
		c.newSlot = false;
		BufferedImage repeat = panel(c, rarity);
		// Same left border colour, different pixels outside it where the gold ornament sits.
		assertEquals(rarity.getRGB(), unlock.getRGB(10, 60));
		assertEquals(unlock.getRGB(10, 60), repeat.getRGB(10, 60));
		assertTrue((unlock.getRGB(5, 60) >>> 24) > 0);
		assertEquals(0, repeat.getRGB(5, 60) >>> 24);
	}
	private BufferedImage panel(Celebration c, java.awt.Color rarity)
	{
		BufferedImage image = new BufferedImage(510, 240, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			CelebrationOverlay.drawPanel(g, 10, 10, 480, c, rarity, null);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}
	@Test
	public void animationEntersFromAboveAndPausesDuringCaseHold()
	{
		CelebrationOverlay overlay = new CelebrationOverlay();
		overlay.client = mock(Client.class);
		overlay.items = mock(ItemManager.class);
		overlay.config = new CelebrationConfig() {};
		when(overlay.client.getViewportWidth()).thenReturn(800);
		when(overlay.client.getViewportHeight()).thenReturn(600);
		Celebration c = new Celebration("Mole claw", -1, 1, "Giant Mole", true, 0, 0, 0);
		c.tier = PreviewTier.COMMON;
		overlay.show(c, 0);
		assertEquals(0, pixels(overlay));
		overlay.advance(550, false);
		assertTrue(pixels(overlay) > 1000);
		overlay.advance(600, true);
		assertEquals(0, pixels(overlay));
		overlay.advance(100000, true);
		assertFalse(overlay.idle());
		overlay.advance(100001, false);
		assertTrue(pixels(overlay) > 1000);
		overlay.advance(110000, false);
		assertTrue(overlay.idle());
	}
	private int pixels(CelebrationOverlay overlay)
	{
		BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			overlay.render(g);
		}
		finally
		{
			g.dispose();
		}
		int count = 0;
		for (int y = 0; y < 600; y++)
		{
			for (int x = 0; x < 800; x++)
			{
				if ((image.getRGB(x, y) >>> 24) > 0)
				{
					count++;
				}
			}
		}
		return count;
	}
	@Test
	public void previewRequestsActualItemSpriteAndCanBeReplaced()
	{
		CelebrationOverlay overlay = new CelebrationOverlay();
		overlay.client = mock(Client.class);
		overlay.items = mock(ItemManager.class);
		overlay.config = new CelebrationConfig() {};
		when(overlay.client.getViewportWidth()).thenReturn(800);
		when(overlay.client.getViewportHeight()).thenReturn(600);
		Celebration preview = new Celebration("Bandos chestplate", 11832, 1, "Preview", true, 0, 0, 0);
		preview.previewTier = PreviewTier.RARE;
		preview.tier = PreviewTier.RARE;
		overlay.show(preview, 0);
		Graphics2D g = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB).createGraphics();
		try
		{
			overlay.render(g);
		}
		finally
		{
			g.dispose();
		}
		verify(overlay.items).getImage(11832);
		overlay.clearPreview();
		assertTrue(overlay.idle());
		preview.previewTier = null;
		overlay.show(preview, 0);
		overlay.clearPreview();
		assertFalse(overlay.idle());
	}
	@Test
	public void onlyNewUnlockHasCollectionHeading()
	{
		Celebration c = new Celebration("Mole claw", 7416, 1, "Giant Mole", true, 0, 0, 0);
		assertEquals("NEW COLLECTION LOG", CelebrationOverlay.heading(c));
		c.newSlot = false;
		assertEquals("", CelebrationOverlay.heading(c));
		c.previewTier = PreviewTier.COMMON;
		assertEquals("TEST", CelebrationOverlay.heading(c));
	}
	@Test
	public void highTierEffectsMoveAndCanBeDisabled()
	{
		Celebration c = new Celebration("Twisted bow", -1, 1, "Chambers of Xeric", false, 0, 0, 0);
		c.tier = PreviewTier.RARE;
		CelebrationConfig on = new CelebrationConfig() {};
		CelebrationConfig off = new CelebrationConfig() {
			@Override
			public boolean tierEffects()
			{
				return false;
			}
		};
		assertFalse(java.util.Arrays.equals(effectPixels(c, on, 1200), effectPixels(c, on, 1700)));
		assertArrayEquals(effectPixels(c, off, 1200), effectPixels(c, off, 1700));
	}
	private int[] effectPixels(Celebration c, CelebrationConfig config, long elapsed)
	{
		return effectPixels(c, config, elapsed, true);
	}
	private int[] effectPixels(Celebration c, CelebrationConfig config, long elapsed, boolean statisticsVisible)
	{
		BufferedImage image = new BufferedImage(510, 260, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		boolean v = statisticsVisible;
		try
		{
			CelebrationOverlay.drawPanel(g, 10, 10, 480, c, java.awt.Color.PINK, null, v, v, v, v, config, elapsed);
		}
		finally
		{
			g.dispose();
		}
		return image.getRGB(0, 0, 510, 260, null, 0, 510);
	}
	@Test
	public void fixedTopPaddingKeepsGoldBannerVisible()
	{
		CelebrationOverlay overlay = new CelebrationOverlay();
		overlay.client = mock(Client.class);
		overlay.items = mock(ItemManager.class);
		overlay.config = new CelebrationConfig() {};
		when(overlay.client.getViewportWidth()).thenReturn(800);
		when(overlay.client.getViewportHeight()).thenReturn(600);
		Celebration c = new Celebration("Mole claw", -1, 1, "Giant Mole", true, 0, 0, 0);
		c.tier = PreviewTier.COMMON;
		overlay.show(c, 0);
		overlay.advance(1200, false);
		BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			overlay.render(g);
		}
		finally
		{
			g.dispose();
		}
		assertTrue("Only the faint halo may reach the top margin", (image.getRGB(300, 0) >>> 24) < (image.getRGB(300, 8) >>> 24));
		assertTrue((image.getRGB(300, 8) >>> 24) > 0);
		java.awt.Color banner = new java.awt.Color(image.getRGB(300, 24), true);
		assertTrue("Visible gold ribbon inside the screen", banner.getRed() > banner.getBlue() + 30);
	}
	@Test
	public void transparentPaddingDoesNotShiftOrShrinkVisibleItem()
	{
		BufferedImage padded = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D source = padded.createGraphics();
		source.setColor(java.awt.Color.WHITE);
		source.fillRect(1, 3, 10, 20);
		source.dispose();
		BufferedImage cropped = padded.getSubimage(1, 3, 10, 20);
		BufferedImage first = drawIcon(padded, 64, 32);
		BufferedImage second = drawIcon(cropped, 64, 32);
		assertArrayEquals(first.getRGB(0, 0, 100, 100, null, 0, 100), second.getRGB(0, 0, 100, 100, null, 0, 100));
		// A whole 2x: the visible 10x20 covers exactly 20x40 around the centre.
		assertNotEquals(0, first.getRGB(50, 30) >>> 24);
		assertNotEquals(0, first.getRGB(50, 69) >>> 24);
		assertEquals(0, first.getRGB(50, 29) >>> 24);
		assertEquals(0, first.getRGB(50, 70) >>> 24);
	}

	@Test
	public void squareAndDiagonalSpritesFitWithoutClippingAtMultipleSizes()
	{
		for (boolean diagonal : new boolean[] {false, true})
		{
			BufferedImage icon = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 32; y++)
			{
				for (int x = 0; x < 36; x++)
				{
					if (!diagonal || Math.abs(x - y) <= 2)
					{
						icon.setRGB(x, y, java.awt.Color.WHITE.getRGB());
					}
				}
			}
			for (int radius : new int[] {8, 16, 24, 32, 40})
			{
				BufferedImage rendered = drawIcon(icon, radius * 2, radius);
				int visible = 0;
				for (int y = 0; y < 100; y++)
				{
					for (int x = 0; x < 100; x++)
					{
						if ((rendered.getRGB(x, y) >>> 24) != 0)
						{
							visible++;
							assertTrue("Sprite pixel outside ring", Math.hypot(x - 50, y - 50) <= radius);
						}
					}
				}
				assertTrue(visible > 0);
			}
		}
	}

	@Test
	public void everyOutlineStyleKeepsHardEdges()
	{
		Celebration c = new Celebration("Ring of endurance (uncharged)", -1, 1, "Hallowed Sepulchre Grand Coffin", false, 0, 0, 0);
		c.tier = PreviewTier.UNCOMMON;
		c.confirmedTotal = 2;
		c.kc = "Coffins: 12";
		c.value = 12_500_000;
		c.wikiCompletion = 2.9;
		int[] black = new int[TextOutline.values().length];
		for (TextOutline outline : TextOutline.values())
		{
			BufferedImage image = new BufferedImage(480, 260, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			CelebrationOverlay.drawPanel(g, 12, 10, 432, c, new java.awt.Color(0x99FF99), null, true, true, true, true,
				new CelebrationConfig() { @Override public TextOutline textOutline() { return outline; } }, 1200);
			g.dispose();
			for (int y = 0; y < image.getHeight(); y++)
			{
				for (int x = 0; x < image.getWidth(); x++)
				{
					int argb = image.getRGB(x, y), alpha = argb >>> 24;
					// Stretched mode enlarges these pixels, so a partly covered edge pixel would blur.
					assertTrue(outline + " has a soft pixel", alpha == 0 || alpha == 255);
					black[outline.ordinal()] += argb == 0xFF000000 ? 1 : 0;
				}
			}
		}
		// Only the text edge is pure black: none, a one-sided shadow, or a four-way outline.
		assertEquals(0, black[TextOutline.NONE.ordinal()]);
		assertTrue(black[TextOutline.SHADOW.ordinal()] > 0);
		assertTrue(black[TextOutline.OUTLINE.ordinal()] > black[TextOutline.SHADOW.ordinal()]);
		assertEquals(TextOutline.SHADOW, new CelebrationConfig() {}.textOutline());

		// With the extra drop rate the tier name moves under the medallion, over bare scenery, and keeps a shadow.
		c.dropRateText = "1/1,000";
		BufferedImage image = new BufferedImage(480, 260, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		CelebrationOverlay.drawPanel(g, 12, 10, 432, c, new java.awt.Color(0x99FF99), null, true, true, true, true,
			new CelebrationConfig() { @Override public TextOutline textOutline() { return TextOutline.NONE; } }, 1200);
		g.dispose();
		boolean shadow = false;
		for (int y = 180; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				shadow |= image.getRGB(x, y) == 0xFF000000;
			}
		}
		assertTrue(shadow);
	}

	@Test
	public void textUsesTheFontsOwnPixelSizes()
	{
		for (int size : new int[] {12, 13, 14, 16, 18, 24})
		{
			for (double scale = .6; scale <= 2; scale += .05)
			{
				assertTrue(CelebrationOverlay.pixelMultiple(size, scale) >= 1);
			}
		}
		// At the default 90% scale the name stands out while statistics use the fonts' own size.
		assertEquals(2, CelebrationOverlay.pixelMultiple(24, .9));
		assertEquals(1, CelebrationOverlay.pixelMultiple(18, .9));
		assertEquals(1, CelebrationOverlay.pixelMultiple(12, .9));
		assertEquals(net.runelite.client.ui.FontManager.getRunescapeSmallFont(), CelebrationOverlay.pixelFont(12));
		assertEquals(net.runelite.client.ui.FontManager.getRunescapeBoldFont(), CelebrationOverlay.pixelFont(24));
	}

	@Test
	public void iconUsesWholePixelBlocks()
	{
		BufferedImage icon = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 32; y++)
		{
			for (int x = 0; x < 36; x++)
			{
				icon.setRGB(x, y, (x + y) % 2 == 0 ? 0xFFFFFFFF : 0xFF000000);
			}
		}
		BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		// Room for 2.45x rounds down to exactly 2x.
		CelebrationOverlay.drawItemIcon(g, icon, 50, 50, 90, 60);
		g.dispose();
		for (int y = 0; y < 64; y++)
		{
			for (int x = 0; x < 72; x++)
			{
				assertEquals(icon.getRGB(x / 2, y / 2), image.getRGB(14 + x, 18 + y));
			}
		}
		assertEquals(0, image.getRGB(13, 50) >>> 24);
		assertEquals(0, image.getRGB(86, 50) >>> 24);
	}

	private BufferedImage drawIcon(BufferedImage icon, double maxSize, double radius)
	{
		BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			CelebrationOverlay.drawItemIcon(g, icon, 50, 50, maxSize, radius);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}

}
