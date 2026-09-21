# Paula Escobar

[![Latest release](https://img.shields.io/github/v/release/adeptum-labs/paula-escobar.svg?style=flat-square)](https://github.com/adeptum-labs/paula-escobar/releases/latest)
[![Tests](https://img.shields.io/github/actions/workflow/status/adeptum-labs/paula-escobar/tests.yml?branch=master&label=tests&style=flat-square)](https://github.com/adeptum-labs/paula-escobar/actions/workflows/tests.yml)
[![License](https://img.shields.io/github/license/adeptum-labs/paula-escobar.svg?style=flat-square)](https://github.com/adeptum-labs/paula-escobar/blob/master/LICENSE)

A terminal music player for demoscene and chip music, named after the Amiga's
sound chip. It plays tracker modules, SID and Atari tunes and streamed audio,
and it opens the party archives: the music competitions of eighty-two party
series and the [ModArchive](https://modarchive.org) charts, browsed year by
year and played on the spot. Everything happens in the terminal, and the build
produces a native executable with GraalVM, so there is no JVM to start and no
jar to carry around.

## Features

- Tracker modules, chip music and streamed audio of the Amiga, the PC, the
  Commodore 64, the Atari 8-bit, ST and Falcon and the ZX Spectrum, every
  format listed on the
  [Formats](https://github.com/adeptum-labs/paula-escobar/wiki/Formats) page.
- The music competitions of eighty-two party series on Demozoo and the
  ModArchive charts, browsed year by year, played on the spot and kept for
  offline use once fetched.
- Archives, crunched modules, Unreal packages, Amiga disk images and
  Commodore disk and tape images opened for what they hold, as deep as
  they go.
- Screens that fill the terminal in 24-bit colour: a spectrum analyser,
  waterfall and vectorscope, VU meters and an oscilloscope per channel, each
  muted or soloed with the mouse.
- Sound through WASAPI, CoreAudio, PulseAudio, ALSA or JACK, recorded to a
  wave file, or played over ssh, as the
  [Audio output](https://github.com/adeptum-labs/paula-escobar/wiki/Audio-output)
  page describes.
- Casting to a Google Cast device on the network and back again without
  stopping, described on the
  [Casting](https://github.com/adeptum-labs/paula-escobar/wiki/Casting) page.

## The screens

![The player, with the song details and the message the musician left in the module on the left, and the spectrum analyser and one oscilloscope per channel on the right](docs/player-screen.png)

Both screens fill the terminal in 24-bit colour, or 256 or 16 where that is
all there is. The player shows the song details on the left, with the
sounding instrument lit up, and on the right a spectrum analyser, one
braille-dot oscilloscope per channel, a position bar and VU meters; `v`
turns the upper panel to a waterfall and then to a vectorscope. The browser
colours the first three placings gold, silver and bronze and keeps a small
spectrum strip of what is playing above the key bar.

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
matter. `mvn test` runs the unit tests.

## Usage

```
paula                        browse the party archives
paula song.mod another.mod   play the files in order
paula info song.mod          print metadata
paula formats                list supported formats
paula --help
```

`?` shows the keys on either screen, and the
[Keys](https://github.com/adeptum-labs/paula-escobar/wiki/Keys) page of the
wiki lists them all, with what the mouse does.

### Browsing

The party series sit in columns on the left and the charts on the right;
`tab` switches sides. The series run from The Party, Assembly and The
Gathering through Breakpoint and Revision to Swedish Icing, the Polish
classics and X, the Commodore 64's own party. A series opens into its
parties, a party into its music competitions and a competition into the
ranked entries, each with its title, its author and whatever is the matter
with it: dimmed for executable music Paula cannot run, `(no download)` where
Demozoo has no file, `(no reader)` where the file is an ARJ or ACE archive
nothing here opens, and `(unsupported music format)` once a file has been
fetched and turned out to hold nothing playable, such as an Amiga
executable. A chart opens into All, MOD, XM, IT, S3M and Other and is read
forty rows at a time as the cursor reaches the end. Playing a line queues
the rest of its list, so `n` walks on through the results, and `m` opens
everything else the musician has, on Demozoo for an entry and on
ModArchive for a chart tune.

Party data comes from [Demozoo](https://demozoo.org) and the files from
scene.org, ModArchive or Modland. Zip, 7z, RAR, LHA and LZX archives, XPK
and PowerPacker crunched modules, Unreal music packages, Amiga disk images
and Commodore disk and tape images are unpacked as deep as they go, and
the file named after the entry is played, with the art of its
`file_id.diz` or the party's own logo shown above the list. Everything
fetched is kept under `~/.cache/paula` (or `$XDG_CACHE_HOME/paula`):
Demozoo answers a week and chart pages a day, both still used offline,
downloaded modules for good. `r` fetches the list in view afresh; delete
the directory to start over.

### Formats

`paula formats` prints what plays, and a file named the way Modland names
them, `MOD.tune`, counts as well.

| Machine | Music |
|---|---|
| Amiga | ProTracker, OctaMED, DigiBooster Pro, AHX and HivelyTracker modules |
| PC | Fast Tracker, Scream Tracker, Impulse Tracker, MultiTracker, Farandole, Composer 669, X-Tracker and UltraTracker modules, and MO3 files |
| Commodore 64 | SID tunes and programs |
| Atari 8-bit | SAP tunes and the native trackers' modules |
| Atari ST and Falcon | FlexTrax modules and YM recordings |
| ZX Spectrum | SoundTracker, Pro Tracker 2 and 3 and Pro Sound Creator modules, and AY recordings |
| Any | MPEG audio, FLAC, Ogg Vorbis, Monkey's Audio, wave, AIFF and AU |

Zip, 7z, RAR, LHA, LZX and gzip archives, PowerPacker and XPK crunched
modules, Unreal music packages, Amiga disk images and Commodore 1541 disk
and tape images are opened for what they hold. The
[Formats](https://github.com/adeptum-labs/paula-escobar/wiki/Formats) page
of the wiki lists every format with its extensions.

## License

Copyright © 2026 Adam Waldenberg, Adeptum AB. Licensed under the GNU General Public License,
version 3 or later. See [LICENSE](LICENSE), and
[LICENSE.addendum](LICENSE.addendum) for the additional permission that
covers linking the RAR reader. The libraries Paula Escobar is built on and
the projects its readers and replayers follow are credited, with their
licences, in the [Licenses](https://github.com/adeptum-labs/paula-escobar/wiki/Licenses) page of the wiki.
