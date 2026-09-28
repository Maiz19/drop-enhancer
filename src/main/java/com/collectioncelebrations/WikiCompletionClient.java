/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Public population statistics only; never sends a player name or collection contents. */
final class WikiCompletionClient
{
	private static final String ROOT = "https://oldschool.runescape.wiki/w/Module:Collection_log/";
	private static final int MAX_BYTES = 2 * 1024 * 1024;
	/** A page must stay well inside MAX_BYTES; a thousand drop lines is roughly a quarter of it. */
	private static final int PAGE = 1000, MAX_ROWS = 120000;
	@Inject
	OkHttpClient http;
	@Inject
	Gson gson;
	private volatile Call active;
	private volatile boolean cancelled;
	private volatile OkHttpClient timed;

	/** Shares the client's connection pool; only the call timeout differs. */
	private OkHttpClient client()
	{
		OkHttpClient cached = timed;
		if (cached == null)
		{
			cached = http.newBuilder().callTimeout(15, TimeUnit.SECONDS).build();
			timed = cached;
		}
		return cached;
	}

	synchronized void cancel()
	{
		// A flag, not just a call cancel: the worker may be between pages, and OkHttp starts a fresh
		// call regardless of the thread's interrupt status.
		cancelled = true;
		Call call = active;
		if (call != null)
		{
			call.cancel();
		}
	}

	/** Clears a previous cancel so a newly started session may issue requests again. */
	synchronized void resume()
	{
		cancelled = false;
	}

	JsonObject fetch() throws IOException
	{
		JsonArray items = get("data.json").getAsJsonArray();
		JsonObject completion = get("completion.json").getAsJsonObject();
		return merge(items, completion);
	}

	WikiDropRates fetchDropRates() throws IOException
	{
		WikiDropRates rates = new WikiDropRates();
		for (int offset = 0; offset < MAX_ROWS; )
		{
			// Ordering by the row's own JSON keeps offsets stable: page_name_sub repeats across drop
			// lines, so ties at a page boundary could silently drop rows. Identical drop_json means an
			// identical rate, so the ties that remain cannot change the result.
			String query = "bucket('dropsline').select('page_name_sub','item_name','drop_json')"
				+ ".orderBy('drop_json','asc').limit(" + PAGE + ").offset(" + offset + ").run()";
			HttpUrl url = HttpUrl.parse("https://oldschool.runescape.wiki/api.php").newBuilder()
				.addQueryParameter("action", "bucket").addQueryParameter("format", "json")
				.addQueryParameter("formatversion", "2").addQueryParameter("query", query).build();
			JsonObject response = get(url).getAsJsonObject();
			if (response.has("error") || !response.has("bucket") || !response.get("bucket").isJsonArray())
			{
				throw new IOException("Wiki drop query failed");
			}
			JsonArray rows = response.getAsJsonArray("bucket");
			if (rows.size() > PAGE)
			{
				throw new IOException("Invalid wiki drop pagination");
			}
			if (rows.size() == 0)
			{
				break;
			}
			rates.addPage(rows, gson);
			// Advance by what arrived, not by what was asked: a server-side cap on limit would
			// otherwise look like the end of the data and truncate the dataset silently.
			offset += rows.size();
		}
		// Reaching the row limit keeps what was read; only an empty result discards the working cache.
		if (rates.isEmpty())
		{
			throw new IOException("Empty wiki drop data");
		}
		return rates;
	}

	private JsonElement get(String page) throws IOException
	{
		return get(HttpUrl.parse(ROOT + page + "?action=raw"));
	}

	private JsonElement get(HttpUrl url) throws IOException
	{
		if (cancelled)
		{
			throw new IOException("Refresh cancelled");
		}
		Request request = new Request.Builder()
							  .url(url)
							  .header("User-Agent", "DropEnhancer/0.1 (RuneLite plugin; +https://github.com/Quentin242/drop-enhancer)")
							  .header("Accept", "application/json")
							  .build();
		Call call = client().newCall(request);
		synchronized (this)
		{
			if (cancelled)
			{
				throw new IOException("Refresh cancelled");
			}
			active = call;
		}
		try (Response response = call.execute())
		{
			ResponseBody body = response.body();
			if (!response.isSuccessful() || body == null)
			{
				throw new IOException("Wiki HTTP " + response.code());
			}
			// contentLength() is -1 for a chunked response, so the read below is the real bound.
			byte[] bytes = body.byteStream().readNBytes(MAX_BYTES + 1);
			if (bytes.length > MAX_BYTES)
			{
				throw new IOException("Wiki response too large");
			}
			return gson.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonElement.class);
		}
		finally
		{
			synchronized (this)
			{
				if (active == call)
				{
					active = null;
				}
			}
		}
	}

	static JsonObject merge(JsonArray items, JsonObject completion) throws IOException
	{
		JsonObject result = new JsonObject();
		for (JsonElement element : items)
		{
			JsonObject item = element.getAsJsonObject();
			if (!item.has("id") || !item.has("name") || item.get("name").isJsonNull())
			{
				continue;
			}
			int id = item.get("id").getAsInt();
			if (id < 0)
			{
				continue;
			}
			JsonObject row = new JsonObject();
			row.addProperty("name", item.get("name").getAsString());
			row.add("tabs", item.has("tabs") ? item.get("tabs") : new JsonArray());
			JsonElement percent = completion.get(String.valueOf(id));
			if (percent != null && !percent.isJsonNull())
			{
				double value = percent.getAsDouble();
				if (!Double.isFinite(value) || value < 0 || value > 100)
				{
					throw new IOException("Invalid wiki completion percentage");
				}
				row.addProperty("comp", value);
			}
			result.add(String.valueOf(id), row);
		}
		if (result.size() == 0)
		{
			throw new IOException("Empty wiki item list");
		}
		return result;
	}
}
