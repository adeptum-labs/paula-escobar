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

package com.adeptum.paula.module.composer669;

import java.util.List;

/**
 * A Composer 669 module as read from disk, or the extended one UNIS 669 writes: the three-line message the
 * tracker shows in place of a title, the samples, the patterns and the order they play in.
 */
record C669File(boolean extended, List<String> message, List<C669Sample> samples, List<C669Pattern> patterns,
                int[] orders, int restart) {

    C669File {
        message = List.copyOf(message);
        samples = List.copyOf(samples);
        patterns = List.copyOf(patterns);
    }

    String title() {
        return message.get(0);
    }

    C669Pattern patternAt(int order) {
        return order >= 0 && order < orders.length ? patterns.get(orders[order]) : null;
    }

    C669Sample sample(int number) {
        return number >= 0 && number < samples.size() ? samples.get(number) : null;
    }
}
