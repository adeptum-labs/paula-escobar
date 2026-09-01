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

package com.adeptum.paula.module.protracker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;

public final class ProTrackerLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT = new ModuleFormat("mod", "ProTracker / NoiseTracker", Set.of("mod"));

    static final int TITLE_LENGTH = 20;
    static final int SAMPLE_COUNT = 31;
    static final int SAMPLE_HEADER_LENGTH = 30;
    static final int SAMPLE_NAME_LENGTH = 22;
    static final int SONG_LENGTH_OFFSET = 950;
    static final int MAGIC_OFFSET = 1080;
    static final int HEADER_LENGTH = MAGIC_OFFSET + 4;

    private static final Map<String, Integer> CHANNELS_BY_MAGIC = Map.of(
            "M.K.", 4, "M!K!", 4, "FLT4", 4, "4CHN", 4,
            "6CHN", 6, "8CHN", 8, "FLT8", 8, "CD81", 8);

    @Override
    public ModuleFormat format() {
        return FORMAT;
    }

    @Override
    public boolean supports(Path path) {
        final String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".mod") || name.startsWith("mod.");
    }

    @Override
    public Module load(Path path) throws IOException {
        return new ProTrackerModule(path, parseHeader(path, Files.readAllBytes(path)));
    }

    static ModuleMetadata parseHeader(Path path, byte[] data) throws UnsupportedModuleException {
        if (data.length < HEADER_LENGTH) {
            throw new UnsupportedModuleException(path, "file is shorter than a ProTracker header");
        }
        final String magic = ascii(data, MAGIC_OFFSET, 4);
        final Integer channels = CHANNELS_BY_MAGIC.get(magic);
        if (channels == null) {
            throw new UnsupportedModuleException(path, "unknown channel signature '" + magic + "'");
        }
        return ModuleMetadata.builder()
                .title(ascii(data, 0, TITLE_LENGTH))
                .format(FORMAT)
                .channels(channels)
                .songLength(data[SONG_LENGTH_OFFSET] & 0xFF)
                .instruments(sampleNames(data))
                .build();
    }

    private static List<String> sampleNames(byte[] data) {
        return IntStream.range(0, SAMPLE_COUNT)
                .mapToObj(i -> ascii(data, TITLE_LENGTH + i * SAMPLE_HEADER_LENGTH, SAMPLE_NAME_LENGTH))
                .toList();
    }

    private static String ascii(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.ISO_8859_1).replace('\0', ' ').strip();
    }
}
