JHenry Controller sound effects
================================

Drop sound files in this folder. Both WAV and OGG Vorbis are supported.

Format:
- WAV (RIFF), PCM, signed 16-bit little-endian, or OGG Vorbis
- 44100 Hz (22050 also fine), mono or stereo
- 0.1 - 1.5 seconds, normalized volume

Required filenames (play on these events):
- online.wav / online.ogg      bot connects / comes online
- offline.wav / offline.ogg    bot disconnects
- start.wav / start.ogg        bot starts digging
- finish.wav / finish.ogg      tunnel completed (success)
- error.wav / error.ogg        bot errored (mode FAILED)

Optional (not wired yet):
- alert.wav / alert.ogg        low HP / low tool durability / inventory full
- click.wav / click.ogg        button press

WAV is preferred if both exist. Missing files are silently ignored.
Toggle sound with the "Sound" button in the sidebar.
