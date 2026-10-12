/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;
/** How the popup is drawn: smoothed, or on whole pixels like the game's interface. */
public enum PopupRendering
{
	SOFT("Soft"),
	HARD("Hard");
	private final String label;
	PopupRendering(String label)
	{
		this.label = label;
	}
	@Override
	public String toString()
	{
		return label;
	}
}
