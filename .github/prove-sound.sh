#!/usr/bin/env bash
#
# Paula Escobar is a terminal music player for demoscene and chip music.
# Copyright © 2026 Adam Waldenberg, Adeptum AB, Org.nr 559494-1824.
#
# This program is free software: you can redistribute it and/or modify it
# under the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.
#
# This program is distributed in the hope that it will be useful, but
# WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
# or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
# more details.
#
# You should have received a copy of the GNU General Public License along
# with this program. If not, see <https://www.gnu.org/licenses/>.
#
# Website: https://www.adeptum.se
# Contact: info@adeptum.se
#
# Plays the test module through the named backend for a few seconds while
# recording it, and checks that the sound went where it should: the log
# names the backend, nothing failed, and the recording holds sound of
# about the length played, which a device that never took any could not
# have paced the player to.

set -euo pipefail

readonly BINARY=$1 BACKEND=$2 BACKEND_NAME=$3
readonly MODULE=src/test/resources/mod/paula-test.mod
readonly RECORDING=played.wav
readonly SECONDS_PLAYED=4 RATE=48000 WAVE_HEADER_BYTES=44 BYTES_PER_FRAME=4

: > paula.log
rm -f "$RECORDING"
"$BINARY" --output "$BACKEND" --rate $RATE --record "$RECORDING" --quit-after $SECONDS_PLAYED "$MODULE" < /dev/null > /dev/null

grep -q "Playing through $BACKEND_NAME at $RATE Hz" paula.log || { cat paula.log; echo "Playback through $BACKEND_NAME did not start."; exit 1; }
! grep -q ' ERROR ' paula.log || { cat paula.log; exit 1; }

frames=$(( ($(wc -c < "$RECORDING") - WAVE_HEADER_BYTES) / BYTES_PER_FRAME ))
sounding=$(tail -c +$((WAVE_HEADER_BYTES + 1)) "$RECORDING" | tr -d '\0' | wc -c)
echo "Recorded $((frames / RATE)) s ($frames frames), $sounding bytes of it not silence."
[ "$sounding" -gt 0 ] || { echo 'The recording is all silence.'; exit 1; }
[ "$frames" -ge $((RATE * (SECONDS_PLAYED - 1))) ] || { echo 'Less was recorded than was played.'; exit 1; }
[ "$frames" -le $((RATE * SECONDS_PLAYED * 2)) ] || { echo 'Far more was recorded than was played, so nothing paced the player.'; exit 1; }
