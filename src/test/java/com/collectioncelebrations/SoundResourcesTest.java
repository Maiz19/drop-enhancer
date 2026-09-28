/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import javax.sound.sampled.AudioInputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SoundResourcesTest
{

	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void freshInstallationCanDecodeEveryDefaultWithoutAnAudioDevice() throws Exception
	{
		CelebrationConfig config = new CelebrationConfig() {};
		String[] names = {config.fileCommon(), config.fileUncommon(), config.fileRare(), config.fileVeryRare(), config.filePet(),
			config.unlockFile()};
		for (String name : names)
		{
			try (AudioInputStream stream = decode(new File(temporary.getRoot(), name)))
			{
				assertTrue(name, stream.getFrameLength() > 0);
				assertTrue(name, stream.getFormat().getSampleRate() > 0);
				assertTrue(name, stream.read(new byte[4096]) > 0);
			}
		}
	}

	@Test
	public void localOverrideWinsOverBundledDefault() throws Exception
	{
		File override = new File(temporary.getRoot(), "botssouls-common.wav");
		try (java.io.InputStream source = getClass().getResourceAsStream("/sounds/drop-enhancer-pet.wav"))
		{
			Files.copy(source, override.toPath());
		}
		try (AudioInputStream local = decode(override);
			 AudioInputStream pet = decode(new File(temporary.getRoot(), "drop-enhancer-pet.wav")))
		{
			assertEquals(pet.getFrameLength(), local.getFrameLength());
		}
	}

	@Test(expected = IOException.class)
	public void invalidLocalOverrideDoesNotUnexpectedlyPlayDefault() throws Exception
	{
		File override = temporary.newFile("botssouls-common.wav");
		Files.writeString(override.toPath(), "invalid WAV");
		decode(override);
	}

	@Test(expected = FileNotFoundException.class)
	public void unknownCustomNameHasNoFallback() throws Exception
	{
		decode(new File(temporary.getRoot(), "missing-custom.wav"));
	}
	@Test
	public void savedLegacyDefaultsDecodeTheNewRecordings() throws Exception
	{
		for (String tier : new String[] {"common", "uncommon", "rare", "veryrare", "unlock", "pet"})
		{
			try (AudioInputStream legacy = decode(new File(temporary.getRoot(), "botssouls-" + tier + ".wav"));
				AudioInputStream current = decode(new File(temporary.getRoot(), "custom-sounds-" + tier + ".wav")))
			{
				assertEquals(current.getFormat().toString(), legacy.getFormat().toString());
				org.junit.Assert.assertArrayEquals(current.readAllBytes(), legacy.readAllBytes());
			}
		}
	}

	@Test
	public void newDefaultNamesStillAllowLocalOverrides() throws Exception
	{
		File override = new File(temporary.getRoot(), "custom-sounds-common.wav");
		try (java.io.InputStream source = getClass().getResourceAsStream("/sounds/drop-enhancer-pet.wav"))
		{
			Files.copy(source, override.toPath());
		}
		try (AudioInputStream local = decode(override);
			AudioInputStream pet = decode(new File(temporary.getRoot(), "drop-enhancer-pet.wav")))
		{
			org.junit.Assert.assertArrayEquals(pet.readAllBytes(), local.readAllBytes());
		}
	}

	@Test
	public void replacedCustomSoundsDefaultsResolveToOriginalSynthesis() throws Exception
	{
		for (String tier : new String[] {"uncommon", "pet"})
		{
			try (AudioInputStream legacy = decode(new File(temporary.getRoot(), "custom-sounds-" + tier + ".wav"));
				AudioInputStream current = decode(new File(temporary.getRoot(), "drop-enhancer-" + tier + ".wav")))
			{
				org.junit.Assert.assertArrayEquals(current.readAllBytes(), legacy.readAllBytes());
			}
		}
	}

	private AudioInputStream decode(File file) throws Exception
	{
		WavData wav = SoundResources.load(net.runelite.client.util.Filepath.Unchecked.getRooted(file.getParentFile().toPath())
			.joinSegment(file.getName()));
		assertTrue(wav.durationMillis > 0);
		return AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav.bytes));
	}

}
