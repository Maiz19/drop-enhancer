/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;
/** The dark edge behind popup text, in the two styles RuneLite's own overlays use. */
public enum TextOutline
{
	NONE("None"),
	SHADOW("Shadow"),
	OUTLINE("Outline");
	private final String label;
	TextOutline(String label)
	{
		this.label = label;
	}
	@Override
	public String toString()
	{
		return label;
	}
}
