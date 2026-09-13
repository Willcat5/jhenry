package willits.jhenry.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public final class SoundPlayer {

	private static final int MAX_POOL = 8;

	private static final Map<String, URL> URLS = new HashMap<>();
	private static final Map<String, List<Clip>> POOLS = new HashMap<>();
	private static boolean muted;

	private SoundPlayer() {
	}

	public static void play(String name) {
		if (muted) {
			return;
		}
		try {
			URL url = URLS.get(name);
			if (url == null) {
				url = findResource(name);
				if (url == null) {
					return;
				}
				URLS.put(name, url);
			}

			List<Clip> pool = POOLS.computeIfAbsent(name, key -> new ArrayList<>());
			Clip clip = null;
			for (Clip candidate : pool) {
				if (!candidate.isRunning()) {
					clip = candidate;
					break;
				}
			}

			if (clip == null) {
				if (pool.size() < MAX_POOL) {
					clip = loadClip(name, url);
					if (clip == null) {
						return;
					}
					pool.add(clip);
				} else {
					clip = pool.get(0);
					clip.stop();
				}
			}

			clip.setFramePosition(0);
			clip.start();
		} catch (Exception e) {
			// missing or unsupported sound file; ignore
		}
	}

	private static URL findResource(String name) {
		URL url = SoundPlayer.class.getResource("/sounds/" + name + ".wav");
		if (url == null) {
			url = SoundPlayer.class.getResource("/sounds/" + name + ".ogg");
		}
		return url;
	}

	private static Clip loadClip(String name, URL url) throws Exception {
		try (AudioInputStream source = AudioSystem.getAudioInputStream(url)) {
			AudioFormat base = source.getFormat();
			AudioInputStream pcmStream = source;
			if (base.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
				AudioFormat pcm = new AudioFormat(
						AudioFormat.Encoding.PCM_SIGNED,
						base.getSampleRate(),
						16,
						base.getChannels(),
						base.getChannels() * 2,
						base.getSampleRate(),
						false);
				pcmStream = AudioSystem.getAudioInputStream(pcm, source);
			}
			Clip clip = AudioSystem.getClip();
			clip.open(pcmStream);
			if (clip.getFrameLength() <= 0) {
				System.err.println("JHenry: sound '" + name + "' decoded to zero frames (" + url + ")");
				clip.close();
				return null;
			}
			return clip;
		}
	}

	public static boolean isMuted() {
		return muted;
	}

	public static void setMuted(boolean value) {
		muted = value;
	}

	public static void toggleMuted() {
		muted = !muted;
	}
}
