# The FlexTrax module format

FlexTrax is a soundtracker New Beat wrote for the Atari Falcon030, released in
its last version, 0.9, in September 2000. It saves in two formats: plain
ProTracker when in ProTracker mode, and its own `.flx` when in FlexTrax mode.

An FLX file is a ProTracker module. The header, the sample table, the order
list and the pattern data are laid out exactly as ProTracker lays them out,
and the four-character mark at offset 1080 is one ProTracker itself uses, in
practice `8CHN` for the eight channels FlexTrax mixes. Any player that reads
modules by that mark plays an FLX file without knowing what it is.

What makes it its own format is a block appended after the sample data,
holding the settings for the two real-time effects the Falcon's DSP applied to
the mix: a reverb and a delay. No player has ever read it. This note describes
it, so that Paula can.

## Finding the block

The block begins immediately after the last sample, at

    1084 + patterns * 64 * channels * 4 + sampleBytes

The first four bytes there read `FLEX`; 152 bytes follow, and the file ends.

Two things about that sum are easy to get wrong, and both were found by
computing it for every module in the archive and seeing where it missed.

`patterns` is one more than the highest pattern the order list names anywhere
in its full 128 entries, not merely within the length the song plays. A module
keeps every pattern it was written with, including any the song has since
stopped playing, and one module in the archive stores seven such.

`sampleBytes` counts nothing for a sample the module leaves empty. ProTracker
gives an unused sample a length of one word rather than none, so that the loop
it also carries stays legal, and no data is stored for it. Counting those two
bytes puts the block two bytes further on for every empty sample, and twelve of
the modules in the archive are findable only once that is allowed for, one of
them carrying twenty-six.

Looking for the mark instead of computing where it should be is not safe: one
module in the archive spells `FLEX` in its sample data.

A module saved without effects has no block at all and ends with its samples.
Of the thirty-one FlexTrax modules in the Fujiology archive, twenty-seven carry
the block and four do not, so its absence is ordinary and means no effects
rather than a truncated file.

## The block

The 152 bytes are thirty-eight records of four, big-endian, and the second
byte of every record is zero. The first thirty-one correspond to the module's
thirty-one samples. The last seven hold the effect settings, one value per
record, and six of the seven are the parameters the tracker's own interface
offers.

| Offset in the block | Parameter | Range |
|---|---|---|
| 124 | Reverb decay | 0 to 64 |
| 128 | unused | |
| 132 | Reverb level | 0 to 64 |
| 136 | Delay decay | 0 to 64 |
| 140 | Delay time | 0 to 64 |
| 144 | Delay ping-pong | 0 to 64 |
| 148 | Delay level | 0 to 64 |

Two modules in that archive carry `0x20000000` where the reverb decay belongs,
which is outside anything the tracker's slider can produce, so a reader should
clamp rather than trust the range.

The tracker's manual describes what the musician was setting. Reverb decay
sets how long the tail runs, reverb level its volume over every sample in use.
Delay decay sets the fade of the repeats, level their volume, time the gap
between them, and ping-pong how far the right channel trails the left: none at
zero, and at maximum half the delay time.

## What the tracker does with them

Before playing, FlexTrax hands the DSP seven 24-bit words derived from the
block. The transforms matter, because they are where the stored 0 to 64
becomes a coefficient.

| Word | From | Value |
|---|---|---|
| 1 | | always zero |
| 2 | reverb level | value × 147456 |
| 3 | reverb decay | value × 135447 + 500000 |
| 4 | delay decay | value × 131072 |
| 5 | delay time | value × 78 + 2008 |
| 6 | delay ping-pong | word 5 × value ÷ 128 |
| 7 | delay level | value × 131072 |

Words three, four and seven are fractions of 2^23, so a stored 64 becomes
exactly 1.0 for the two levels that scale by 131072. Word five is a count of
samples, from 2008 to 6800, and word six is that count scaled to at most half
of it.

## What the DSP does with them

The delay is one ring buffer whose length is word five, read at two taps: the
write position, and a second tap word six behind it, which is what makes the
right channel trail the left. Each pass multiplies what comes out of the buffer
by the delay decay, adds the dry signal, and writes the sum back, so the
repeats fade by that coefficient. Both taps are then multiplied by the delay
level to give what the effect contributes to the mix.

The reverb is a comb network whose coefficients sit in a table, into which the
reverb decay is written for both channels, and whose summed output is scaled
by the reverb level before it joins the mix.

## Replay rate

The delay time is a count of samples, so what it means in seconds depends on
the rate the Falcon was replaying at. FlexTrax drives the machine's clock
divider at either 1 or 2, which is 49170 Hz or 32780 Hz, and the module records
which was in use nowhere. A player at another rate has to scale the sample
count, and has to choose one of the two as the rate the count was meant for.

## Sources

Everything here was read out of FlexTrax 0.9 itself, from the manual shipped
with it and from its program and DSP binaries, and checked against the
thirty-one FlexTrax modules the Fujiology archive holds.
