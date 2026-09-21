/*
 * Paula Escobar is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adam Waldenberg, Adeptum AB, Org.nr 559494-1824.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Website: https://www.adeptum.se
 * Contact: info@adeptum.se
 */

package com.adeptum.paula.archive.dms;

/**
 * One track header: the cylinder it holds (0-79 for the disk, 80 for file_id.diz), where its packed bytes lie
 * and how long they run, the length after the first stage where a second one follows, the length once fully
 * unpacked, the compression mode and the flags that say whether to keep the running state or start it fresh,
 * and the additive checksum the unpacked bytes must sum to.
 */
record DmsTrack(int number, int flags, int mode, int packedOffset, int packedLength, int intermediateLength,
        int unpackedLength, int checksum) {
}
