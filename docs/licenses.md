# Licenses

Paula Escobar is copyright © 2026 Adam Waldenberg, Adeptum AB, and licensed
under the GNU General Public License, version 3 or later, as set out in
[LICENSE](../LICENSE). [LICENSE.addendum](../LICENSE.addendum) grants the
additional permission that covers linking the RAR reader.

It is built on, and follows, the work of others. This page lists that work
and the terms it comes under.

## Libraries

- **JavaMod** is copyright Daniel Becker and licensed under the GNU General
  Public License, version 3.
- **ASAP** is copyright Piotr Fusik and licensed under the GNU General Public
  License, version 2 or later, used here under version 3.
- **LHA archives** are read with the LHA Library for Java, copyright Michel
  Ishizuka, distributed under the BSD 2-Clause License reproduced in
  [lib/JLHA-LICENSE.txt](../lib/JLHA-LICENSE.txt).
- **7z archives** are read with Apache Commons Compress over the XZ for Java
  library, both under the Apache License 2.0.
- **RAR archives** are read with [junrar](https://github.com/junrar/junrar),
  distributed under the UnRAR license reproduced in
  [UNRAR-LICENSE.txt](../UNRAR-LICENSE.txt), which allows unpacking RAR
  archives and forbids re-creating the RAR compression algorithm; Paula
  Escobar only unpacks.

## Code ported from other projects

- **LZX:** the decoder follows the implementation in
  [XADMaster](https://github.com/MacPaw/XADMaster), copyright MacPaw Inc.,
  licensed under the GNU Lesser General Public License version 2.1 or later
  and used under the GPL as that licence permits.
- **XPK:** the unpacker follows Teemu Suutari's
  [ancient](https://github.com/temisu/ancient), distributed under the BSD
  2-Clause License.
- **OctaMED:** the replayer follows the MED loaders and player of
  [libxmp](https://github.com/libxmp/libxmp), copyright Claudio Matsuoka and
  Hipolito Carraro Jr, distributed under the MIT licence reproduced in
  [XMP-LICENSE.txt](../XMP-LICENSE.txt).
- **PowerPacker:** the decruncher follows the one in libsidplay2 as vendored
  in JavaMod, copyright Michael Schwendt and Dag Lem, distributed under the
  GNU General Public License.
- **AHX and HivelyTracker:** the replayer follows HivelyTracker's, copyright
  Pete Gordon, under the BSD 3-Clause License reproduced in
  [HIVELY-LICENSE.txt](../HIVELY-LICENSE.txt).
- **MO3:** the reader follows `Load_mo3.cpp` of
  [OpenMPT](https://github.com/OpenMPT/openmpt), copyright the OpenMPT project
  developers and Olivier Lapicque, distributed under the BSD 3-Clause License
  reproduced in [OPENMPT-LICENSE.txt](../OPENMPT-LICENSE.txt); its
  decompression routines come from Laurent Clévy's
  [unmo3](https://github.com/lclevy/unmo3) and were relicensed with his
  permission.
- **Composer 669:** the replayer follows OpenMPT's `Load_669.cpp` and the
  hertz arithmetic of its player.
- **X-Tracker:** the reader follows OpenMPT's `Load_dmf.cpp` for the file
  layout and the packed samples.
- **UltraTracker:** modules are rewritten as Impulse Tracker ones the way
  OpenMPT's `Load_ult.cpp`, a port of Storlek's reader from Schism Tracker,
  does it.
- **AY-3-8910 and YM2149:** the chips are emulated by a port of Peter
  Sovietov's [ayumi](https://github.com/true-grue/ayumi), distributed under
  the MIT License.
- **ZX Spectrum trackers:** the SoundTracker, Pro Tracker 2, Pro Tracker 3 and
  Pro Sound Creator readers and players follow Vitamin's
  [ZXTune](https://github.com/vitamin-caig/zxtune), distributed under the GNU
  Lesser General Public License version 3, and take their note and volume
  tables from it. A Pro Tracker 3.4 module is tuned, and a Pro Tracker 2
  module's volumes scaled, the way recordings of such modules sound rather
  than the way ZXTune works them out, and a Pro Sound Creator module glides,
  rests and breaks out of its loops the way Sergey Bulba's AY Emulator plays
  it.

## Other material

- **Font:** the eight by eight font the release art is drawn with on a screen
  comes from [font8x8](https://github.com/dhepper/font8x8) by Daniel Hepper,
  after Marcel Sondaar, placed in the public domain;
  `tools/generate-code-page-font` puts its glyphs in code page order.
