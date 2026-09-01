/*
 * Paula is a terminal music player for demoscene and chip music.
 * Copyright © 2026 Adeptum AB, Org.nr 559494-1824.
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

package com.adeptum.paula.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs({OS.LINUX, OS.MAC})
class CommandAudioSinkTest {

    @Test
    void streamsLittleEndianPcmToTheCommand(@TempDir Path dir) throws Exception {
        final Path capture = dir.resolve("pcm.raw");
        final CommandAudioSink sink = new CommandAudioSink(rate -> List.of("sh", "-c", "cat > " + capture));

        sink.open(48000);
        sink.write(new short[] {0x0102, (short) 0xFFFE, 0x7FFF, (short) 0x8000}, 2);
        sink.close();

        assertArrayEquals(new byte[] {0x02, 0x01, (byte) 0xFE, (byte) 0xFF, (byte) 0xFF, 0x7F, 0x00, (byte) 0x80},
                Files.readAllBytes(capture));
    }

    @Test
    void reportsMissingCommand() {
        final CommandAudioSink sink = new CommandAudioSink(rate -> List.of("paula-no-such-audio-command"));
        assertThrows(AudioException.class, () -> sink.open(48000));
    }
}
