/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.runelite.client.audio.AudioPlayer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SoundQueueTest
{
	@Rule public TemporaryFolder temporary = new TemporaryFolder();
	private SoundQueue sounds;
	private AudioPlayer audio;

	@Before
	public void setup() throws Exception
	{
		sounds = spy(new SoundQueue());
		audio = mock(AudioPlayer.class);
		sounds.audioPlayer = audio;
		sounds.gate = mock(CaseGate.class);
		sounds.runeliteDirectory = temporary.getRoot();
		java.nio.file.Path folder = temporary.getRoot().toPath().resolve("collection-celebrations/sounds");
		Files.createDirectories(folder);
		try (InputStream input = getClass().getResourceAsStream("/sounds/custom-sounds-common.wav"))
		{
			Files.copy(input, folder.resolve("test.wav"));
		}
		doReturn(1000L).when(sounds).monotonicMillis();
		sounds.start();
	}

	@After public void cleanup() { sounds.stop(); }

	private void await(BooleanSupplier condition)
	{
		long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
		while (!condition.getAsBoolean() && System.nanoTime() < until) { Thread.yield(); }
		assertTrue(condition.getAsBoolean());
	}

	@Test
	public void rewardSoundWaitsAndUsesRuneLiteGainOnce() throws Exception
	{
		when(sounds.gate.blocked()).thenReturn(true);
		sounds.offer("test.wav", 50, 0, true);
		sounds.tick(Long.MAX_VALUE);
		verifyNoInteractions(audio);
		when(sounds.gate.blocked()).thenReturn(false);
		sounds.tick(Long.MAX_VALUE);
		sounds.tick(Long.MAX_VALUE);
		verify(audio, timeout(1000).times(1)).play(any(InputStream.class), eq(20f * (float)Math.log10(.5)));
	}

	@Test
	public void resetDropsQueuedAudio()
	{
		sounds.offer("test.wav", 80, 10000, true);
		sounds.reset();
		sounds.tick(Long.MAX_VALUE);
		verifyNoInteractions(audio);
	}

	@Test
	public void mutedAndInvalidNamesNeverReachAudioPlayer()
	{
		sounds.playNow("test.wav", 0);
		sounds.playPreview("../test.wav", 50);
		verifyNoInteractions(audio);
	}

	@Test
	public void lateUnlockCancelsOnlyMatchingValueSound() throws Exception
	{
		sounds.offerValue("test.wav", 50, "Mole claw", -1);
		sounds.offerValue("test.wav", 50, "Coins", -1);
		sounds.cancelValue("MOLE CLAW");
		sounds.tick(Long.MAX_VALUE);
		verify(audio, timeout(1000).times(1)).play(any(InputStream.class), anyFloat());
	}

	@Test
	public void rewardPopupCancelsOnlyItsOwnValueSounds() throws Exception
	{
		sounds.offerValue("test.wav", 50, "Soul rune", 7);
		sounds.offerValue("test.wav", 50, "Coins", 8);
		sounds.offerValue("test.wav", 50, "Coins", -1);
		sounds.cancelValueGroup(7);
		sounds.cancelValueGroup(-1);
		for (long clock = 100_000; clock <= 400_000; clock += 100_000)
		{
			doReturn(clock).when(sounds).monotonicMillis();
			await(() -> !sounds.busy());
			sounds.tick(Long.MAX_VALUE);
		}
		verify(sounds, times(2)).playNow("test.wav", 50);
	}

	@Test
	public void valueAudioIsEligibleImmediatelyButRespectsCaseHold()
	{
		when(sounds.gate.delayMillis()).thenReturn(1200L);
		sounds.offerValue("test.wav", 50, "Coins", -1);
		when(sounds.gate.blocked()).thenReturn(true);
		sounds.tick(System.currentTimeMillis());
		verify(sounds, never()).playNow(anyString(), anyInt());
		when(sounds.gate.blocked()).thenReturn(false);
		sounds.tick(System.currentTimeMillis());
		verify(sounds).playNow("test.wav", 50);
	}

	@Test
	public void tierAndJingleBothUseAudioPlayer() throws Exception
	{
		sounds.playPreview("test.wav", 80, "test.wav", 60);
		verify(audio, timeout(1000).times(2)).play(any(InputStream.class), anyFloat());
		assertTrue(sounds.busy());
		sounds.cancelPreview();
		// The public API cannot stop started audio; its remaining duration stays reserved.
		assertTrue(sounds.busy());
		doReturn(3000L).when(sounds).monotonicMillis();
		await(() -> !sounds.busy());
	}

	@Test
	public void queueWaitsForWavDuration() throws Exception
	{
		sounds.playNow("test.wav", 80);
		verify(audio, timeout(1000)).play(any(InputStream.class), anyFloat());
		sounds.offerValue("test.wav", 50, "Coins", -1);
		sounds.tick(Long.MAX_VALUE);
		verify(audio, times(1)).play(any(InputStream.class), anyFloat());
		doReturn(3000L).when(sounds).monotonicMillis();
		await(() -> !sounds.busy());
		sounds.tick(Long.MAX_VALUE);
		verify(audio, timeout(1000).times(2)).play(any(InputStream.class), anyFloat());
	}

	@Test
	public void cancellationDuringLoadPreventsStalePreviewAndJingle() throws Exception
	{
		CompletableFuture<Void> entered = new CompletableFuture<>(), release = new CompletableFuture<>();
		doAnswer(call -> {
			entered.complete(null);
			release.get(2, TimeUnit.SECONDS);
			return call.callRealMethod();
		}).when(sounds).load(any(java.io.File.class));
		try
		{
			sounds.playPreview("test.wav", 80, "test.wav", 60);
			entered.get(1, TimeUnit.SECONDS);
			sounds.cancelPreview();
			release.complete(null);
			await(() -> !sounds.busy());
			verifyNoInteractions(audio);
		}
		finally { release.complete(null); }
	}

	@Test
	public void failedPlaybackReleasesQueue() throws Exception
	{
		doThrow(new java.io.IOException("device unavailable")).doNothing().when(audio).play(any(InputStream.class), anyFloat());
		sounds.playNow("test.wav", 80);
		verify(audio, timeout(1000)).play(any(InputStream.class), anyFloat());
		await(() -> !sounds.busy());
		sounds.playNow("test.wav", 50);
		verify(audio, timeout(1000).times(2)).play(any(InputStream.class), anyFloat());
	}

	@Test
	public void stalledLoadDoesNotFreezePopupQueue() throws Exception
	{
		CompletableFuture<Void> entered = new CompletableFuture<>(), release = new CompletableFuture<>();
		doAnswer(call -> {
			entered.complete(null);
			release.get(2, TimeUnit.SECONDS);
			return call.callRealMethod();
		}).when(sounds).load(any(java.io.File.class));
		try
		{
			sounds.playNow("test.wav", 80);
			entered.get(1, TimeUnit.SECONDS);
			doReturn(7000L).when(sounds).monotonicMillis();
			assertFalse(sounds.busy());
			release.complete(null);
			verifyNoInteractions(audio);
		}
		finally { release.complete(null); }
	}

	@Test
	public void burstDuringHoldKeepsLatest128InOrder()
	{
		doNothing().when(sounds).playNow(anyString(), anyInt());
		when(sounds.gate.blocked()).thenReturn(true);
		for (int i = 0; i < 1000; i++) { sounds.offerValue("sound" + i + ".wav", 50, "Coins", -1); }
		sounds.tick(Long.MAX_VALUE);
		verify(sounds, never()).playNow(anyString(), anyInt());
		when(sounds.gate.blocked()).thenReturn(false);
		for (int i = 0; i < 130; i++) { sounds.tick(Long.MAX_VALUE); }
		org.mockito.ArgumentCaptor<String> files = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(sounds, times(128)).playNow(files.capture(), eq(50));
		for (int i = 0; i < 128; i++) { assertEquals("sound" + (872 + i) + ".wav", files.getAllValues().get(i)); }
	}
}
