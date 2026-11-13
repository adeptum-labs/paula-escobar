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

package com.adeptum.paula.demozoo.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ChecksumsTest {

    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final String NAME = "demozoo-catalogue-v1.bin.gz";

    @Test
    void sha256OfAbcIsTheKnownValue() {
        assertEquals(ABC, Checksums.sha256("abc".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void lineIsTheChecksumTwoSpacesTheNameAndANewline() {
        assertEquals(ABC + "  " + NAME + "\n", Checksums.line(ABC, NAME));
    }

    @Test
    void parseReadsABareChecksum() throws IOException {
        assertEquals(ABC, Checksums.parse(ABC));
    }

    @Test
    void parseReadsASha256sumLine() throws IOException {
        assertEquals(ABC, Checksums.parse(Checksums.line(ABC, NAME)));
    }

    @Test
    void parseReadsAnUpperCaseChecksum() throws IOException {
        assertEquals(ABC, Checksums.parse(ABC.toUpperCase(Locale.ROOT)));
    }

    @Test
    void parseReadsThroughTheWhitespaceAroundIt() throws IOException {
        assertEquals(ABC, Checksums.parse("  \n" + Checksums.line(ABC, NAME) + "\n\n"));
    }

    @Test
    void parseRejectsAChecksumThatIsTooShort() {
        assertThrows(IOException.class, () -> Checksums.parse(ABC.substring(1)));
    }

    @Test
    void parseRejectsTextThatIsNotAHexadecimalChecksum() {
        assertThrows(IOException.class, () -> Checksums.parse("not the checksum"));
    }
}
