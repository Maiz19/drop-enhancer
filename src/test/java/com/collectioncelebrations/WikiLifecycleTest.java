package com.collectioncelebrations;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

public class WikiLifecycleTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private WikiRarity create() throws Exception
	{
		WikiRarity wiki = new WikiRarity();
		wiki.dataDirectory = folder.newFolder().toPath();
		wiki.gson = new Gson();
		wiki.config = mock(CelebrationConfig.class, CALLS_REAL_METHODS);
		wiki.remote = new WikiCompletionClient();
		wiki.remote.gson = wiki.gson;
		wiki.resolver = mock(RarityResolver.class);
		wiki.dropRates = new DropRateResolver();
		return wiki;
	}

	/** Only the first page carries rows; the empty page after it is what ends the paging loop. */
	private static String bucket(okhttp3.Request request, com.google.gson.JsonObject... rows)
	{
		String query = request.url().queryParameter("query");
		if (query == null || !query.contains(".offset(0)."))
		{
			return "{\"bucket\":[]}";
		}
		StringBuilder body = new StringBuilder("{\"bucket\":[");
		for (int i = 0; i < rows.length; i++)
		{
			body.append(i > 0 ? "," : "").append(rows[i]);
		}
		return body.append("]}").toString();
	}

	private okhttp3.Response response(okhttp3.Interceptor.Chain chain)
	{
		String body = chain.request().url().encodedPath().endsWith("data.json") ? "[{\"id\":1,\"name\":\"Example\"}]" : "{\"1\":50}";
		if (chain.request().url().encodedPath().equals("/api.php"))
		{
			body = bucket(chain.request(), WikiDropRatesTest.row("Boss", "Example", "1/512"));
		}
		return new okhttp3.Response.Builder()
			.request(chain.request())
			.protocol(okhttp3.Protocol.HTTP_1_1)
			.code(200)
			.message("OK")
			.body(okhttp3.ResponseBody.create(okhttp3.MediaType.parse("application/json"), body))
			.build();
	}

	private void awaitCache(WikiRarity wiki) throws Exception
	{
		java.nio.file.Path cache = wiki.dataDirectory.resolve("wiki-drop-rates-cache.json");
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!java.nio.file.Files.exists(cache) && System.nanoTime() < deadline) { Thread.sleep(10); }
		assertTrue("Drop-rate cache was not written", java.nio.file.Files.exists(cache));
	}

	private static long at(String instant)
	{
		return java.time.Instant.parse(instant).toEpochMilli();
	}

	@Test
	public void aCacheGoesStaleOnTheThursdayAfterTheGameUpdate()
	{
		// 2026-09-17 is a Thursday, 09-16 the Wednesday update before it, 09-24 the Thursday after.
		long friday = at("2026-09-18T12:00:00Z");
		assertFalse("Fetched that Thursday, still current on Friday", WikiRarity.staleSince(at("2026-09-17T09:00:00Z"), friday));
		assertTrue("Fetched before the Thursday cutoff", WikiRarity.staleSince(at("2026-09-16T23:59:59Z"), friday));
		// Anything from the previous week is stale once the next Thursday starts.
		assertFalse(WikiRarity.staleSince(at("2026-09-18T12:00:00Z"), at("2026-09-23T23:59:59Z")));
		assertTrue(WikiRarity.staleSince(at("2026-09-18T12:00:00Z"), at("2026-09-24T00:00:00Z")));
		// A cache is never kept for longer than a week, and a broken timestamp always refreshes.
		assertTrue(WikiRarity.staleSince(at("2026-09-17T00:00:00Z"), at("2026-09-24T00:00:00Z")));
		assertTrue(WikiRarity.staleSince(0, friday));
		assertTrue("A timestamp from the future cannot be trusted", WikiRarity.staleSince(at("2027-01-01T00:00:00Z"), friday));
	}

	@Test
	public void aCacheFromAnOlderFormatIsReplacedEvenWhenItIsFresh() throws Exception
	{
		WikiRarity wiki = create();
		wiki.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(this::response).build();
		when(wiki.config.refreshWikiData()).thenReturn(true);
		java.nio.file.Files.createDirectories(wiki.dataDirectory);
		java.nio.file.Path cache = wiki.dataDirectory.resolve("wiki-drop-rates-cache.json");
		// Written today, but by a build whose rates were formatted differently.
		java.nio.file.Files.writeString(cache, "{\"fetchedAt\":" + System.currentTimeMillis()
			+ ",\"rates\":{\"Boss\":{\"Example\":\"1/9,999\"}}}");
		try
		{
			wiki.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (!"1/512".equals(wiki.dropRateText("Boss", "Example")) && System.nanoTime() < deadline) { Thread.sleep(10); }
			assertEquals("A formatless cache must be refetched, not displayed", "1/512", wiki.dropRateText("Boss", "Example"));
			assertTrue(java.nio.file.Files.readString(cache).contains("\"format\""));
		}
		finally { wiki.stop(); }
	}

	@Test
	public void localRatesStillResolveByNameWhileWikiTablesAreAbsent() throws Exception
	{
		WikiRarity wiki = create();
		wiki.dropRates.reload(java.util.Map.of("Boss variant",
			java.util.Map.of("Example", 0.002, "Rare example", 0.00005)));
		// Nothing downloaded yet, so the local dataset is the only source and must not be withheld.
		assertEquals("1/500", wiki.dropRateText("Some other boss", "Example"));
		assertEquals("1/500", wiki.dropRateText(null, "Example"));
		// Local rates follow the same separator threshold as the downloaded ones.
		assertEquals("1/20,000", wiki.dropRateText(null, "Rare example"));
	}

	@Test
	public void flickingTheSwitchCannotRepeatAFullDownload() throws Exception
	{
		WikiRarity wiki = create();
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		wiki.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
			calls.incrementAndGet();
			return response(chain);
		}).build();
		when(wiki.config.refreshWikiData()).thenReturn(true);
		try
		{
			wiki.start();
			awaitCache(wiki);
			int afterStart = calls.get();
			wiki.refreshSetting();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (calls.get() == afterStart && System.nanoTime() < deadline) { Thread.sleep(10); }
			int afterForced = calls.get();
			assertTrue("The first toggle must still refresh", afterForced > afterStart);

			// A second toggle inside the interval falls back to the caches it just wrote.
			wiki.refreshSetting();
			Thread.sleep(300);
			assertEquals("A repeated toggle must not download again", afterForced, calls.get());
		}
		finally { wiki.stop(); }
	}

	@Test
	public void onlyASourceTheWikiListsWithholdsTheLocalFallback() throws Exception
	{
		WikiRarity wiki = create();
		wiki.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(this::response).build();
		when(wiki.config.refreshWikiData()).thenReturn(true);
		// The local dataset files both items under a source name the wiki tables never mention.
		wiki.dropRates.reload(java.util.Map.of("Boss variant",
			java.util.Map.of("Example", 0.002, "Unlisted", 0.004)));
		try
		{
			wiki.start();
			awaitCache(wiki);
			assertEquals("1/512", wiki.dropRateText("Boss", "Example"));
			// The wiki lists "Boss" and does not give it this item, so that absence is authoritative.
			assertNull("A listed source must not borrow a rate by name", wiki.dropRateText("Boss", "Unlisted"));
			// "Other boss" appears nowhere in the tables, so they say nothing and local data stands.
			assertEquals("1/500", wiki.dropRateText("Other boss", "Example"));
		}
		finally { wiki.stop(); }
	}

	@Test
	public void refreshUpdatesCompletionAndDropsEvenWhenBothCachesAreFresh() throws Exception
	{
		WikiRarity wiki = create();
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicBoolean updated = new java.util.concurrent.atomic.AtomicBoolean();
		wiki.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
			calls.incrementAndGet();
			if (updated.get() && chain.request().url().encodedPath().equals("/api.php"))
			{
				return WikiDropRatesTest.response(chain.request(), bucket(chain.request(), WikiDropRatesTest.row("Boss", "Example", "1/256")));
			}
			if (updated.get() && chain.request().url().encodedPath().endsWith("completion.json"))
			{
				return WikiDropRatesTest.response(chain.request(), "{\"1\":25}");
			}
			return response(chain);
		}).build();
		when(wiki.config.refreshWikiData()).thenReturn(true);
		try
		{
			wiki.start();
			awaitCache(wiki);
			assertEquals("1/512", wiki.dropRateText("Boss", "Example"));
			assertEquals(Double.valueOf(50), wiki.entry(1, "Example").completion);
			// Two item/completion files plus the drop pages: one with rows, one empty to end the loop.
			assertEquals(4, calls.get());
			updated.set(true);
			wiki.refreshSetting();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (!"1/256".equals(wiki.dropRateText("Boss", "Example")) && System.nanoTime() < deadline) { Thread.sleep(10); }
			assertEquals("Explicit refresh must bypass both weekly caches", 8, calls.get());
			assertEquals("1/256", wiki.dropRateText("Boss", "Example"));
			assertEquals(Double.valueOf(25), wiki.entry(1, "Example").completion);
		}
		finally { wiki.stop(); }
	}

	@Test
	public void freshCachesWorkAfterRestartWithoutAnyNetworkRequests() throws Exception
	{
		WikiRarity first = create();
		first.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(this::response).build();
		when(first.config.refreshWikiData()).thenReturn(true);
		try { first.start(); awaitCache(first); }
		finally { first.stop(); }
		WikiRarity second = create();
		second.dataDirectory = first.dataDirectory;
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		second.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
			calls.incrementAndGet();
			throw new java.io.IOException("Offline");
		}).build();
		when(second.config.refreshWikiData()).thenReturn(true);
		try
		{
			second.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (second.dropRateText("Boss", "Example") == null && System.nanoTime() < deadline) { Thread.sleep(10); }
			assertEquals("1/512", second.dropRateText("Boss", "Example"));
			assertEquals(Double.valueOf(50), second.entry(1, "Example").completion);
			assertEquals(0, calls.get());
		}
		finally { second.stop(); }
	}

	@Test
	public void downloadedDataStaysInUseWithRefreshingSwitchedOff() throws Exception
	{
		WikiRarity first = create();
		first.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(this::response).build();
		when(first.config.refreshWikiData()).thenReturn(true);
		try { first.start(); awaitCache(first); }
		finally { first.stop(); }
		// Even a cache from before the weekly cutoff is better than no data at all.
		java.nio.file.Path completion = first.dataDirectory.resolve("wiki-completion-cache.json");
		java.nio.file.Files.writeString(completion,
			java.nio.file.Files.readString(completion).replaceFirst("\"fetchedAt\":\\d+", "\"fetchedAt\":1"));
		assertTrue(java.nio.file.Files.readString(completion).contains("\"fetchedAt\":1,"));
		WikiRarity second = create();
		second.dataDirectory = first.dataDirectory;
		java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
		second.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
			calls.incrementAndGet();
			throw new java.io.IOException("Offline");
		}).build();
		when(second.config.refreshWikiData()).thenReturn(false);
		try
		{
			second.start();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while ((second.dropRateText("Boss", "Example") == null || second.entry(1, "Example") == null) && System.nanoTime() < deadline)
			{
				Thread.sleep(10);
			}
			assertEquals("1/512", second.dropRateText("Boss", "Example"));
			assertEquals(Double.valueOf(50), second.entry(1, "Example").completion);
			assertEquals(0, calls.get());
		}
		finally { second.stop(); }
	}

	@Test
	public void disablingDuringDropDownloadCancelsWithoutReplacingCachedRates() throws Exception
	{
		WikiRarity wiki = create();
		CountDownLatch entered = new CountDownLatch(1);
		AtomicReference<Thread> worker = new AtomicReference<>();
		AtomicReference<okhttp3.Call> request = new AtomicReference<>();
		java.util.concurrent.atomic.AtomicBoolean block = new java.util.concurrent.atomic.AtomicBoolean();
		wiki.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
			if (block.get() && chain.request().url().encodedPath().equals("/api.php"))
			{
				worker.set(Thread.currentThread());
				request.set(chain.call());
				entered.countDown();
				try { new CountDownLatch(1).await(5, TimeUnit.SECONDS); }
				catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
				return WikiDropRatesTest.response(chain.request(), bucket(chain.request(), WikiDropRatesTest.row("Boss", "Example", "1/256")));
			}
			return response(chain);
		}).build();
		when(wiki.config.refreshWikiData()).thenReturn(true);
		try
		{
			wiki.start();
			awaitCache(wiki);
			java.nio.file.Path cache = wiki.dataDirectory.resolve("wiki-drop-rates-cache.json");
			String original = java.nio.file.Files.readString(cache);
			block.set(true);
			wiki.refreshSetting();
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			when(wiki.config.refreshWikiData()).thenReturn(false);
			wiki.refreshSetting();
			worker.get().join(3000);
			assertFalse(worker.get().isAlive());
			assertTrue(request.get().isCanceled());
			assertEquals("1/512", wiki.dropRateText("Boss", "Example"));
			assertEquals(original, java.nio.file.Files.readString(cache));
		}
		finally { wiki.stop(); }
	}

	@Test
	public void enablingLoadsDataWithoutPluginRestart() throws Exception
	{
		WikiRarity wiki = create();
		wiki.remote.http = new okhttp3.OkHttpClient.Builder().addInterceptor(this::response).build();
		try
		{
			wiki.start();
			when(wiki.config.refreshWikiData()).thenReturn(true);
			wiki.refreshSetting();
			verify(wiki.resolver, timeout(2000).atLeastOnce()).reload(anyMap());
		}
		finally
		{
			wiki.stop();
		}
	}

	@Test
	public void disablingCancelsAndRejectsLateNetworkResult() throws Exception
	{
		WikiRarity wiki = create();
		CountDownLatch entered = new CountDownLatch(1);
		AtomicReference<Thread> worker = new AtomicReference<>();
		when(wiki.config.refreshWikiData()).thenReturn(true);
		AtomicReference<okhttp3.Call> request = new AtomicReference<>();
		wiki.remote.http = new okhttp3.OkHttpClient.Builder()
							   .addInterceptor(chain -> {
								   request.set(chain.call());
								   worker.set(Thread.currentThread());
								   entered.countDown();
								   try
								   {
									   new CountDownLatch(1).await(2, TimeUnit.SECONDS);
								   }
								   catch (InterruptedException expected)
								   {
									   Thread.currentThread().interrupt();
								   }
								   return response(chain);
							   })
							   .build();
		try
		{
			wiki.start();
			assertTrue(entered.await(2, TimeUnit.SECONDS));
			when(wiki.config.refreshWikiData()).thenReturn(false);
			wiki.refreshSetting();
			worker.get().join(2000);
			assertFalse(worker.get().isAlive());
			assertTrue(request.get().isCanceled());
			verify(wiki.resolver, never()).reload(anyMap());
			assertNull(wiki.entry(1, "Example"));
		}
		finally
		{
			wiki.stop();
		}
	}
}
