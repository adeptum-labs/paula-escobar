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

package com.adeptum.paula.module.ape;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import de.quippy.jmac.decoder.IAPEDecompress;
import de.quippy.jmac.info.APETag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the lossless streams of Monkey's Audio through the decoder the JavaMod jar carries. Its own container
 * is left alone, as the other sampled formats leave theirs: it hands out window toolkit panels the executable
 * has not got.
 */
public final class ApeLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT =
            new ModuleFormat("ape", "Monkey's Audio", Set.of("ape", "apl", "mac"));

    private static final String SECONDS = "seconds";

    @Override
    public ModuleFormat format() {
        return FORMAT;
    }

    @Override
    public boolean supports(Path path) {
        return FORMAT.matches(path.getFileName().toString());
    }

    @Override
    public Module load(Path path) throws IOException {
        final byte[] file = Files.readAllBytes(path);
        final String name = path.getFileName().toString();
        final IAPEDecompress decoder = open(path, file, name);
        final ApeAudio audio = audioOf(path, decoder);
        return new ApeModule(path, metadata(decoder.getApeInfoTag(), audio), file, audio);
    }

    private static IAPEDecompress open(Path path, byte[] file, String name) throws IOException {
        try {
            return ApeAudio.open(file, name);
        } catch (IOException | RuntimeException e) {
            throw new UnsupportedModuleException(path, "not Monkey's Audio");
        }
    }

    private static ApeAudio audioOf(Path path, IAPEDecompress decoder) throws UnsupportedModuleException {
        final ApeAudio audio = ApeAudio.of(decoder);
        if (!audio.isPlayable()) {
            throw new UnsupportedModuleException(path, "unplayable Monkey's Audio stream of " + audio.describe());
        }
        return audio;
    }

    /**
     * The tags a file carries about itself go where a module's credits would, since that is where the player
     * shows the lines a format offers beyond its title.
     */
    private static ModuleMetadata metadata(APETag tag, ApeAudio audio) {
        final List<String> credits = new ArrayList<>();
        addUnlessBlank(credits, field(tag, APETag.APE_TAG_FIELD_ARTIST));
        addUnlessBlank(credits, field(tag, APETag.APE_TAG_FIELD_ALBUM));
        addUnlessBlank(credits, field(tag, APETag.APE_TAG_FIELD_YEAR));
        credits.add(audio.describe());
        return ModuleMetadata.builder()
                .title(field(tag, APETag.APE_TAG_FIELD_TITLE))
                .format(FORMAT)
                .channels(audio.channels())
                .songLength(audio.seconds())
                .lengthUnit(SECONDS)
                .credits(credits)
                .build();
    }

    private static String field(APETag tag, String name) {
        try {
            final String value = tag == null ? null : tag.GetFieldString(name);
            return value == null ? "" : value.trim();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static void addUnlessBlank(List<String> credits, String line) {
        if (!line.isBlank()) {
            credits.add(line);
        }
    }
}
