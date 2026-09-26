/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.RuneLite;
import net.runelite.client.audio.AudioPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class SoundQueue
{
	private static final Logger log = LoggerFactory.getLogger(SoundQueue.class);
	@Inject
	CelebrationConfig config;
	@Inject
	CaseGate gate;
	@Inject
	AudioPlayer audioPlayer;
	private final List<Request> waiting = new ArrayList<>();
	private final List<Long> playingUntil = new CopyOnWriteArrayList<>();
	private ExecutorService worker;
	private volatile int generation;
	private volatile int previewGeneration;
	private volatile long openingSince;
	private AtomicInteger pendingAudio = new AtomicInteger();

	long monotonicMillis()
	{
		return System.nanoTime() / 1000000;
	}

	boolean busy()
	{
		if (worker != null && pendingAudio.get() > 0 && monotonicMillis() - openingSince > 5000)
		{
			generation++;
			previewGeneration++;
			worker.shutdownNow();
			worker = null;
			log.debug("Audio device did not respond; audio suspended until plugin restart");
		}
		long now = monotonicMillis();
		playingUntil.removeIf(deadline -> now >= deadline);
		return worker != null && (pendingAudio.get() > 0 || !playingUntil.isEmpty());
	}
	File runeliteDirectory = RuneLite.RUNELITE_DIR;
	private static final class Request
	{
		final String name;
		final int volume;
		final long due;
		final boolean reward;
		String valueItemName;
		long valueGroup = -1;
		Request(String name, int volume, long due, boolean reward)
		{
			this.name = name;
			this.volume = volume;
			this.due = due;
			this.reward = reward;
		}
	}
	void start()
	{
		pendingAudio = new AtomicInteger();
		worker = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "collection-celebrations-audio");
			t.setDaemon(true);
			return t;
		});
	}

	void reset()
	{
		generation++;
		waiting.clear();
		cancelPreview();
	}

	void stop()
	{
		reset();
		if (worker != null)
		{
			worker.shutdownNow();
			worker = null;
		}
	}
	static boolean validName(String name)
	{
		return name != null && name.trim().matches("[A-Za-z0-9_ .()-]+\\.wav") && !name.contains("..");
	}
	private File file(String name)
	{
		return new File(new File(runeliteDirectory, "collection-celebrations/sounds"), name.trim());
	}
	void offer(String name, int volume, long delay, boolean reward)
	{
		if (volume <= 0)
		{
			return;
		}
		if (waiting.size() == 128)
		{
			waiting.remove(0);
		}
		waiting.add(new Request(name, volume, System.currentTimeMillis() + Math.max(delay, reward ? gate.delayMillis() : 0), reward));
	}
	void offerValue(String name, int volume, String itemName, long group)
	{
		if (volume <= 0)
		{
			return;
		}
		if (waiting.size() == 128)
		{
			waiting.remove(0);
		}
		// Item identity is already known. Release at the next render callback; cases still gate playback.
		Request request = new Request(name, volume, System.currentTimeMillis(), true);
		request.valueItemName = itemName.toLowerCase(java.util.Locale.ROOT);
		request.valueGroup = group;
		waiting.add(request);
	}
	void cancelValue(String itemName)
	{
		String key = itemName.toLowerCase(java.util.Locale.ROOT);
		waiting.removeIf(r -> key.equals(r.valueItemName));
	}
	/** A reward whose popup already played its sound should not follow it with value sounds. */
	void cancelValueGroup(long group)
	{
		waiting.removeIf(r -> r.valueItemName != null && r.valueGroup >= 0 && r.valueGroup == group);
	}
	void tick(long now)
	{
		Iterator<Request> it = waiting.iterator();
		while (it.hasNext())
		{
			Request r = it.next();
			if (!busy() && now >= r.due && (!r.reward || !gate.blocked()))
			{
				it.remove();
				playNow(r.name, r.volume);
				break;
			}
		}
	}
	void cancelPreview()
	{
		// AudioPlayer owns started audio and exposes no stop handle. Cancel pending work only.
		previewGeneration++;
	}

	WavData load(File file) throws Exception
	{
		return SoundResources.load(file);
	}

	void playPreview(String name, int volume)
	{
		playPreview(name, volume, null, 0);
	}

	void playPreview(String name, int volume, String unlockName, int unlockVolume)
	{
		submit(name, volume, unlockName, unlockVolume, true);
	}

	void playNow(String name, int volume)
	{
		submit(name, volume, null, 0, false);
	}

	private void submit(String name, int volume, String layer, int layerVolume, boolean preview)
	{
		if (worker == null || ((volume <= 0 || !validName(name)) && (layerVolume <= 0 || !validName(layer))))
		{
			return;
		}
		int session = generation;
		int token = previewGeneration;
		AtomicInteger counter = pendingAudio;
		if (counter.getAndIncrement() == 0)
		{
			openingSince = monotonicMillis();
		}
		worker.execute(() -> {
			try
			{
				playLayer(name, volume, session, token, preview);
				playLayer(layer, layerVolume, session, token, preview);
			}
			finally
			{
				counter.decrementAndGet();
			}
		});
	}

	private void playLayer(String name, int volume, int session, int token, boolean preview)
	{
		if (session != generation || (preview && token != previewGeneration) || volume <= 0 || !validName(name))
		{
			return;
		}
		try
		{
			WavData wav = load(file(name));
			if (session != generation || (preview && token != previewGeneration))
			{
				return;
			}
			float gain = 20f * (float)Math.log10(Math.min(100, volume) / 100f);
			try (ByteArrayInputStream input = new ByteArrayInputStream(wav.bytes))
			{
				audioPlayer.play(input, gain);
			}
			// AudioPlayer has no completion callback. Reserve the measured WAV duration.
			playingUntil.add(monotonicMillis() + wav.durationMillis + 50);
		}
		catch (Exception failure)
		{
			log.debug("Unable to play sound {}", name, failure);
		}
	}
}
