/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.util.Map;
import java.util.TreeMap;

/**
 * One account's collection log quantities: the last reading of the log, plus the drops counted since. Each new reading
 * replaces the count, so a drop the plugin did not see is corrected the next time the log is opened.
 */
final class CollectionLedger
{
	private final Map<Integer, Integer> items = new TreeMap<>();
	private boolean changed;

	/** An official quantity, read from the collection log. */
	void observe(int itemId, int quantity)
	{
		if (itemId >= 0 && quantity >= 0)
		{
			put(itemId, quantity);
		}
	}

	/**
	 * Counts a received drop on top of the last reading.
	 *
	 * @return the new count, or null while the log has never been read for this item
	 */
	Integer add(int itemId, int quantity)
	{
		Integer count = items.get(itemId);
		if (count == null || quantity <= 0)
		{
			return count;
		}
		int total = (int)Math.min(Integer.MAX_VALUE, (long)count + quantity);
		put(itemId, total);
		return total;
	}

	private void put(int itemId, int quantity)
	{
		Integer old = items.put(itemId, quantity);
		changed |= old == null || old != quantity;
	}

	Integer get(int itemId)
	{
		return items.get(itemId);
	}

	boolean obtained(int itemId)
	{
		Integer count = get(itemId);
		return count != null && count > 0;
	}

	void clear()
	{
		items.clear();
		changed = false;
	}

	/** Replaces the counts with a saved form from {@link #changes()}; anything unreadable is skipped. */
	void load(String saved)
	{
		clear();
		if (saved == null)
		{
			return;
		}
		for (String pair : saved.split(","))
		{
			int colon = pair.indexOf(':');
			try
			{
				int itemId = Integer.parseInt(pair.substring(0, Math.max(0, colon)));
				int quantity = Integer.parseInt(pair.substring(colon + 1));
				if (itemId >= 0 && quantity >= 0)
				{
					items.put(itemId, quantity);
				}
			}
			catch (NumberFormatException ignored)
			{
				// Left out, as if never read.
			}
		}
	}

	/** @return the counts as {@code id:quantity} pairs when they changed since the last call or load, else null */
	String changes()
	{
		if (!changed)
		{
			return null;
		}
		changed = false;
		StringBuilder saved = new StringBuilder(items.size() * 10);
		for (Map.Entry<Integer, Integer> item : items.entrySet())
		{
			if (saved.length() > 0)
			{
				saved.append(',');
			}
			saved.append(item.getKey()).append(':').append(item.getValue());
		}
		return saved.toString();
	}
}
