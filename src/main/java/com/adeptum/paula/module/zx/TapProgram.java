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

package com.adeptum.paula.module.zx;

import com.adeptum.paula.module.UnsupportedModuleException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a tape puts into memory and where it starts: the machine-code blocks at the addresses the loader names or
 * their headers carry, the entry the loader jumps to, and the RAMTOP the stack sits under.
 */
record TapProgram(String title, int ramTop, int entry, List<Segment> segments) {

    static final int DEFAULT_RAM_TOP = 0xFF57;

    record Segment(int address, byte[] data) {
    }

    static TapProgram of(Path path, TapFile tape) throws UnsupportedModuleException {
        final List<TapPart> parts = tape.parts();
        final Optional<TapPart> basic = parts.stream().filter(part -> part.header().type() == TapHeader.PROGRAM).findFirst();
        final List<TapPart> code = parts.stream().filter(part -> part.header().type() == TapHeader.CODE).toList();
        if (code.isEmpty()) {
            throw new UnsupportedModuleException(path, "the tape holds no machine code");
        }
        final BasicProgram loader = basic.map(part -> BasicProgram.parse(part.data())).orElse(null);
        final List<Segment> segments = new ArrayList<>();
        for (int i = 0; i < code.size(); i++) {
            final int address = loader != null && i < loader.loads().size() && loader.loads().get(i).isPresent()
                    ? loader.loads().get(i).getAsInt() : code.get(i).header().param1();
            segments.add(new Segment(address, code.get(i).data()));
        }
        final int entry = loader == null ? segments.get(0).address() : loader.entry().orElseThrow(
                () -> new UnsupportedModuleException(path, "the loader does not start any machine code"));
        final int ramTop = loader == null ? DEFAULT_RAM_TOP : loader.clear().orElse(DEFAULT_RAM_TOP);
        final String title = basic.orElse(code.get(0)).header().name();
        return new TapProgram(title, ramTop, entry, List.copyOf(segments));
    }
}
