/* Copyright (c) 2026 maiz. BSD-2-Clause; see LICENSE. */
package com.collectioncelebrations;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import net.runelite.client.util.Filepath;

/** Local overrides take precedence; defaults are read directly from the plugin JAR. */
final class SoundResources
{
	private static final Map<String, String> LEGACY_DEFAULTS = Map.of(
		"botssouls-common.wav", "custom-sounds-common.wav",
		"botssouls-pet.wav", "drop-enhancer-pet.wav",
		"custom-sounds-pet.wav", "drop-enhancer-pet.wav",
		"botssouls-uncommon.wav", "drop-enhancer-uncommon.wav",
		"custom-sounds-uncommon.wav", "drop-enhancer-uncommon.wav",
		"botssouls-rare.wav", "custom-sounds-rare.wav",
		"botssouls-veryrare.wav", "custom-sounds-veryrare.wav",
		"botssouls-unlock.wav", "custom-sounds-unlock.wav");

	private SoundResources()
	{
	}

	static WavData load(Filepath local) throws IOException
	{
		if (local.exists())
		{
			try (InputStream input = local.openInputStream())
			{
				return WavData.read(input);
			}
		}
		String name = LEGACY_DEFAULTS.getOrDefault(local.getFileName(), local.getFileName());
		InputStream resource = SoundQueue.validName(name) ? SoundResources.class.getResourceAsStream("/sounds/" + name) : null;
		if (resource == null)
		{
			throw new FileNotFoundException("Sound is not installed: " + name);
		}
		try (InputStream input = resource)
		{
			return WavData.read(input);
		}
	}
}
