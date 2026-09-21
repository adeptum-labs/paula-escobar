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

import java.io.IOException;

/**
 * A track with too little content to earn a real table names its one symbol directly instead of building a
 * tree for it, so decoding it costs no bits at all.
 */
final class HeavyTable {

    private final PrefixCode code;
    private final int constant;

    private HeavyTable(PrefixCode code, int constant) {
        this.code = code;
        this.constant = constant;
    }

    static HeavyTable of(PrefixCode code) {
        return new HeavyTable(code, 0);
    }

    static HeavyTable constant(int symbol) {
        return new HeavyTable(null, symbol);
    }

    int decode(BitReader bits) throws IOException {
        return code == null ? constant : code.decode(bits);
    }
}
