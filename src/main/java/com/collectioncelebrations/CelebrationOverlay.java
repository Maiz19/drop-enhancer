/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE.
 * Layout, font rendering and separate icon animation adapted from SnakeSteak's
 * Collection Log Popup Enhanced; copyright (c) 2026 SnakeSteak.
 * See licenses/enhanced.txt. All frame artwork below is original Java2D geometry. */
package com.collectioncelebrations;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
class CelebrationOverlay extends Overlay
{
	static final int WIDTH = 480, HEIGHT = 234;
	static final long SLIDE_MS = 550, ICON_MS = 400, FADE_MS = 400;
	@Inject
	Client client;
	@Inject
	ItemManager items;
	@Inject
	CelebrationConfig config;
	@Inject
	net.runelite.client.plugins.grounditems.GroundItemsConfig groundItemsConfig;
	private Celebration current;
	private long remaining, lifetime, lastFrame;
	private boolean held;

	CelebrationOverlay()
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
		setMovable(false);
	}
	void clearPreview()
	{
		if (current != null && current.previewTier != null)
		{
			clear();
		}
	}
	boolean idle()
	{
		return current == null;
	}
	boolean showing(Celebration c)
	{
		return current == c;
	}
	void show(Celebration c, long now)
	{
		current = c;
		lifetime = Math.max(1, Math.min(20, config.displaySeconds())) * 1000L + SLIDE_MS + ICON_MS + FADE_MS;
		remaining = lifetime;
		lastFrame = now;
		held = false;
	}
	void clear()
	{
		current = null;
		remaining = 0;
		held = false;
	}
	void advance(long now, boolean hold)
	{
		if (current != null && !held && !hold)
		{
			remaining -= Math.max(0, now - lastFrame);
		}
		lastFrame = now;
		held = hold;
		if (remaining <= 0)
		{
			current = null;
		}
	}
	static double slideProgress(long elapsed)
	{
		double t = Math.max(0, Math.min(1, elapsed / (double)SLIDE_MS));
		return 1 - Math.pow(1 - t, 3);
	}
	@Override
	public Dimension render(Graphics2D original)
	{
		if (current == null || held || (!config.showPopups() && current.previewTier == null))
		{
			return null;
		}
		Graphics2D g = (Graphics2D)original.create();
		try
		{
			double scale = Math.max(.6, Math.min(2, config.popupScale() / 100.0));
			scale = Math.min(
				scale, Math.min((client.getViewportWidth() - 24) / (double)WIDTH, (client.getViewportHeight() - 24) / (double)HEIGHT));
			if (scale <= 0)
			{
				return null;
			}
			int width = (int)Math.round(WIDTH * scale), height = (int)Math.ceil(HEIGHT * scale);
			int left = client.getViewportXOffset(), top = client.getViewportYOffset();
			int x = left + (client.getViewportWidth() - width) / 2;
			int targetY = top + 8;
			long elapsed = lifetime - remaining;
			int y = (int)Math.round(top - height - 12 + (targetY - top + height + 12) * slideProgress(elapsed));
			g.clipRect(left, top, client.getViewportWidth(), client.getViewportHeight());
			g.setComposite(AlphaComposite.SrcOver.derive((float)Math.max(0, Math.min(1, remaining / (double)FADE_MS))));
			// Render geometry and fonts at the target size.
			drawPanel(g, x, y, width, current, TierStyle.color(current.tier, config, groundItemsConfig),
					  config.showItemIcon() && current.itemId >= 0 ? items.getImage(current.itemId) : null, config.showTotal(),
					  config.showKc(), config.showValue(), config.showWiki(), config, elapsed);
		}
		finally
		{
			g.dispose();
		}
		return null;
	}
	static void drawPanel(Graphics2D g, int x, int y, int width, Celebration c, Color accent, BufferedImage icon)
	{
		drawPanel(g, x, y, width, c, accent, icon, true, true, true, true, new CelebrationConfig() {});
	}
	static void drawPanel(Graphics2D g, int x, int y, int width, Celebration c, Color accent, BufferedImage icon, boolean totalVisible,
						  boolean kcVisible, boolean valueVisible, boolean wikiVisible, CelebrationConfig config)
	{
		drawPanel(g, x, y, width, c, accent, icon, totalVisible, kcVisible, valueVisible, wikiVisible, config, 1200);
	}

	static void drawPanel(Graphics2D original, int x, int y, int width, Celebration c, Color accent, BufferedImage icon,
						  boolean totalVisible, boolean kcVisible, boolean valueVisible, boolean wikiVisible, CelebrationConfig config,
						  long elapsed)
	{
		Graphics2D g = (Graphics2D)original.create();
		try
		{
			g.translate(x, y);
			Layout l = new Layout(width / (double)WIDTH, config);
			// Every edge lands on whole pixels, so stretched mode scales the popup up as hard pixels.
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
			if (accent == null)
			{
				accent = new Color(221, 190, 125);
			}
			float brightness = Math.max(10, Math.min(60, config.backgroundDarkness())) / 100f;
			Color tint = new Color(Math.round(accent.getRed() * brightness), Math.round(accent.getGreen() * brightness),
								   Math.round(accent.getBlue() * brightness));
			Shape frame = frame(l, 0);
			if (c.newSlot && config.unlockEffects())
			{
				// Gold is a separate halo OUTSIDE the unmodified rarity frame.
				for (int i = 18; i >= 2; i -= 2)
				{
					g.setStroke(new BasicStroke(l.f(i)));
					g.setColor(new Color(255, 204, 92, 24));
					g.draw(frame);
				}
			}
			g.setPaint(new GradientPaint(0, 0, tint, 0, l.p(176), new Color(14, 17, 23)));
			g.fill(frame);
			g.setColor(accent);
			g.setStroke(new BasicStroke(l.f(2)));
			g.draw(frame);
			g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 85));
			g.setStroke(new BasicStroke(l.f(1)));
			g.draw(frame(l, 5));
			// Decorative top diamond and tier label belong to the rarity, not the unlock effect.
			g.setColor(accent);
			g.fillPolygon(new int[] {l.p(240), l.p(244), l.p(240), l.p(236)}, new int[] {l.p(3), l.p(7), l.p(11), l.p(7)}, 4);
			if (c.newSlot && config.unlockEffects())
			{
				// Keep the gold visible inside the viewport even when attached to its top edge.
				drawUnlockEffect(g, l, elapsed);
			}
			String caption = heading(c);
			l.center(g, caption, 240, 29, 18, c.newSlot ? new Color(0xFFE5A1) : config.colourCaption(), 332);
			String name = c.name + (c.dropQuantity > 1 ? " ×" + c.dropQuantity : "");
			l.center(g, name, 240, 62, 24, config.colourItemName(), 432);
			l.center(g, c.source == null ? WikiDropRates.UNKNOWN_SOURCE : c.source, 240, 81, 13, config.colourStatLabel(), 420);
			PopupStat[] choices = {config.stat1(), config.stat2(), config.stat3(), config.stat4()};
			// Without a rate the statistic is left out entirely: a guaranteed or unlisted item has none to show.
			boolean extraDropRate = config.showDropRate() && c.dropRateText != null
									&& java.util.Arrays.stream(choices).noneMatch(s -> s == PopupStat.DROP_RATE);
			if (extraDropRate)
			{
				l.center(g, "DROP RATE", 240, 98, 12, config.colourStatLabel(), 104);
				l.center(g, c.dropRateText, 240, 116, 16, config.colourStatValue(), 104);
			}
			for (int i = 0; i < choices.length; i++)
			{
				String label = "", value = "—";
				PopupStat choice = choices[i];
				if (choice == null || choice == PopupStat.NONE)
				{
					continue;
				}
				if (choice == PopupStat.DROP_RATE && c.dropRateText == null)
				{
					choice = PopupStat.VALUE;
				}
				if (c.extraItem && (choice == PopupStat.COLLECTION_COUNT || choice == PopupStat.WIKI_COMPLETION))
				{
					continue;
				}
				switch (choice)
				{
				// A statistic that is switched off leaves its slot empty rather than showing a
				// labelled dash; one that is switched on keeps its place even with nothing to show.
				case COLLECTION_COUNT:
					if (!totalVisible)
					{
						continue;
					}
					label = c.confirmedTotal != null ? "COLLECTED" : c.lastSyncedTotal != null ? "LAST SYNCED" : "TEMPORARY";
					value = String.valueOf(c.confirmedTotal != null	   ? c.confirmedTotal
										   : c.lastSyncedTotal != null ? c.lastSyncedTotal
																	   : c.provisionalTotal);
					break;
				case KILL_COUNT:
					if (!kcVisible)
					{
						continue;
					}
					label = "KILL COUNT";
					// A bought item, or a source that keeps no count, holds its slot with a dash so
					// that the other three statistics stay where the player expects them.
					if (c.kc != null)
					{
						String kills = c.kc.replaceFirst("^[^:]+: *", "");
						if (!kills.isBlank())
						{
							value = kills;
						}
					}
					break;
				case VALUE:
					if (!valueVisible)
					{
						continue;
					}
					label = "ITEM VALUE";
					value = c.untradeable ? "Untradeable" : String.format(java.util.Locale.ROOT, "%,d gp", c.value);
					break;
				case WIKI_COMPLETION:
					if (!wikiVisible || c.wikiCompletion == null)
					{
						continue;
					}
					label = "WIKI COMP.";
					value = String.format(java.util.Locale.ROOT, "%.2f%%", c.wikiCompletion);
					break;
				case DROP_RATE:
					label = "DROP RATE";
					value = c.dropRateText;
					break;
				default:
					break;
				}
				// Two stacked stats on each side leave the central item medallion clear.
				int cx = i % 2 == 0 ? 105 : 375, baseline = i < 2 ? 113 : 150;
				l.center(g, label, cx, baseline, 12, config.colourStatLabel(), 156);
				l.center(g, value, cx, baseline + 18, 18, config.colourStatValue(), 156);
			}
			if (config.tierEffects() && elapsed >= SLIDE_MS)
			{
				drawTierEffect(g, l, c.tier, accent, elapsed, config.showItemIcon());
			}
			if (config.showItemIcon() && elapsed >= SLIDE_MS)
			{
				double raw = Math.max(0, Math.min(1, (elapsed - SLIDE_MS) / (double)ICON_MS));
				double t = raw - 1, pop = .4 + .6 * (1 + 2.9 * t * t * t + 1.9 * t * t);
				int radius = l.p(39 * pop), cx = l.p(240), cy = l.p(171);
				g.setColor(new Color(16, 20, 28));
				g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
				g.setStroke(new BasicStroke(l.f(2)));
				g.setColor(accent);
				g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
				g.setStroke(new BasicStroke(l.f(1)));
				g.drawOval(cx - radius + l.p(5), cy - radius + l.p(5), radius * 2 - l.p(10), radius * 2 - l.p(10));
				if (icon != null)
				{
					drawItemIcon(g, icon, cx, cy, l.scale * 64 * pop, Math.max(1, radius - l.p(7) - 1));
				}
				else
				{
					l.center(g, "…", 240, 177, 24, accent, 50);
				}
			}
			// With the extra statistic the tier name sits under the medallion, outside the frame and
			// over bare scenery, so it keeps at least a shadow there whatever the outline setting.
			TextOutline outline = config.textOutline();
			l.center(g, c.tier == null ? "" : c.tier.toString(), 240, extraDropRate ? 229 : 117, 14, accent, 100,
					 extraDropRate && outline == TextOutline.NONE ? TextOutline.SHADOW : outline);
		}
		finally
		{
			g.dispose();
		}
	}
	static void drawItemIcon(Graphics2D g, BufferedImage icon, int cx, int cy, double maxSize, double safeRadius)
	{
		int left = icon.getWidth(), top = icon.getHeight(), right = -1, bottom = -1;
		for (int y = 0; y < icon.getHeight(); y++)
		{
			for (int x = 0; x < icon.getWidth(); x++)
			{
				if ((icon.getRGB(x, y) >>> 24) != 0)
				{
					left = Math.min(left, x);
					top = Math.min(top, y);
					right = Math.max(right, x);
					bottom = Math.max(bottom, y);
				}
			}
		}
		if (right < left)
		{
			return;
		}
		int width = right - left + 1, height = bottom - top + 1;
		double centerX = (left + right + 1) / 2.0, centerY = (top + bottom + 1) / 2.0;
		double extent = 0;
		for (int y = top; y <= bottom; y++)
		{
			for (int x = left; x <= right; x++)
			{
				if ((icon.getRGB(x, y) >>> 24) != 0)
				{
					// Include pixel corners so diagonal tips also fit inside the ring.
					extent = Math.max(extent, Math.hypot(Math.abs(x + .5 - centerX) + .5, Math.abs(y + .5 - centerY) + .5));
				}
			}
		}
		double scale = Math.min(maxSize / Math.max(width, height), Math.max(0, safeRadius - 1) / extent);
		if (scale >= 1)
		{
			// Every sprite pixel becomes the same square block, like the game's own icons.
			scale = Math.floor(scale);
		}
		int drawWidth = Math.max(1, (int)Math.floor(width * scale));
		int drawHeight = Math.max(1, (int)Math.floor(height * scale));
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(icon, cx - drawWidth / 2, cy - drawHeight / 2,
			cx - drawWidth / 2 + drawWidth, cy - drawHeight / 2 + drawHeight,
			left, top, right + 1, bottom + 1, null);
	}

	private static Shape frame(Layout l, int inset)
	{
		Path2D p = new Path2D.Double();
		p.moveTo(l.p(14 + inset), l.p(inset));
		p.lineTo(l.p(466 - inset), l.p(inset));
		p.lineTo(l.p(480 - inset), l.p(14 + inset));
		p.lineTo(l.p(480 - inset), l.p(162 - inset));
		p.lineTo(l.p(466 - inset), l.p(176 - inset));
		p.lineTo(l.p(14 + inset), l.p(176 - inset));
		p.lineTo(l.p(inset), l.p(162 - inset));
		p.lineTo(l.p(inset), l.p(14 + inset));
		p.closePath();
		return p;
	}
	static String heading(Celebration c)
	{
		return c.newSlot		? (c.previewTier != null ? "TEST · NEW COLLECTION LOG" : "NEW COLLECTION LOG")
		: c.previewTier != null ? "TEST"
								: "";
	}
	private static void drawUnlockEffect(Graphics2D g, Layout l, long elapsed)
	{
		double pulse = .5 + .5 * Math.sin(elapsed / 400.0);
		// Gold title ribbon with a slow shimmer.
		g.setPaint(new GradientPaint(l.p(68), 0, new Color(171, 110, 22, 85), l.p(240), 0, new Color(244, 190, 58, 115), true));
		g.fillRoundRect(l.p(68), l.p(10), l.p(344), l.p(26), l.p(8), l.p(8));
		for (int side = 0; side < 2; side++)
		{
			int x = side == 0 ? 10 : 470;
			for (int i = 0; i < 4; i++)
			{
				double phase = ((elapsed + i * 370L) % 1700) / 1700.0;
				double y = 160 - phase * 111;
				double xx = x + (side == 0 ? 1 : -1) * (4 + Math.sin(phase * Math.PI) * 10);
				star(g, l, xx, y, 2 + 3 * Math.sin(phase * Math.PI), new Color(255, 227, 151, (int)(230 * Math.sin(phase * Math.PI))));
			}
		}
		star(g, l, 56, 26, 6 + 2 * pulse, new Color(255, 223, 128));
		star(g, l, 424, 26, 6 + 2 * pulse, new Color(255, 223, 128));
	}
	private static void drawTierEffect(Graphics2D g, Layout l, PreviewTier tier, Color colour, long elapsed, boolean iconVisible)
	{
		int strength = tier == PreviewTier.PET ? 3 : tier == PreviewTier.VERY_RARE ? 2 : tier == PreviewTier.RARE ? 1 : 0;
		if (strength == 0)
		{
			return;
		}
		// This effect belongs to the rarity, so it also appears on repeats.
		if (iconVisible)
		{
			double rotation = elapsed / 850.0;
			int cx = l.p(240), cy = l.p(171), r = l.p(46);
			g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 155));
			g.setStroke(new BasicStroke(l.f(1.5)));
			for (int i = 0; i < strength + 1; i++)
			{
				double angle = rotation + i * Math.PI * 2 / (strength + 1);
				g.drawArc(cx - r, cy - r, r * 2, r * 2, (int)Math.toDegrees(-angle), 38);
				star(g, l, 240 + Math.cos(angle) * 46, 171 + Math.sin(angle) * 46, 3, colour);
			}
			if (strength > 1)
			{
				for (int i = 0; i < 12; i++)
				{
					double angle = i * Math.PI * 2 / 12 + rotation / 4;
					double ray = 49 + (strength == 3 ? 8 : 4) * (.5 + .5 * Math.sin(rotation * 2 + i));
					g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 100));
					g.drawLine(l.p(240 + Math.cos(angle) * 43), l.p(171 + Math.sin(angle) * 43), l.p(240 + Math.cos(angle) * ray),
							   l.p(171 + Math.sin(angle) * ray));
				}
			}
		}
		if (strength >= 1)
		{
			for (int i = 0; i < strength * 4; i++)
			{
				double phase = ((elapsed + i * 271L) % 2400) / 2400.0;
				double x = i % 2 == 0 ? 30 + (i % 3) * 9 : 450 - (i % 3) * 9;
				star(g, l, x, 176 - phase * 150, 1.5 + 1.5 * Math.sin(phase * Math.PI),
					 new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), (int)(170 * Math.sin(phase * Math.PI))));
			}
		}
	}
	private static void star(Graphics2D g, Layout l, double x, double y, double radius, Color colour)
	{
		int cx = l.p(x), cy = l.p(y), r = Math.max(1, l.p(radius));
		g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), colour.getAlpha() / 5));
		g.fillOval(cx - r * 2, cy - r * 2, r * 4, r * 4);
		g.setColor(colour);
		g.fillPolygon(new int[] {cx, cx + Math.max(1, r / 3), cx + r, cx + Math.max(1, r / 3), cx, cx - Math.max(1, r / 3), cx - r,
								 cx - Math.max(1, r / 3)},
					  new int[] {cy - r, cy - Math.max(1, r / 3), cy, cy + Math.max(1, r / 3), cy + r, cy + Math.max(1, r / 3), cy,
								 cy - Math.max(1, r / 3)},
					  8);
	}
	/**
	 * The RuneScape fonts draw pixel for pixel at 16px and at whole multiples of it, so popup text
	 * uses only those sizes: bold for the name, headings and values, small for the rest.
	 */
	static Font pixelFont(int size)
	{
		return size >= 14 ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont();
	}
	static int pixelMultiple(int size, double scale)
	{
		// Leans towards the larger multiple, so the item name still stands out at the default scale.
		return Math.max(1, (int)Math.round(size * scale / 16 + .2));
	}
	private static final class Layout
	{
		final double scale;
		final CelebrationConfig config;
		Layout(double scale, CelebrationConfig config)
		{
			this.scale = scale;
			this.config = config;
		}
		int p(double value)
		{
			return (int)Math.round(value * scale);
		}
		float f(double value)
		{
			// Strokes stay whole pixels wide.
			return Math.max(1, Math.round(value * scale));
		}
		void center(Graphics2D g, String value, int x, int y, int size, Color colour, int maxWidth)
		{
			center(g, value, x, y, size, colour, maxWidth, config.textOutline());
		}
		void center(Graphics2D g, String value, int x, int y, int size, Color colour, int maxWidth, TextOutline outline)
		{
			int width = p(maxWidth);
			Font base = pixelFont(size);
			int multiple = pixelMultiple(size, scale);
			g.setFont(base.deriveFont(16f * multiple));
			while (multiple > 1 && g.getFontMetrics().stringWidth(value) > width)
			{
				g.setFont(base.deriveFont(16f * --multiple));
			}
			if (g.getFontMetrics().stringWidth(value) > width)
			{
				base = FontManager.getRunescapeSmallFont();
				g.setFont(base.deriveFont(16f));
			}
			if (g.getFontMetrics().stringWidth(value) > width)
			{
				while (!value.isEmpty() && g.getFontMetrics().stringWidth(value + "…") > width)
				{
					value = value.substring(0, value.length() - 1);
				}
				value += "…";
			}
			// Centre the capitals where the design centres them, whatever size the text became.
			int capCentre = base == FontManager.getRunescapeSmallFont() ? 6 : 7;
			int baseline = (int)Math.round((y - .4375 * size) * scale) + capCentre * multiple;
			int left = p(x) - g.getFontMetrics().stringWidth(value) / 2;
			// The same shadow and four-way outline as RuneLite's own overlay text, one font pixel wide.
			g.setColor(Color.BLACK);
			if (outline == TextOutline.OUTLINE)
			{
				g.drawString(value, left, baseline - multiple);
				g.drawString(value, left, baseline + multiple);
				g.drawString(value, left - multiple, baseline);
				g.drawString(value, left + multiple, baseline);
			}
			else if (outline == TextOutline.SHADOW)
			{
				g.drawString(value, left + multiple, baseline + multiple);
			}
			g.setColor(colour);
			g.drawString(value, left, baseline);
		}
	}
}
