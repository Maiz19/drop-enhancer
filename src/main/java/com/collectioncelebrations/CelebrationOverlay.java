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
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
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
	static final int WIDTH = 480, HEIGHT = 266;
	/** At 100% the popup is drawn at this share of its design size: small, with the item sprite pixel for pixel. */
	static final double BASE_SCALE = .6375;
	/** The item medallion's radius in design units: at 100% a full 36x32 sprite fits inside its inner ring. */
	static final int MEDALLION = 49;
	/** The medallion's centre: low enough to leave room between it and the extra drop rate above it. */
	static final int MEDALLION_Y = 186;
	/** The frame's bottom edge in design units, a little below the medallion's centre. */
	static final int FRAME_BOTTOM = MEDALLION_Y + 11;
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
			double scale = Math.max(90, Math.min(250, config.popupSize())) / 100.0 * BASE_SCALE;
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
			if (l.soft)
			{
				// Smooth shapes, but on the pixel grid where they can be: whole-pixel strokes on pixel edges, so
				// stretched mode enlarges far fewer half-covered pixels than plain smoothing.
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			}
			else
			{
				// Every edge lands on whole pixels, so stretched mode scales the popup up as hard pixels.
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
				g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
			}
			// Text is the same in both: smoothed, the RuneScape fonts blur and lose their shapes in stretched mode.
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
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
					g.setColor(new Color(255, 204, 92, 24));
					l.outline(g, frame, i);
				}
			}
			g.setPaint(new GradientPaint(0, 0, tint, 0, l.p(FRAME_BOTTOM), new Color(14, 17, 23)));
			g.fill(frame);
			g.setColor(accent);
			l.outline(g, frame, 2);
			g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 85));
			l.outline(g, frame(l, 5), 1);
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
				l.center(g, "DROP RATE", 240, 101, 12, config.colourStatLabel(), 104);
				l.center(g, c.dropRateText, 240, 120, 16, config.colourStatValue(), 104);
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
					label = "COLLECTED";
					// A dash until the collection log has been read once for this item.
					Integer total = c.confirmedTotal != null ? c.confirmedTotal : c.countedTotal;
					if (total != null)
					{
						value = String.valueOf(total);
					}
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
				// Spaced for the fonts' own 16px at 100%, where they are larger than the design's sizes.
				int cx = i % 2 == 0 ? 105 : 375, baseline = i < 2 ? 108 : 156;
				l.center(g, label, cx, baseline, 12, config.colourStatLabel(), 156);
				l.center(g, value, cx, baseline + 23, 18, config.colourStatValue(), 156);
			}
			double pop = iconPop(elapsed);
			if (config.tierEffects())
			{
				// From the start, as the unlock effect: the twinkles slide in with the panel, the ring grows with the medallion.
				drawTierEffect(g, l, c.tier, accent, elapsed, config.showItemIcon() ? pop : 0);
			}
			if (config.showItemIcon() && pop > 0)
			{
				int radius = l.p(MEDALLION * pop), cx = l.p(240), cy = l.p(MEDALLION_Y);
				if (l.soft)
				{
					// A halo in the rarity colour that fades out around the disc, like the stars' glow.
					float halo = radius * 1.35f;
					g.setPaint(new RadialGradientPaint(cx, cy, halo, new float[] {0, radius / halo, 1},
						new Color[] {alpha(accent, 70), alpha(accent, 70), alpha(accent, 0)}));
					g.fill(new Ellipse2D.Double(cx - halo, cy - halo, halo * 2, halo * 2));
				}
				g.setColor(new Color(16, 20, 28));
				g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
				// A soft light in the rarity colour behind the item, so dark sprites stand out from the disc.
				g.setPaint(new RadialGradientPaint(cx, cy, Math.max(1, radius), new float[] {0, 1},
					new Color[] {alpha(accent, 115), alpha(accent, 0)}));
				g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
				g.setColor(accent);
				l.outline(g, new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2), 2);
				l.outline(g, new Ellipse2D.Double(cx - radius + l.p(5), cy - radius + l.p(5), radius * 2 - l.p(10), radius * 2 - l.p(10)), 1);
				if (icon != null)
				{
					// Always at its settled size: the growing disc reveals it rather than scaling its pixels.
					drawItemIcon(g, icon, cx, cy, l.scale * 64, Math.max(1, l.p(MEDALLION) - l.p(7) - 1), Math.max(1, radius - l.p(7) - 1));
				}
				else
				{
					l.center(g, "…", 240, 177, 24, accent, 50);
				}
			}
			// With the extra statistic the tier name sits under the medallion, outside the frame and
			// over bare scenery, so it keeps at least a shadow there whatever the outline setting.
			TextOutline outline = config.textOutline();
			l.center(g, c.tier == null ? "" : c.tier.toString(), 240, extraDropRate ? MEDALLION_Y + 70 : 112, 14, accent, 100,
					 extraDropRate && outline == TextOutline.NONE ? TextOutline.SHADOW : outline);
		}
		finally
		{
			g.dispose();
		}
	}
	static void drawItemIcon(Graphics2D g, BufferedImage icon, int cx, int cy, double maxSize, double safeRadius)
	{
		drawItemIcon(g, icon, cx, cy, maxSize, safeRadius, safeRadius);
	}
	/**
	 * The sprite in whole-pixel blocks, never shrunk: a shrunk sprite loses rows and columns of pixels. What does not
	 * fit inside {@code clipRadius} is cut off instead.
	 */
	static void drawItemIcon(Graphics2D g, BufferedImage icon, int cx, int cy, double maxSize, double safeRadius, double clipRadius)
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
		// Every sprite pixel becomes the same square block, like the game's own icons.
		scale = Math.max(1, Math.floor(scale));
		int drawWidth = Math.max(1, (int)Math.floor(width * scale));
		int drawHeight = Math.max(1, (int)Math.floor(height * scale));
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		Shape clip = g.getClip();
		double r = Math.max(0, clipRadius - 1);
		g.clip(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
		g.drawImage(icon, cx - drawWidth / 2, cy - drawHeight / 2,
			cx - drawWidth / 2 + drawWidth, cy - drawHeight / 2 + drawHeight,
			left, top, right + 1, bottom + 1, null);
		g.setClip(clip);
	}
	private static Color alpha(Color colour, int alpha)
	{
		return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
	}

	private static Shape frame(Layout l, int inset)
	{
		Path2D p = new Path2D.Double();
		p.moveTo(l.p(14 + inset), l.p(inset));
		p.lineTo(l.p(466 - inset), l.p(inset));
		p.lineTo(l.p(480 - inset), l.p(14 + inset));
		p.lineTo(l.p(480 - inset), l.p(FRAME_BOTTOM - 14 - inset));
		p.lineTo(l.p(466 - inset), l.p(FRAME_BOTTOM - inset));
		p.lineTo(l.p(14 + inset), l.p(FRAME_BOTTOM - inset));
		p.lineTo(l.p(inset), l.p(FRAME_BOTTOM - 14 - inset));
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
	/** The medallion's size, popping in after the slide: 0 before it shows, 1 once settled. */
	static double iconPop(long elapsed)
	{
		if (elapsed < SLIDE_MS)
		{
			return 0;
		}
		double t = Math.max(0, Math.min(1, (elapsed - SLIDE_MS) / (double)ICON_MS)) - 1;
		return .4 + .6 * (1 + 2.9 * t * t * t + 1.9 * t * t);
	}
	/** {@code ring}: the medallion's size, or 0 while it does not show (then the ring around it does not either). */
	private static void drawTierEffect(Graphics2D g, Layout l, PreviewTier tier, Color colour, long elapsed, double ring)
	{
		int strength = tier == PreviewTier.PET ? 3 : tier == PreviewTier.VERY_RARE ? 2 : tier == PreviewTier.RARE ? 1 : 0;
		if (strength == 0)
		{
			return;
		}
		// This effect belongs to the rarity, so it also appears on repeats.
		if (ring > 0)
		{
			double rotation = elapsed / 850.0;
			int cx = l.p(240), cy = l.p(MEDALLION_Y), r = l.p((MEDALLION + 7) * ring);
			g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 155));
			for (int i = 0; i < strength + 1; i++)
			{
				double angle = rotation + i * Math.PI * 2 / (strength + 1);
				// Soft arcs turn smoothly; hard ones in whole degrees.
				double start = l.soft ? Math.toDegrees(-angle) : (int)Math.toDegrees(-angle);
				l.outline(g, new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, start, 38, Arc2D.OPEN), 1.5);
				star(g, l, 240 + Math.cos(angle) * (MEDALLION + 7) * ring, MEDALLION_Y + Math.sin(angle) * (MEDALLION + 7) * ring, 3, colour);
			}
			if (strength > 1)
			{
				for (int i = 0; i < 12; i++)
				{
					double angle = i * Math.PI * 2 / 12 + rotation / 4;
					double ray = (MEDALLION + 10 + (strength == 3 ? 8 : 4) * (.5 + .5 * Math.sin(rotation * 2 + i))) * ring;
					g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 100));
					if (l.soft)
					{
						double s = l.scale;
						g.draw(new Line2D.Double(s * (240 + Math.cos(angle) * (MEDALLION + 4) * ring), s * (MEDALLION_Y + Math.sin(angle) * (MEDALLION + 4) * ring),
							s * (240 + Math.cos(angle) * ray), s * (MEDALLION_Y + Math.sin(angle) * ray)));
					}
					else
					{
						g.drawLine(l.p(240 + Math.cos(angle) * (MEDALLION + 4) * ring), l.p(MEDALLION_Y + Math.sin(angle) * (MEDALLION + 4) * ring),
								   l.p(240 + Math.cos(angle) * ray), l.p(MEDALLION_Y + Math.sin(angle) * ray));
					}
				}
			}
		}
		// Twinkles rise along both sides, more of them the rarer the drop. Each side's are spread evenly in height,
		// the two sides in between each other, in lanes clear of the statistics.
		int perSide = strength * 4;
		for (int i = 0; i < perSide * 2; i++)
		{
			int side = i % 2, k = i / 2;
			double phase = (elapsed / 2400.0 + (k + side * .5) / perSide) % 1, fade = Math.sin(phase * Math.PI);
			double inset = 12 + (k * .618034 % 1) * 22 + Math.sin(phase * Math.PI * 2 + k) * 2;
			star(g, l, side == 0 ? inset : 480 - inset, FRAME_BOTTOM - phase * (FRAME_BOTTOM - 26), (2.8 + k % 3 * .8) * (.5 + .5 * fade),
				 new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), (int)(170 * fade)));
		}
	}
	/**
	 * A four-pointed star, pixel for pixel and the same on every side around its centre pixel, with a one-pixel glow
	 * along its edge. Filled polygons are not used: without anti-aliasing they drop their right and bottom edge
	 * pixels, which cut the star's right and bottom points short.
	 */
	private static void star(Graphics2D g, Layout l, double x, double y, double radius, Color colour)
	{
		if (l.soft)
		{
			// Around the same pixel's centre as the hard star.
			softStar(g, l.scale * x + .5, l.scale * y + .5, Math.max(1, l.scale * radius), colour);
			return;
		}
		// Rounded, so a pulsing star keeps its shape from one size to the next.
		int cx = l.p(x), cy = l.p(y), r = Math.max(1, l.p(radius)), w = Math.max(1, (int)Math.round(r / 3.0));
		Color glow = new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), colour.getAlpha() / 3);
		for (int dy = -r - 1; dy <= r + 1; dy++)
		{
			for (int dx = -r - 1; dx <= r + 1; dx++)
			{
				if (inStar(dx, dy, r, w))
				{
					g.setColor(colour);
				}
				else if (inStar(dx - 1, dy, r, w) || inStar(dx + 1, dy, r, w) || inStar(dx, dy - 1, r, w) || inStar(dx, dy + 1, r, w))
				{
					g.setColor(glow);
				}
				else
				{
					continue;
				}
				g.fillRect(cx + dx, cy + dy, 1, 1);
			}
		}
	}
	/** Tips at distance r on the axes, waist corners at (±w, ±w); the pixel at offset (dx, dy) from the centre pixel. */
	static boolean inStar(int dx, int dy, int r, int w)
	{
		int big = Math.max(Math.abs(dx), Math.abs(dy)), small = Math.min(Math.abs(dx), Math.abs(dy));
		return big <= r && small <= (r > w ? w * (r - big) / (double)(r - w) : 0);
	}
	/**
	 * A smooth star on a glow that fades out, rather than a flat disc that enlarges into a blob. It may sit between
	 * pixels, so twinkles glide instead of stepping. Leaves the paint as it was.
	 */
	private static void softStar(Graphics2D g, double x, double y, double r, Color colour)
	{
		java.awt.Paint paint = g.getPaint();
		float glow = (float)(r * 2.2);
		g.setPaint(new RadialGradientPaint((float)x, (float)y, glow, new float[] {0, 1},
			new Color[] {new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), colour.getAlpha() * 2 / 5),
				new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0)}));
		g.fill(new Ellipse2D.Double(x - glow, y - glow, glow * 2, glow * 2));
		double w = r / 3;
		Path2D p = new Path2D.Double();
		p.moveTo(x, y - r);
		p.lineTo(x + w, y - w);
		p.lineTo(x + r, y);
		p.lineTo(x + w, y + w);
		p.lineTo(x, y + r);
		p.lineTo(x - w, y + w);
		p.lineTo(x - r, y);
		p.lineTo(x - w, y - w);
		p.closePath();
		g.setColor(colour);
		g.fill(p);
		g.setPaint(paint);
	}
	/**
	 * The RuneScape fonts draw pixel for pixel at 16px and at whole multiples of it, so popup text
	 * uses only those sizes: bold for the item name, the smaller font for everything else.
	 */
	static Font pixelFont(int size)
	{
		return size >= 20 ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont();
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
		final boolean soft;
		Layout(double scale, CelebrationConfig config)
		{
			this.scale = scale;
			this.config = config;
			soft = config.popupRendering() == PopupRendering.SOFT;
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
		/**
		 * A whole-pixel-wide outline. A smooth one of odd width moves half a pixel, so its straight edges cover whole
		 * pixels instead of two half ones.
		 */
		void outline(Graphics2D g, Shape shape, double width)
		{
			float w = f(width);
			g.setStroke(new BasicStroke(w));
			g.draw(soft && w % 2 == 1 ? AffineTransform.getTranslateInstance(.5, .5).createTransformedShape(shape) : shape);
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
