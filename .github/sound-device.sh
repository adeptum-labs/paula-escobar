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
# Gives a macOS runner a sound device, which it has none of. BlackHole is
# a loopback device that lives in a Core Audio HAL plug-in, so a fresh
# coreaudiod is all it takes to appear; the reboot the cask asks for is
# not needed for that.

set -euo pipefail

readonly DEVICE='BlackHole 2ch'

brew install --cask blackhole-2ch
sudo killall coreaudiod || echo 'Core Audio was not running; it starts with the plug-in in place.'

for _ in $(seq 1 30); do
  if system_profiler SPAudioDataType | grep -q "$DEVICE"; then
    system_profiler SPAudioDataType
    exit 0
  fi
  sleep 1
done
echo "$DEVICE did not appear after restarting Core Audio." >&2
system_profiler SPAudioDataType >&2
exit 1
