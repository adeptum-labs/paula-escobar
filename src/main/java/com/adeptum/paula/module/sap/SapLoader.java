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

package com.adeptum.paula.module.sap;

import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.ModuleMetadata;
import com.adeptum.paula.module.UnsupportedModuleException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.sf.asap.ASAPFormatException;
import net.sf.asap.ASAPInfo;

/**
 * Loads Atari 8-bit tunes, SAP files and the native modules of the Atari trackers alike, through ASAP.
 */
@Slf4j
public final class SapLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT = new ModuleFormat("sap", "Atari 8-bit tunes (ASAP)", Set.of(
            "sap", "cmc", "cm3", "cmr", "cms", "dmc", "dlt", "mpt", "md1", "md2", "mpd", "rmt", "tmc", "tm8", "tm2",
            "fc", "d15", "d8"));

    /**
     * A tune without a TIME tag plays for as long as an unlisted SID does.
     */
    static final Duration DEFAULT_LENGTH = Duration.ofMinutes(3);

    private static final int CHANNELS_PER_POKEY = 4;
    private static final String SUBTUNES = "subtunes";
    private static final String ENGINE = " (ASAP)";
    private static final String NOT_A_TUNE = "not an Atari 8-bit tune";

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
        final String name = nameForAsap(path.getFileName().toString());
        final ASAPInfo info = new ASAPInfo();
        try {
            info.load(name, file, file.length);
        } catch (ASAPFormatException e) {
            throw new UnsupportedModuleException(path, e.getMessage());
        } catch (RuntimeException e) {
            log.debug("ASAP rejected {}", path, e);
            throw new UnsupportedModuleException(path, NOT_A_TUNE);
        }
        final int song = info.getDefaultSong();
        return new SapModule(path, metadata(info, name), file, song, length(info, song));
    }

    /**
     * ASAP reads the format off the extension after the last dot, so a name the Modland way round, SAP.tune,
     * is turned back before it is handed over.
     */
    static String nameForAsap(String fileName) {
        final int dot = fileName.indexOf('.');
        if (dot < 1 || FORMAT.extensions().contains(ModuleFormat.extensionOf(fileName))) {
            return fileName;
        }
        return fileName.substring(dot + 1) + "." + fileName.substring(0, dot).toLowerCase(Locale.ROOT);
    }

    private static ModuleMetadata metadata(ASAPInfo info, String name) {
        return ModuleMetadata.builder()
                .title(info.getTitle())
                .format(new ModuleFormat(FORMAT.id(), formatName(info, name), FORMAT.extensions()))
                .channels(CHANNELS_PER_POKEY * info.getChannels())
                .songLength(info.getSongs())
                .lengthUnit(SUBTUNES)
                .credits(Stream.of(info.getAuthor(), info.getDate()).filter(text -> !text.isBlank()).toList())
                .build();
    }

    /**
     * A SAP file is named after the tracker the tune came out of where ASAP can tell, a native module after
     * its own extension.
     */
    private static String formatName(ASAPInfo info, String name) {
        final String extension = Objects.requireNonNullElse(info.getOriginalModuleExt(), ModuleFormat.extensionOf(name));
        try {
            return ASAPInfo.getExtDescription(extension) + ENGINE;
        } catch (ASAPFormatException e) {
            return FORMAT.name();
        }
    }

    private static Duration length(ASAPInfo info, int song) {
        final int millis = info.getDuration(song);
        return millis < 0 ? DEFAULT_LENGTH : Duration.ofMillis(millis);
    }
}
