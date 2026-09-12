# Paula Escobar

[![Latest release](https://img.shields.io/github/v/release/adeptum-labs/paula-escobar.svg?style=flat-square)](https://github.com/adeptum-labs/paula-escobar/releases/latest)
[![Tests](https://img.shields.io/github/actions/workflow/status/adeptum-labs/paula-escobar/tests.yml?branch=master&label=tests&style=flat-square)](https://github.com/adeptum-labs/paula-escobar/actions/workflows/tests.yml)
[![License](https://img.shields.io/github/license/adeptum-labs/paula-escobar.svg?style=flat-square)](https://github.com/adeptum-labs/paula-escobar/blob/master/LICENSE)

A terminal music player for demoscene and chip music, named after the Amiga's
sound chip. It plays tracker modules, SID and Atari tunes and streamed audio,
and it opens the party archives: the music competitions of eighty party
series and the [ModArchive](https://modarchive.org) charts, browsed year by
year and played on the spot. Everything happens in the terminal, and the build
produces a native executable with GraalVM, so there is no JVM to start and no
jar to carry around.

## The screens

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
| `c`     | cast to a device        |
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

### Browsing

The party series sit in columns on the left and the charts on the right;
`tab` switches sides. The series run from The Party, Assembly and The
Gathering through Breakpoint and Revision to Swedish Icing, the Polish
classics and X, the Commodore 64's own party. A series opens into its
parties, a party into its music competitions and a competition into the
ranked entries, each with its title, its author and whatever is the matter
with it: dimmed for executable music Paula cannot run, `(no download)` where
Demozoo has no file, `(no reader)` where the file is a container nothing here
opens. A chart opens into All, MOD, XM, IT, S3M and Other and is read forty
rows at a time as the cursor reaches the end. Playing a line queues the rest
of its list, so `n` walks on through the results, and `m` opens everything
else the musician has, on Demozoo for an entry and on ModArchive for a chart
tune.

Party data comes from [Demozoo](https://demozoo.org) and the files from
scene.org, ModArchive or Modland. Zip, 7z, RAR, LHA and LZX archives, XPK
and PowerPacker crunched modules, Unreal music packages and 1541 disk and
tape images are unpacked as deep as they go, and the file named after the
entry is played, with the art of its `file_id.diz` or the party's own logo
shown above the list. Everything fetched is kept under `~/.cache/paula` (or
`$XDG_CACHE_HOME/paula`): Demozoo answers a week and chart pages a day, both
still used offline, downloaded modules for good. `r` fetches the list in
view afresh; delete the directory to start over.

### Chip and Amiga formats

Tracker modules are decoded by [JavaMod](https://github.com/quippy-git/javamod),
Daniel Becker's pure-Java player, which also brings the libsidplay2 port with
reSID emulation that plays Commodore 64 SID tunes; its jar is vendored under
`lib/` with the fixes in `tools/javamod-*.patch` compiled in by
`tools/patch-javamod`. The Atari Falcon's FlexTrax modules are ProTracker
modules under their own extension and play as those, with the reverb and the
delay the Falcon's DSP laid over the mix put back over it by replayers
written for Paula that follow the tracker's own DSP program; the settings for
those are kept in a block of the file that
[docs/flextrax-format.md](docs/flextrax-format.md) describes. MPEG audio,
FLAC, Ogg Vorbis, Monkey's Audio, wave,
AIFF and AU files, the formats a streaming competition is handed in as, are
decoded by JLayer, jFLAC, jOrbis and jMAC and resampled to the rate the
engine mixes at; `--rate 44100` hands a CD-rate file through untouched.

A SID tune plays for the length in the High Voltage SID Collection's song
length database, fetched into the cache on first use and refreshed monthly,
or three minutes when unlisted; a C64 party file that is a 1541 disk or a
tape image has its programs run by the same emulation. Atari 8-bit SAP files
and the native modules of the Atari trackers play through
[ASAP](https://asap.sourceforge.net), Piotr Fusik's POKEY and 6502 emulation,
for the length their TIME tag gives, each POKEY channel with a scope of its
own. DigiBooster Pro 2 and 3 modules and AHX and HivelyTracker modules are
played by replayers written for Paula that follow
[libdigibooster3](https://github.com/grzegorz-kraszewski/libdigibooster3)
and [HivelyTracker](https://github.com/pete-gordon/hivelytracker)'s
`hvl_replay.c` and render sample for sample as those do, with a scope and
muting per voice. OctaMED modules of every kind, MMD0 through MMD3, are
played by a third that follows the MED loaders and player of
[libxmp](https://github.com/libxmp/libxmp), sounding the sampled,
multi-octave, synthetic and hybrid instruments alike, with a scope and
muting per track; its mixdown is Paula's own rather than libxmp's. Composer
669 and UNIS 669 modules, from the PC tracker that counted its notes in
hertz, are played by a fourth that follows what
[OpenMPT](https://openmpt.org)'s player found the original to do: slides
and vibrato by whole hertz, effects that carry on until the next note, and
the pan slide and retrigger of the UNIS extension. MO3
files, the compressed form the demoscene passes those trackers' modules
around in, are unpacked back into the module the tracker wrote and played
by its own player: the song, the effects and the instruments as Impulse
Tracker, Scream Tracker, ProTracker, Fast Tracker or MultiTracker meant
them, and the samples whether they were kept whole, delta packed, or
squeezed into MPEG audio or Ogg Vorbis. Nothing
under `net.sf.asap` is edited by hand; `tools/generate-asap` regenerates it, as
`tools/patch-javamod` rebuilds the vendored JavaMod jar with its fixes.

### Casting

Press `c` in the player, or click `cast` in the footer, and Paula lists this
machine and every Google Cast device on the network. Choose one and the song
moves there without stopping; choose this machine to bring it back.
`--output cast` plays there from the start and `--cast NAME` says which
device, by the name it was given or its address.

A Cast device fetches the sound rather than being sent it, so Paula serves it
an endless wave file, paced so the device runs some four seconds behind;
`--cast-lag SECONDS` asks for less, down to what the device keeps of its own.
That lag is measured from where the device says it is, and the spectrum, the
scopes and the position follow the sound being heard rather than the sound
being sent. Screens show a card with the song, the musician and a picture
from Demozoo, the release's `file_id.diz` or the party's logo. Seeking hands
the song to the device again from where the player stands.

The sound is served on port 7373, or the one `--cast-port` names, from the
address the device was reached through; a firewall between the two must let
the device fetch from that port.

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
BSD 2-Clause License. The OctaMED replayer follows the MED loaders and player
of [libxmp](https://github.com/libxmp/libxmp), copyright Claudio Matsuoka and
Hipolito Carraro Jr, distributed under the MIT licence reproduced in
[XMP-LICENSE.txt](XMP-LICENSE.txt). The PowerPacker decruncher follows the
one in libsidplay2 as vendored in JavaMod, copyright Michael Schwendt and
Dag Lem, distributed under the GNU General Public License. The AHX and
HivelyTracker replayer follows HivelyTracker's, copyright Pete Gordon, under
the BSD 3-Clause
License reproduced in [HIVELY-LICENSE.txt](HIVELY-LICENSE.txt). The MO3 reader
follows `Load_mo3.cpp` of [OpenMPT](https://github.com/OpenMPT/openmpt),
copyright the OpenMPT project developers and Olivier Lapicque, distributed
under the BSD 3-Clause License reproduced in
[OPENMPT-LICENSE.txt](OPENMPT-LICENSE.txt); its decompression routines come
from Laurent Clévy's [unmo3](https://github.com/lclevy/unmo3) and were
relicensed with his permission. The Composer 669 replayer follows the same
project's `Load_669.cpp` and the hertz arithmetic of its player. 7z archives
are read with Apache Commons Compress over the XZ for Java library, both under
the Apache License 2.0. RAR archives are read with
[junrar](https://github.com/junrar/junrar), distributed under the UnRAR
license reproduced in [UNRAR-LICENSE.txt](UNRAR-LICENSE.txt), which allows
unpacking RAR archives and forbids re-creating the RAR compression algorithm;
Paula Escobar only unpacks. The eight by eight font the release art is drawn
with on a screen comes from
[font8x8](https://github.com/dhepper/font8x8) by Daniel Hepper, after Marcel
Sondaar, placed in the public domain; `tools/generate-code-page-font`
puts its glyphs in code page order.