# Paula Escobar

A terminal music player for demoscene and chip music, named after the Amiga's
sound chip. Everything happens in the terminal: picocli drives the command
line and JLine draws a full-screen, colour player view. The build produces a
native executable with GraalVM, so there is no JVM to start and no jar to
carry around.

Paula Escobar plays local files, but it also opens the party archives: the music
competitions of fourteen party series — from The Party, Assembly and The
Gathering through Breakpoint and Revision to Swedish Icing and the Polish
classics Intel Outside, Gravity and Xenium — can be browsed year by year
straight from the player, and any placed entry is downloaded from
scene.org, ModArchive or Modland and played on the spot.

Tracker modules are decoded and mixed by [JavaMod](https://github.com/quippy-git/javamod),
Daniel Becker's pure-Java player, which covers ProTracker, NoiseTracker,
FastTracker II, Scream Tracker, Impulse Tracker, Farandole and MultiTracker
files among others. Commodore 64 SID tunes play through the libsidplay2
port with reSID chip emulation that JavaMod bundles. Its jar is vendored
under `lib/` as a small Maven repository because no current release is
published to Maven Central. DigiBooster modules, which JavaMod does not
read, have a replayer of their own inside Paula Escobar.

## Building

```
mvn package
```

produces `target/paula`, a native executable. The build needs Maven and a
GraalVM for JDK 21 or newer registered in `~/.m2/toolchains.xml` with the
vendor `graalvm`, for example:

```xml
<toolchain>
    <type>jdk</type>
    <provides>
        <version>22</version>
        <vendor>graalvm</vendor>
    </provides>
    <configuration>
        <jdkHome>/home/you/graalvm/graalvm-jdk-22.0.2+9.1/</jdkHome>
    </configuration>
</toolchain>
```

Compilation, tests and `native-image` all run on that toolchain, so
`JAVA_HOME` and `GRAALVM_HOME` do not matter. `mvn test` runs the unit tests
without building the native image.

## Usage

```
paula                        browse the party archives
paula song.mod another.mod   play the files in order
paula info song.mod          print metadata
paula formats                list supported formats
paula --help
```

Keys while playing:

| Key     | Action                  |
|---------|-------------------------|
| `space` | pause / resume          |
| `←` `→` | seek five seconds       |
| `n`     | next track              |
| `p`     | previous track          |
| `b`     | switch to the browser   |
| `q`     | quit                    |

Keys while browsing:

| Key                 | Action                                   |
|---------------------|------------------------------------------|
| `↑` `↓`             | move the cursor                          |
| `PgUp` `PgDn`       | move a page                              |
| `Home` `End`        | jump to the first or last line           |
| `enter` `→`         | open the line, or play an entry          |
| `backspace` `←` `esc` | go back one level (`esc` at the top quits) |
| `b`                 | switch to the player                     |
| `space`             | pause / resume what is playing           |
| `q`                 | quit                                     |

### The screens

![The player, with the song details and the message the musician left in the module on the left, and the spectrum analyser and one oscilloscope per channel on the right](docs/player-screen.png)

Both screens fill the terminal: a gradient title bar on top, the key hints
in a bar at the bottom and box-drawn panels in between, in 24-bit colour
where the terminal supports it and rounded to 256 or 16 colours where it
does not. The player shows the song details on the left, with the
instrument that is sounding lit up, and on the right a 32-band spectrum
analyser with peak hold, one braille-dot oscilloscope per channel (a
single one for SID tunes), a position bar with elapsed and total time and
stereo VU meters, all redrawn thirty times a second. The browser colours
the first three placings gold, silver and bronze, highlights the cursor row
across the list and shows what is playing with a small spectrum strip
above the key bar, so music keeps going while you browse. The shot above
is Approach by Nightbeat, which won the multichannel competition at Icing
1999, with a scope for each of its 31 channels and the message the musician
wrote into the sample names beside them.

### Browsing demo parties

The browser starts with the party series, opens into the parties by year,
then into every music competition of that party and finally into the ranked
entries. Entries in streaming or executable music competitions are shown
dimmed because Paula cannot play them, but they stay in the list so the
results are complete. C64 competitions play through the SID emulation.
Entries Demozoo has no download for are marked "(no download)" as soon as
their details have been fetched, since some releases never made it to any
archive.
Playing an entry queues the rest of the competition
after it in ranked order, so `n` walks through the results.

Party data comes from [Demozoo](https://demozoo.org). The entry itself is
fetched from scene.org when Demozoo knows the party release there, otherwise
from ModArchive or Modland. Zip, LHA, Amiga LZX archives and 1541 disk images are unpacked, along with
whatever archives they hold in turn, and
the file inside named after the entry is played, or the first module in
name order when none is. Modules wrapped by the
Amiga's XPK packer are unwrapped as well when they use the NUKE, DUKE or
SQSH packers, which is what tracker modules of the time were packed with.

Party archives are often packed with a `file_id.diz` or an information file
carrying a hand drawn banner for the competition. Opening a competition
brings down its first entry in the background — the download playing it
would have cost anyway — and the art that comes with it is shown above the
list for every entry in that competition, an entry's own art taking
precedence. It is read in the code page it was drawn in: the box characters
of the PC or the accented letters of the Amiga, whichever the file leans
towards. Art shaped by terminal escapes is left alone.

Competitions handed in as bare modules carry no such file, and there the
party stands in for them. Demozoo names the folder a party keeps on
scene.org, and the file id or information file found in it — the information
directory first, since that is where a party keeps what belongs to the party
as a whole — is shown as the competition opens, until an entry's own art
takes its place. A ticker turns beside an entry whose files are on their way
down, and beside the competition while its logo is fetched, so a wait looks
like a wait rather than like nothing happening.

Everything fetched is kept under `~/.cache/paula` (or `$XDG_CACHE_HOME/paula`
when that variable holds an absolute path): Demozoo answers are refreshed after a week but
still used when the network is down, and downloaded modules and party logos
are kept for good.
Delete the directory to start over.

### SID tunes

A SID tune never ends on its own, so Paula plays it for the length listed
in the High Voltage SID Collection's song length database. That database
(`Songlengths.md5`, about five megabytes) is downloaded into the cache the
first time a SID is played, refreshed monthly and kept when offline; tunes
it does not know play for three minutes. The start subtune is played and the
player shows the author and release credits from the file.

### DigiBooster modules

The modules of DigiBooster Pro 2 and DigiBooster 3, the Amiga trackers no
other Java player reads, are played by a replayer written for Paula: the
sequencer and effects of the tracker, envelopes per instrument, and a chain
of wavetable, resampler, stereo panoramizer and cross feeding echo for every
one of the up to 254 tracks. It follows
[libdigibooster3](https://github.com/grzegorz-kraszewski/libdigibooster3),
the reference replayer released by APC&TCP under the two-clause BSD licence,
and renders the modules on Modland sample for sample as that library does.

### Commodore 64 disk images

A party file for a C64 competition is often a 1541 disk image holding every
entry as a program rather than a tune. Those images are unpacked like any
other archive — they carry no header, so they are known by their size — and
the programs inside are run by the same emulation that plays SID files. A
program plays the whole release, so it is only reached for when no link
offers the tune itself.

### Audio output

The native executable streams raw PCM into a system audio command, chosen
automatically: `pacat` (PulseAudio or PipeWire) first, then `aplay` (ALSA).
Pick one explicitly with `--output pulse` or `--output alsa`. When Paula runs
on a JVM, for example from the tests, Java Sound is used instead
(`--output javasound`).

Log output goes to `paula.log` in the working directory so it never
disturbs the player screen.

### Sound on the machine you are sitting at

`tools/paula-sound` starts the player where it lives and plays it out of the
machine you have ssh'd in from. That machine opens the way itself: it serves
its sound on its own loopback,

```
pactl load-module module-native-protocol-tcp listen=127.0.0.1
```

and carries a reverse tunnel to it with the ssh connection, once and for all
in its `~/.ssh/config`:

```
Host <the machine running paula>
    RemoteForward 4713 127.0.0.1:4713
```

Nothing listens on the network at either end. The script finds the near end
of the tunnel, makes sure a sound server answers through it and starts Paula
on the PULSE backend so the route does not depend on the ALSA setup of the
machine it runs on. When a piece is missing it tells the two apart — no
tunnel, or a tunnel with no sound server behind it — and prints what to run
where.

## Layout

| Package                          | Responsibility                                              |
|----------------------------------|-------------------------------------------------------------|
| `com.adeptum.paula`              | `Paula`, the root picocli command and entry point           |
| `com.adeptum.paula.cli`          | `info` and `formats` subcommands, version provider          |
| `com.adeptum.paula.module`       | module model, loader interface and loader registry          |
| `com.adeptum.paula.module.javamod` | loads tracker modules through JavaMod                     |
| `com.adeptum.paula.module.sid`   | SID loader and renderer, HVSC song lengths                  |
| `com.adeptum.paula.module.digibooster` | reads and plays DigiBooster modules                   |
| `com.adeptum.paula.audio`        | `AudioSink`, the audio backends and PCM encoding            |
| `com.adeptum.paula.playback`     | `Renderer`, `PlaybackEngine`, the track loader and the session |
| `com.adeptum.paula.playback.javamod` | pulls mixed audio from JavaMod into the pipeline        |
| `com.adeptum.paula.playlist`     | playlist navigation over local and Demozoo tracks           |
| `com.adeptum.paula.demozoo`      | Demozoo API model, cached client, track resolution and browsing art |
| `com.adeptum.paula.archive`      | zip and LHA extraction, format detection by magic bytes     |
| `com.adeptum.paula.archive.lzx`  | Amiga LZX decoder                                           |
| `com.adeptum.paula.archive.xpk`  | Amiga XPK unpacker (NUKE, DUKE, SQSH)                       |
| `com.adeptum.paula.cache`        | the XDG cache directory                                     |
| `com.adeptum.paula.ui`           | player and browser screens, frame layout, key mapping, JLine terminal |
| `com.adeptum.paula.ui.visual`    | FFT, spectrum analyser, VU meters, braille scopes, bars, palette |

Adding a format means implementing `ModuleLoader`, returning a `Module`
whose `createRenderer` produces the audio, and registering the loader in
`ModuleLoaderRegistry`.

Resources the native image must carry are listed in
`src/main/resources/META-INF/native-image/com.adeptum/paula/resource-config.json`.

## License

Copyright © 2026 Adam Waldenberg, Adeptum AB. Licensed under the GNU General Public License,
version 3 or later. See [LICENSE](LICENSE). JavaMod is copyright Daniel
Becker and licensed under the GNU General Public License, version 3.

LHA archives are read with the LHA Library for Java, copyright Michel
Ishizuka, distributed under the BSD 2-Clause License reproduced in
[lib/JLHA-LICENSE.txt](lib/JLHA-LICENSE.txt). The LZX decoder follows the
implementation in [XADMaster](https://github.com/MacPaw/XADMaster), copyright
MacPaw Inc., licensed under the GNU Lesser General Public License version 2.1
or later and used under the GPL as that licence permits. The XPK unpacker
follows Teemu Suutari's [ancient](https://github.com/temisu/ancient),
distributed under the BSD 2-Clause License.
