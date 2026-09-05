# Paula Escobar

A terminal music player for demoscene and chip music, named after the Amiga's
sound chip. Everything happens in the terminal: picocli drives the command
line and JLine draws a full-screen, colour player view. The build produces a
native executable with GraalVM, so there is no JVM to start and no jar to
carry around.

Besides local files it opens the party archives: the music competitions of
over sixty party series, from The Party, Assembly and The Gathering through
Breakpoint and Revision to Swedish Icing, the Polish classics and X, the
Commodore 64's own party, can be browsed year by year, and any placed entry
is downloaded from scene.org, ModArchive or Modland and played on the spot.
The charts of [ModArchive](https://modarchive.org), its most favoured, most
downloaded and featured modules, can be browsed by format the same way.

Tracker modules are decoded by [JavaMod](https://github.com/quippy-git/javamod),
Daniel Becker's pure-Java player, which also brings the libsidplay2 port with
reSID emulation that plays Commodore 64 SID tunes; its jar is vendored under
`lib/` with the fixes in `tools/javamod-*.patch` compiled in by
`tools/patch-javamod`. Atari 8-bit tunes play through
[ASAP](https://asap.sourceforge.net), Piotr Fusik's POKEY and 6502 emulation,
whose Java `tools/generate-asap` generates into the tree. DigiBooster modules
have a replayer of their own inside Paula Escobar. MPEG audio, FLAC, Ogg
Vorbis, wave, AIFF and AU files, the formats a streaming competition is
handed in as, are decoded by JLayer, jFLAC and jOrbis and resampled to the
rate the engine mixes at; `--rate 44100` hands a CD-rate file through
untouched.

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

A C compiler is needed as well (`cc` on Linux and macOS, Visual Studio's
`cl` on Windows); `native-image` itself already requires one. The sound
shim in `src/main/c` is compiled into a static library during
`prepare-package`, so `mvn test` runs without it.

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
| `v`     | next visualiser         |
| `?`     | show the keys           |
| `q`     | quit                    |

The mouse works the panels: a click on the upper one turns it to the next
visualiser, and a click on a scope silences that channel until it is clicked
again. Shift-click, or a double click where the terminal keeps shift-click
for selecting text, leaves that channel sounding alone and brings the rest
back on the next. What is silenced belongs to the track being played.

Keys while browsing:

| Key                 | Action                                   |
|---------------------|------------------------------------------|
| `↑` `↓`             | move the cursor                          |
| `PgUp` `PgDn`       | move ten lines                           |
| `Home` `End`        | jump to the first or last line           |
| `tab`               | switch between the parties and the charts |
| `enter` `→`         | open the line, or play an entry          |
| `backspace` `←` `esc` | go back one level (`esc` at the top quits) |
| `b`                 | switch to the player                     |
| `r`                 | fetch this list and its logo afresh      |
| `?`                 | show the keys                            |
| `space`             | pause / resume what is playing           |
| `q`                 | quit                                     |

### The screens

![The player, with the song details and the message the musician left in the module on the left, and the spectrum analyser and one oscilloscope per channel on the right](docs/player-screen.png)

Both screens fill the terminal, in 24-bit colour where the terminal has it
and rounded to 256 or 16 colours where it does not. The player shows the song
details on the left, with the sounding instrument lit up, and on the right a
32-band spectrum analyser with peak hold, one braille-dot oscilloscope per
channel, a position bar and stereo VU meters, redrawn thirty times a second.
`v` turns the upper panel over to a waterfall of the spectrum and then to a
vectorscope plotting left against right, where the hard-panned channels of an
Amiga module draw a shape of their own. The browser colours the first three
placings gold, silver and bronze and keeps a small spectrum strip of what is
playing above the key bar, so music keeps going while you browse. The shot
above is Approach by Nightbeat, which won the multichannel competition at
Icing 1999, with a scope for each of its 31 channels and the message the
musician wrote into the sample names beside them.

### Browsing

The browser opens with the party series in columns on the left and the
charts on the right; `tab` switches sides and `?` lays out every key. A
series opens into its parties by year, a party into its music competitions
and a competition into the ranked entries, each with its title, its author
and whatever is the matter with it in a column of its own. Entries in
executable music competitions are dimmed, since Paula cannot run them, and
stay in the list so the results are complete; those Demozoo has no download
for are marked `(no download)`, those whose only download is a container
nothing here reads `(no reader)`. Playing an entry queues the rest of the
competition after it in ranked order, so `n` walks through the results.

A chart, Top Favourites, Most Downloads or the weekly Featured picks, opens
into All, MOD, XM, IT, S3M and Other by file extension. Forty rows are
fetched at a time and the next forty as the cursor reaches the end, a row
below the last saying that more follows; the right-hand column gives the
favourites, the downloads or the week featured. Playing a tune queues the
rest of the list.

Party data comes from [Demozoo](https://demozoo.org); the entry itself from
scene.org when Demozoo knows the release there, otherwise from ModArchive or
Modland. Zip, 7z, RAR, LHA and Amiga LZX archives and 1541 disk images are
unpacked, along with whatever archives they hold in turn, modules wrapped by
the Amiga's XPK packer are unwrapped, and the file named after the entry is
played, or the first module in name order when none is. Unpacking, and a
download long enough to wait for, is counted up on the status line. The
`file_id.diz` or information file that travels in a party archive, or the
party's own on scene.org, is shown above the list as a competition opens,
read in the code page it was drawn in.

Everything fetched is kept under `~/.cache/paula` (or `$XDG_CACHE_HOME/paula`):
Demozoo answers are refreshed after a week and chart pages after a day, both
still used when the network is down, and downloaded modules are kept for good,
a competition handed in as one archive downloaded once however many of its
entries are played. `r` fetches the list in view afresh, with its logo.
Delete the directory to start over.

### SID tunes

A SID tune never ends on its own, so Paula plays it for the length listed
in the High Voltage SID Collection's song length database. That database
(`Songlengths.md5`, about five megabytes) is downloaded into the cache the
first time a SID is played, refreshed monthly and kept when offline; tunes
it does not know play for three minutes. The start subtune is played and the
player shows the author and release credits from the file.

### Atari 8-bit tunes

SAP files and the native modules of Chaos Music Composer, Delta Music
Composer, Music ProTracker, Raster Music Tracker, Theta Music Composer and
Future Composer play through ASAP's emulation of the POKEY chip and the
6502. A tune plays for the length its TIME tag gives, or three minutes
without one, starting at its default subtune, and the player shows the
author and date from the file. Every POKEY channel has a scope of its own,
four to a chip and eight for a stereo tune, and can be silenced like a
tracker channel. The engine's Java is generated from ASAP's sources by
`tools/generate-asap`, which pins what it takes; nothing under
`net.sf.asap` is edited by hand.

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

The native executable plays sound itself through
[miniaudio](https://miniaud.io), which is compiled into it. It picks the
first backend that answers: WASAPI on Windows, CoreAudio on macOS,
PulseAudio (also PipeWire), ALSA and JACK on Linux. Pick one explicitly
with `--output pulse`, `--output alsa`, `--output jack`,
`--output coreaudio` or `--output wasapi`; `--output null` plays into
nothing at the right speed. The build proves the sound on every platform:
it plays the test module through Core Audio on macOS and WASAPI on
Windows, where the runner is given a virtual sound card to play into, and
through the null backend on Linux, and checks the recording `--record`
kept for sound of the length played.
When Paula runs on a JVM, from the runnable jar or from the tests, Java
Sound is used instead (`--output javasound`).

Java Sound is not open to the native executable: a native image finds no
mixer providers, which [GraalVM does not intend to
fix](https://github.com/oracle/graal/issues/9620). That is why the
executable carries a sound library of its own.

`--quit-after SECONDS` stops Paula by itself after that long and lets it
run without a terminal, for scripts and for the build. `--record FILE`
keeps a copy of everything played in that wave file, at the output rate,
whichever backend the sound went to.

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

Nothing listens on the network at either end. The script points
`PULSE_SERVER` at the near end of the tunnel, which the PulseAudio backend
— the first one tried on Linux — picks up. Were nothing answering there,
the sound would fall through to ALSA on the machine running Paula, so the
script makes sure a sound server replies before it starts anything. When a
piece is missing it tells the two apart — no tunnel, or a tunnel with no
sound server behind it — and prints what to run where.

## Releases

Every release hangs off its tag on GitHub and carries an executable for
Linux, macOS and Windows, built on each of those machines by GitHub Actions,
and the runnable jar. The jar is also kept in `releases/` here, so a checkout
of any version holds the thing that version built.

Which one to take: the executable, if there is one for your machine — it
starts at once, needs no Java and plays sound itself, for the reason given
under audio output above. The jar needs a Java 21 runtime and nothing
else, and plays everywhere as it is:

```
java -jar paula-escobar-0.1.0.jar
```

Cutting one is a single command, and it pushes nothing:

```
./create-release.sh
```

It builds and tests at the version the pom is working towards, keeps the jar
under `releases/`, records it in a `Release X.Y.Z` commit with an annotated
tag, and opens the next snapshot. Sending the tag is what builds the three
executables and drafts the release:

```
git push && git push origin v0.1.0
```

The release is drafted rather than published, so it can be read over — and
thrown away without a trace — before anyone sees it.

## Layout

| Package                          | Responsibility                                              |
|----------------------------------|-------------------------------------------------------------|
| `com.adeptum.paula`              | `Paula`, the root picocli command and entry point           |
| `com.adeptum.paula.cli`          | `info` and `formats` subcommands, version provider          |
| `com.adeptum.paula.module`       | module model, loader interface and loader registry          |
| `com.adeptum.paula.module.javamod` | loads tracker modules through JavaMod                     |
| `com.adeptum.paula.module.sid`   | SID loader and renderer, HVSC song lengths                  |
| `com.adeptum.paula.module.digibooster` | reads and plays DigiBooster modules                   |
| `com.adeptum.paula.module.sap`   | Atari 8-bit loader and renderer over ASAP                   |
| `com.adeptum.paula.module.mp3`   | MPEG audio loader, renderer and ID3 tags                    |
| `com.adeptum.paula.module.flac`  | FLAC loader, renderer and Vorbis comments                   |
| `com.adeptum.paula.module.ogg`   | Ogg Vorbis loader, renderer and Vorbis comments             |
| `com.adeptum.paula.module.wav`   | wave, AIFF and AU loader, container reader and renderer     |
| `com.adeptum.paula.module.pcm`   | resampling and buffering shared by the sampled formats      |
| `com.adeptum.paula.audio`        | `AudioSink`, the audio backends and PCM encoding            |
| `com.adeptum.paula.playback`     | `Renderer`, `PlaybackEngine`, the track loader and the session |
| `com.adeptum.paula.playback.javamod` | pulls mixed audio from JavaMod into the pipeline        |
| `com.adeptum.paula.playlist`     | playlist navigation over local, Demozoo and ModArchive tracks |
| `com.adeptum.paula.demozoo`      | Demozoo API model, cached client, track resolution and browsing art |
| `com.adeptum.paula.modarchive`   | ModArchive charts read off the site's pages and cached      |
| `com.adeptum.paula.archive`      | zip, 7z, RAR and LHA extraction, detection by magic bytes   |
| `com.adeptum.paula.archive.lzx`  | Amiga LZX decoder                                           |
| `com.adeptum.paula.archive.xpk`  | Amiga XPK unpacker (NUKE, DUKE, SQSH)                       |
| `com.adeptum.paula.cache`        | the XDG cache directory                                     |
| `com.adeptum.paula.ui`           | player and browser screens, frame layout, key mapping, JLine terminal |
| `com.adeptum.paula.ui.visual`    | FFT, spectrum analyser, VU meters, braille scopes, bars, palette |
| `net.sf.asap`                    | ASAP, generated by fut from its Fusion sources, never edited by hand |

Adding a format means implementing `ModuleLoader`, returning a `Module`
whose `createRenderer` produces the audio, and registering the loader in
`ModuleLoaderRegistry`.

Resources the native image must carry are listed in
`src/main/resources/META-INF/native-image/com.adeptum/paula/resource-config.json`.

## License

Copyright © 2026 Adam Waldenberg, Adeptum AB. Licensed under the GNU General Public License,
version 3 or later. See [LICENSE](LICENSE), and
[LICENSE.addendum](LICENSE.addendum) for the additional permission that
covers linking the RAR reader. JavaMod is copyright Daniel Becker and
licensed under the GNU General Public License, version 3. ASAP is copyright
Piotr Fusik and licensed under the GNU General Public License, version 2
or later, used here under version 3.

LHA archives are read with the LHA Library for Java, copyright Michel
Ishizuka, distributed under the BSD 2-Clause License reproduced in
[lib/JLHA-LICENSE.txt](lib/JLHA-LICENSE.txt). The LZX decoder follows the
implementation in [XADMaster](https://github.com/MacPaw/XADMaster), copyright
MacPaw Inc., licensed under the GNU Lesser General Public License version 2.1
or later and used under the GPL as that licence permits. The XPK unpacker
follows Teemu Suutari's [ancient](https://github.com/temisu/ancient),
distributed under the BSD 2-Clause License. 7z archives are read with
Apache Commons Compress over the XZ for Java library, both under the
Apache License 2.0. RAR archives are read with
[junrar](https://github.com/junrar/junrar), distributed under the UnRAR
license reproduced in [UNRAR-LICENSE.txt](UNRAR-LICENSE.txt), which
allows unpacking RAR archives and forbids re-creating the RAR
compression algorithm; Paula Escobar only unpacks.
