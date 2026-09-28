package com.collectioncelebrations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Plugin Hub rules the review would reject, checked in the published code (src/main/java) with comments and strings
 * left out. The rules and where they come from: ~/Projects/RUNELITE-PLUGIN-HUB-RULES.md. Since 2026-09 reviewers send
 * updates back for direct file I/O (Filepath only), Thread interrupt and KeyboardFocusManager.
 */
public class HubRulesTest
{
	private static final class Rule
	{
		final Pattern pattern;
		final String why;
		/** Files where a use was decided on and is known to the reviewers. */
		final List<String> allowedIn;

		Rule(String regex, String why, String... allowedIn)
		{
			this.pattern = Pattern.compile(regex);
			this.why = why;
			this.allowedIn = Arrays.asList(allowedIn);
		}
	}

	private static final List<Rule> RULES = Arrays.asList(
		new Rule("\\bKeyboardFocusManager\\b|\\bKeyEventDispatcher\\b|addAWTEventListener",
			"global keyboard/focus/input hooks are not allowed; use key bindings on our own components"),
		new Rule("\\.interrupt\\(|\\binterrupted\\(|isInterrupted\\(",
			"Thread interrupt is not allowed (also not Thread.currentThread().interrupt())"),
		new Rule("Thread\\.sleep\\(|\\.awaitTermination\\(|\\bCountDownLatch\\b|\\.cancel\\(true\\)",
			"no sleeping, blocking shutdown or interrupting cancel; use scheduled executors, shutdownNow, CompletableFuture"),
		new Rule("\\bjava\\.io\\.File\\b|\\bnew File\\(|\\bFile\\[\\]|\\bFileInputStream\\b|\\bFileOutputStream\\b"
			+ "|\\bFileReader\\b|\\bFileWriter\\b|\\bRandomAccessFile\\b|\\bjava\\.nio\\.file\\.(Files|Paths|Path)\\b"
			+ "|\\bFiles\\.|\\bPaths\\.|RUNELITE_DIR|CACHE_DIR|user\\.home|\\bJFileChooser\\b|Filepath\\.Unchecked"
			+ "|ImageIO\\.(read|write)\\([^)]*\\bFile",
			"all file I/O goes through net.runelite.client.util.Filepath from getPluginDirectory()"),
		new Rule("\\bDesktop\\b|LinkBrowser\\.open\\(",
			"Desktop and LinkBrowser.open are restricted; use LinkBrowser.browse"),
		new Rule("\\bhopToWorld\\(",
			"client.hopToWorld forces a full manual review (reviewers: no longer allowed in hub plugins)"),
		new Rule("\\.menuAction\\(|\\brunScript\\(|\\.invokeMenuAction\\(",
			"no menu actions or scripts that act on the game"),
		new Rule("\\bgetenv\\(|availableProcessors\\(|\\bManagementFactory\\b|addShutdownHook\\(",
			"getenv, availableProcessors, ManagementFactory and shutdown hooks are not allowed (ClientShutdown event)"),
		new Rule("\\bgetDeclared(Field|Method|Constructor)s?\\(|setAccessible\\(|Class\\.forName\\(|\\.getMethod\\("
			+ "|\\bMethodHandles?\\b|\\bClassLoader\\b|\\bUnsafe\\b|System\\.(load|loadLibrary|exit)\\(",
			"no reflection, class loading, native code or System.exit"),
		new Rule("\\bProcessBuilder\\b|Runtime\\.getRuntime\\(\\)", "no external processes"),
		new Rule("\\bHttpURLConnection\\b|openConnection\\(|\\bjava\\.net\\.http\\b|new OkHttpClient\\("
			+ "|OkHttpClient\\.Builder\\(|new Gson\\(|new GsonBuilder\\(",
			"only the injected OkHttpClient and Gson"),
		new Rule("\\bObject(Input|Output)Stream\\b|\\bSerializable\\b", "no Java (de)serialization"),
		new Rule("\\bRobot\\b|\\.toFront\\(|setAlwaysOnTop\\(|setExtendedState\\(|\\.requestFocus\\(\\)",
			"never move, raise, focus or send input to the client window"),
		new Rule("setCameraYaw|setCameraPitch", "setting camera yaw/pitch is not allowed outside detached camera"),
		new Rule("@PluginDependency\\((?!.*\\b(SlayerPlugin|XpTrackerPlugin|BankTagsPlugin|ClueScrollPlugin|ItemStatPlugin|PartyPlugin)\\.class)",
		    "since RuneLite 1.13 a @PluginDependency must expose services (only Slayer, XP Tracker, Bank Tags, Clue Scroll, "
		    + "Item Stats, Party): read other plugins' config with ConfigManager.getConfig, find them in PluginManager.getPlugins"),
		new Rule("\\bWidgetInfo\\b|\\bWidgetID\\b|client\\.getVar\\(", "deprecated APIs the packager refuses")
	);

	/** Comments become a space, strings and characters empty, so only code is checked. */
	private static final Pattern NOT_CODE = Pattern.compile(
		"\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|//[^\\n]*|/\\*[\\s\\S]*?\\*/");

	static String code(String source)
	{
		Matcher m = NOT_CODE.matcher(source);
		StringBuffer out = new StringBuffer();
		while (m.find())
		{
			String found = m.group();
			String kept = found.startsWith("//") || found.startsWith("/*") ? " " : found.substring(0, 1) + found.charAt(0);
			// Keep line breaks, so line numbers stay right.
			kept += found.replaceAll("[^\\n]", "");
			m.appendReplacement(out, Matcher.quoteReplacement(kept));
		}
		m.appendTail(out);
		return out.toString();
	}

	@Test public void publishedCodeKeepsToThePluginHubRules() throws IOException
	{
		List<String> broken = new ArrayList<>();
		List<Path> sources;
		try (Stream<Path> walk = Files.walk(Paths.get("src/main/java")))
		{
			sources = walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
		assertTrue("no sources found", sources.size() > 10);
		for (Path source : sources)
		{
			String[] lines = code(new String(Files.readAllBytes(source), StandardCharsets.UTF_8)).split("\n", -1);
			for (int i = 0; i < lines.length; i++)
			{
				for (Rule rule : RULES)
				{
					if (rule.pattern.matcher(lines[i]).find() && !rule.allowedIn.contains(source.getFileName().toString()))
					{
						broken.add(source + ":" + (i + 1) + ": " + rule.why + "\n    " + lines[i].trim());
					}
				}
			}
		}
		assertTrue("Plugin Hub rules broken:\n" + String.join("\n", broken), broken.isEmpty());
	}

	@Test public void onlyCodeIsChecked()
	{
		String checked = code("int a = 1; // Thread.sleep(5)\nString s = \"KeyboardFocusManager\"; /* new File(x)\n*/ b();");
		assertTrue(checked, !checked.contains("sleep") && !checked.contains("Keyboard") && !checked.contains("File"));
		assertTrue(checked, checked.contains("b();") && checked.split("\n", -1).length == 3);
		assertTrue(RULES.get(1).pattern.matcher("Thread.currentThread().interrupt();").find());
		assertTrue(RULES.get(3).pattern.matcher("Files.readAllBytes(path)").find());
		assertTrue(!RULES.get(3).pattern.matcher("Filepath file = home.joinSegment(\"x\");").find());
	}
}
