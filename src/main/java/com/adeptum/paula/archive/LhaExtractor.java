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

package com.adeptum.paula.archive;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.function.Predicate;
import jp.gr.java_conf.dangan.io.LimitedInputStream;
import jp.gr.java_conf.dangan.util.lha.CompressMethod;
import jp.gr.java_conf.dangan.util.lha.LhaHeader;
import jp.gr.java_conf.dangan.util.lha.LzssInputStream;
import jp.gr.java_conf.dangan.util.lha.PreLh1Decoder;
import jp.gr.java_conf.dangan.util.lha.PreLh5Decoder;
import jp.gr.java_conf.dangan.util.lha.PreLz5Decoder;
import jp.gr.java_conf.dangan.util.lha.PreLzsDecoder;

/**
 * Reads LHA archives with jlha's header parser and decoders wired up by hand; the library's own
 * {@code LhaInputStream} instantiates decoders by class name, which the native image cannot do.
 */
public final class LhaExtractor implements ArchiveExtractor {

    private static final int METHOD_OFFSET = 2;
    private static final int METHOD_PREFIX_LENGTH = 3;
    private static final Set<String> METHOD_PREFIXES = Set.of("-lh", "-lz", "-pm");
    private static final String NAME_ENCODING = StandardCharsets.ISO_8859_1.name();

    @Override
    public boolean matches(byte[] head) {
        return head.length >= METHOD_OFFSET + METHOD_PREFIX_LENGTH
                && METHOD_PREFIXES.contains(new String(head, METHOD_OFFSET, METHOD_PREFIX_LENGTH, StandardCharsets.US_ASCII));
    }

    @Override
    public void extract(Path archive, Path into, Predicate<String> wanted) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(archive))) {
            for (byte[] data = LhaHeader.getFirstHeaderData(in); data != null; data = LhaHeader.getNextHeaderData(in)) {
                final LhaHeader header = new LhaHeader(data, NAME_ENCODING);
                final InputStream compressed = new LimitedInputStream(in, header.getCompressedSize());
                final String name = header.getPath().replace('\\', '/');
                if (wanted.test(name) && !header.getCompressMethod().equals(CompressMethod.LHD)) {
                    final long written = Files.copy(decoder(compressed, header), Archives.target(into, name), StandardCopyOption.REPLACE_EXISTING);
                    if (written != header.getOriginalSize()) {
                        throw new IOException("Truncated LHA entry " + name + " in " + archive.getFileName());
                    }
                }
                compressed.transferTo(OutputStream.nullOutputStream());
            }
        } catch (RuntimeException e) {
            throw new IOException("Corrupt LHA archive " + archive.getFileName() + ": " + e, e);
        }
    }

    private static InputStream decoder(InputStream in, LhaHeader header) throws IOException {
        final String method = header.getCompressMethod();
        final long size = header.getOriginalSize();
        return switch (method) {
            case CompressMethod.LH0, CompressMethod.LZ4 -> in;
            case CompressMethod.LH1 -> new LzssInputStream(new PreLh1Decoder(in), size);
            case CompressMethod.LH4, CompressMethod.LH5, CompressMethod.LH6, CompressMethod.LH7 ->
                    new LzssInputStream(new PreLh5Decoder(in, method), size);
            case CompressMethod.LZS -> new LzssInputStream(new PreLzsDecoder(in), size);
            case CompressMethod.LZ5 -> new LzssInputStream(new PreLz5Decoder(in), size);
            default -> throw new IOException("Unsupported LHA method " + method + " for " + header.getPath());
        };
    }
}
