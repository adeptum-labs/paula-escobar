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

package com.adeptum.paula.module.ay;

import com.adeptum.paula.archive.LhaExtractor;
import com.adeptum.paula.module.Module;
import com.adeptum.paula.module.ModuleFormat;
import com.adeptum.paula.module.ModuleLoader;
import com.adeptum.paula.module.UnsupportedModuleException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

/**
 * Loads a recording of what a tune wrote to the AY-3-8910, off a ZX Spectrum as a PSG or off an Atari ST as a
 * YM. Neither holds a tracker's patterns: they are the registers themselves, taken down at each interrupt,
 * which is why anything that ever played on the chip can be kept this way.
 */
public final class AyLoader implements ModuleLoader {

    public static final ModuleFormat FORMAT =
            new ModuleFormat("ay", "AY register recordings", Set.of("psg", "ym", "vtx"));

    private static final String PSG_MARK = "PSG";

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
        try {
            return AyModule.of(path, framesOf(Files.readAllBytes(path)));
        } catch (IOException e) {
            throw new UnsupportedModuleException(path, e.getMessage());
        }
    }

    /**
     * A YM is nearly always packed into an LHA archive of its own, so a file carrying no mark it knows is
     * unwrapped and offered again.
     */
    private static RegisterFrames framesOf(byte[] file) throws IOException {
        final Optional<RegisterFrames> plain = byMark(file);
        if (plain.isPresent()) {
            return plain.get();
        }
        return byMark(LhaExtractor.only(file))
                .orElseThrow(() -> new IOException("Nothing the AY ever played is inside the wrapper"));
    }

    /**
     * A VTX may be marked YM as well, so the block form is looked for first: it is the one that follows its
     * mark with a version digit.
     */
    private static Optional<RegisterFrames> byMark(byte[] file) throws IOException {
        if (startsWith(file, PSG_MARK)) {
            return Optional.of(PsgReader.read(file));
        }
        if (YmReader.marksABlock(file)) {
            return Optional.of(YmReader.read(file));
        }
        if (VtxReader.marksARecording(file)) {
            return Optional.of(VtxReader.read(file));
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] file, String mark) {
        if (file.length < mark.length()) {
            return false;
        }
        for (int at = 0; at < mark.length(); at++) {
            if (file[at] != mark.charAt(at)) {
                return false;
            }
        }
        return true;
    }
}
