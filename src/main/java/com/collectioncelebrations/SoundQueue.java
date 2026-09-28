/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.util.Filepath;
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
	// A device that stalls this often is given up on rather than holding a thread per sound.
	private static final int MAX_STALLS = 3;
	private int stalls;
	// Recent rewards that have had their sound; any later drop sound for one of them is dropped.
	private final Deque<Long> spokenGroups = new ArrayDeque<>();

	long monotonicMillis()
	{
		return System.nanoTime() / 1000000;
	}

	boolean busy()
	{
		if (worker != null && pendingAudio.get() > 0 && monotonicMillis() - openingSince > 5000)
		{
			// Abandon the stalled sound and carry on with a fresh worker.
			generation++;
			previewGeneration++;
			worker.shutdownNow();
			worker = null;
			if (++stalls < MAX_STALLS)
			{
				startWorker();
			}
			else
			{
				log.warn("Audio device keeps stalling; Drop Enhancer audio is off until the plugin restarts");
			}
		}
		long now = monotonicMillis();
		playingUntil.removeIf(deadline -> now >= deadline);
		return worker != null && (pendingAudio.get() > 0 || !playingUntil.isEmpty());
	}
	/** The plugin directory's sounds folder, where users put their own WAVs; set by the plugin before start. */
	Filepath soundDirectory;
	private static final class Request
	{
		final String name;
		final int volume;
		final long due;
		final boolean reward;
		String valueItemName;
		long valueGroup = -1;
		int priority;
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
		stalls = 0;
		startWorker();
	}

	private void startWorker()
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
		spokenGroups.clear();
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
	private Filepath file(String name)
	{
		return soundDirectory.joinSegment(name.trim());
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
	/** @param priority the sound's tier ordinal; a reward's items play only their highest one */
	void offerValue(String name, int volume, String itemName, long group, int priority)
	{
		if (volume <= 0 || (group >= 0 && spokenGroups.contains(group)))
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
		request.priority = priority;
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
		if (group < 0)
		{
			return;
		}
		if (!spokenGroups.contains(group))
		{
			if (spokenGroups.size() == 32)
			{
				spokenGroups.removeFirst();
			}
			spokenGroups.addLast(group);
		}
		waiting.removeIf(r -> r.valueItemName != null && r.valueGroup == group);
	}
	void tick(long now)
	{
		for (Request r : waiting)
		{
			if (!busy() && now >= r.due && (!r.reward || !gate.blocked()))
			{
				Request chosen = r;
				if (r.valueItemName != null && r.valueGroup >= 0)
				{
					// One drop sound per reward: its most notable item speaks for the rest.
					for (Request other : waiting)
					{
						if (other.valueItemName != null && other.valueGroup == r.valueGroup && other.priority > chosen.priority)
						{
							chosen = other;
						}
					}
					cancelValueGroup(r.valueGroup);
				}
				else
				{
					waiting.remove(r);
				}
				playNow(chosen.name, chosen.volume);
				return;
			}
		}
	}
	void cancelPreview()
	{
		// AudioPlayer owns started audio and exposes no stop handle. Cancel pending work only.
		previewGeneration++;
	}

	WavData load(Filepath file) throws Exception
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
			// Timed per sound, so several healthy sounds in a row never look like one stalled sound.
			openingSince = monotonicMillis();
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
