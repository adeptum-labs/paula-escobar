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
whose Java `tools/generate-asap` generates into the tree. DigiBooster, AHX
and HivelyTracker modules have replayers of their own inside Paula Escobar.
MPEG audio, FLAC, Ogg
Vorbis, wave, AIFF and AU files, the formats a streaming competition is
handed in as, are decoded by JLayer, jFLAC and jOrbis and resampled to the
rate the engine mixes at; `--rate 44100` hands a CD-rate file through
untouched.

## Building

`mvn package` produces `target/paula`, a native executable. It needs Maven,
a C compiler (`cc`, or Visual Studio's `cl` on Windows) and a GraalVM for
JDK 21 or newer registered in `~/.m2/toolchains.xml` with the vendor
`graalvm`:

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

Everything runs on that toolchain, so `JAVA_HOME` and `GRAALVM_HOME` do not
matter. `mvn test` runs the unit tests without the native image or the C.

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

The mouse works the panels: a click on the upper one turns to the next
visualiser, a click on a scope silences that channel until clicked again,
and a shift-click or double click leaves that channel sounding alone.

Keys while browsing:

| Key                 | Action                                   |
|---------------------|------------------------------------------|
| `↑` `↓`             | move the cursor                          |
| `PgUp` `PgDn`       | move ten lines                           |
| `Home` `End`        | jump to the first or last line           |
| `tab`               | switch between the parties and the charts |
| `enter` `→`         | open the line, or play an entry          |
| `m`                 | more by the musician                     |
| `backspace` `←` `esc` | go back one level (`esc` at the top quits) |
| `b`                 | switch to the player                     |
| `r`                 | fetch this list and its logo afresh      |
| `?`                 | show the keys                            |
| `space`             | pause / resume what is playing           |
| `q`                 | quit                                     |

### The screens

![The player, with the song details and the message the musician left in the module on the left, and the spectrum analyser and one oscilloscope per channel on the right](docs/player-screen.png)

Both screens fill the terminal in 24-bit colour, or 256 or 16 where that is
all there is. The player shows the song details on the left, with the
sounding instrument lit up, and on the right a spectrum analyser, one
braille-dot oscilloscope per channel, a position bar and VU meters; `v`
turns the upper panel to a waterfall and then to a vectorscope. The browser
colours the first three placings gold, silver and bronze and keeps a small
spectrum strip of what is playing above the key bar. The shot above is
Approach by Nightbeat, which won the multichannel competition at Icing 1999,
with a scope for each of its 31 channels.

### Browsing

The party series sit in columns on the left and the charts on the right;
`tab` switches sides. A series opens into its parties, a party into its
music competitions and a competition into the ranked entries, each with its
title, its author and whatever is the matter with it: dimmed for executable
music Paula cannot run, `(no download)` where Demozoo has no file,
`(no reader)` where the file is a container nothing here opens. A chart
opens into All, MOD, XM, IT, S3M and Other and is read forty rows at a time
as the cursor reaches the end. Playing a line queues the rest of its list,
so `n` walks on through the results, and `m` opens everything else the
musician has, on Demozoo for an entry and on ModArchive for a chart tune.

Party data comes from [Demozoo](https://demozoo.org) and the files from
scene.org, ModArchive or Modland. Zip, 7z, RAR, LHA and LZX archives, XPK
packed modules and 1541 disk images are unpacked as deep as they go, and the
file named after the entry is played, with the art of its `file_id.diz` or
the party's own logo shown above the list. Everything fetched is kept under
`~/.cache/paula` (or `$XDG_CACHE_HOME/paula`): Demozoo answers a week and
chart pages a day, both still used offline, downloaded modules for good.
`r` fetches the list in view afresh; delete the directory to start over.

### Chip and Amiga formats

A SID tune plays for the length in the High Voltage SID Collection's song
length database, fetched into the cache on first use and refreshed monthly,
or three minutes when unlisted; a C64 party file that is a 1541 disk image
has its programs run by the same emulation. Atari 8-bit SAP files and the
native modules of the Atari trackers play through ASAP's POKEY and 6502
emulation for the length their TIME tag gives, each POKEY channel with a
scope of its own. DigiBooster Pro 2 and 3 modules and AHX and HivelyTracker
modules are played by replayers written for Paula that follow
[libdigibooster3](https://github.com/grzegorz-kraszewski/libdigibooster3)
and [HivelyTracker](https://github.com/pete-gordon/hivelytracker)'s
`hvl_replay.c` and render sample for sample as those do, with a scope and
muting per voice. Nothing under `net.sf.asap` is edited by hand;
`tools/generate-asap` regenerates it, as `tools/patch-javamod` rebuilds the
vendored JavaMod jar with its fixes.

### Audio output

The native executable plays sound itself through
[miniaudio](https://miniaud.io), since a native image finds no Java Sound
mixers, picking the first backend that answers (WASAPI, CoreAudio,
PulseAudio, ALSA, JACK) or the one named with `--output`; `--output null`
plays into nothing at the right speed and the runnable jar uses Java Sound.
`--record FILE` keeps a wave file of everything played and
`--quit-after SECONDS` stops Paula without a terminal, which is how the
build proves the sound on every platform. Logging goes to `paula.log` in the
working directory.

Over ssh, `tools/paula-sound` plays out of the machine you sit at: that
machine serves PulseAudio on its loopback with
`pactl load-module module-native-protocol-tcp listen=127.0.0.1` and carries
a reverse tunnel with `RemoteForward 4713 127.0.0.1:4713` in its
`~/.ssh/config`; the script points `PULSE_SERVER` at the tunnel and says
what is missing when nothing answers.

## Releases

Every release hangs off its tag on GitHub with an executable for Linux, macOS
and Windows and the runnable jar, also kept under `releases/` here; the jar
needs a Java 21 runtime:

```
java -jar paula-escobar-0.1.0.jar
```

`./create-release.sh` builds and tests at the version the pom is working
towards, records the jar in a `Release X.Y.Z` commit with an annotated tag
and opens the next snapshot, pushing nothing. Sending the tag builds the
executables and drafts the release:

```
git push && git push origin v0.1.0
```

## License

Copyright © 2026 Adam Waldenberg, Adeptum AB. Licensed under the GNU General Public License,
version 3 or later. See [LICENSE](LICENSE), and
[LICENSE.addendum](LICENSE.addendum) for the additional permission that
covers linking the RAR reader. JavaMod is copyright Daniel Becker and
licensed under the GNU General Public License, version 3. ASAP is copyright
Piotr Fusik and licensed under the GNU General Public License, version 2
or later, used here under version 3.

LHA archives are read with the LHA Library for Java, copyright Michel
Ishizuka, distributed under the BSD 2-Clause License reproduced in [lib/JLHA-
LICENSE.txt](lib/JLHA-LICENSE.txt). The LZX decoder follows the implementation
in [XADMaster](https://github.com/MacPaw/XADMaster), copyright MacPaw Inc.,
licensed under the GNU Lesser General Public License version 2.1 or later and
used under the GPL as that licence permits. The XPK unpacker follows Teemu
Suutari's [ancient](https://github.com/temisu/ancient), distributed under the
BSD 2-Clause License. The AHX and HivelyTracker replayer follows
HivelyTracker's, copyright Pete Gordon, distributed under the BSD 3-Clause
License reproduced in [HIVELY-LICENSE.txt](HIVELY-LICENSE.txt). 7z archives
are read with Apache Commons Compress over the XZ for Java library, both under
the Apache License 2.0. RAR archives are read with
[junrar](https://github.com/junrar/junrar), distributed under the UnRAR
license reproduced in [UNRAR-LICENSE.txt](UNRAR-LICENSE.txt), which allows
unpacking RAR archives and forbids re-creating the RAR compression algorithm;
Paula Escobar only unpacks.