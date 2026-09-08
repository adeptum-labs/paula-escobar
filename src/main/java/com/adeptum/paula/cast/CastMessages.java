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

package com.adeptum.paula.cast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The envelope every Cast message travels in: a protocol buffer of a version, who it is from and to, the
 * namespace it belongs to and a payload, which for everything Paula says is a string of JSON.
 *
 * <p>The buffer has seven fields and Paula uses six, so they are written and read here by hand rather than
 * through a protocol buffer library: a field is a varint tag of its number and wire type, then a varint for a
 * number or a length and the bytes for a string.</p>
 */
public final class CastMessages {

    public static final String SENDER = "sender-0";
    public static final String RECEIVER = "receiver-0";

    public static final String CONNECTION = "urn:x-cast:com.google.cast.tp.connection";
    public static final String HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat";
    public static final String RECEIVER_NAMESPACE = "urn:x-cast:com.google.cast.receiver";
    public static final String MEDIA = "urn:x-cast:com.google.cast.media";

    private static final int PROTOCOL_VERSION = 1;
    private static final int SOURCE = 2;
    private static final int DESTINATION = 3;
    private static final int NAMESPACE = 4;
    private static final int PAYLOAD_TYPE = 5;
    private static final int PAYLOAD_UTF8 = 6;
    private static final int PAYLOAD_BINARY = 7;

    private static final int WIRE_VARINT = 0;
    private static final int WIRE_LENGTH = 2;
    private static final int VERSION_CASTV2 = 0;
    private static final int PAYLOAD_STRING = 0;
    private static final int CONTINUES = 0x80;
    private static final int SEVEN_BITS = 0x7F;
    private static final int TAG_SHIFT = 3;
    private static final int WIRE_MASK = 0x07;

    private CastMessages() {
    }

    /**
     * One message, addressed and named: what goes over the wire once framed with its length.
     */
    public record CastMessage(String source, String destination, String namespace, String payload) {

        public byte[] encode() {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            varint(out, PROTOCOL_VERSION, VERSION_CASTV2);
            string(out, SOURCE, source);
            string(out, DESTINATION, destination);
            string(out, NAMESPACE, namespace);
            varint(out, PAYLOAD_TYPE, PAYLOAD_STRING);
            string(out, PAYLOAD_UTF8, payload);
            return out.toByteArray();
        }
    }

    public static CastMessage decode(byte[] encoded) throws IOException {
        String source = "";
        String destination = "";
        String namespace = "";
        String payload = "";
        int at = 0;
        while (at < encoded.length) {
            final long tag = readVarint(encoded, at);
            at += varintLength(encoded, at);
            final int field = (int) (tag >>> TAG_SHIFT);
            final int wire = (int) (tag & WIRE_MASK);
            if (wire == WIRE_VARINT) {
                at += varintLength(encoded, at);
                continue;
            }
            if (wire != WIRE_LENGTH) {
                throw new IOException("A Cast message carries a field of a kind this does not read");
            }
            final int length = (int) readVarint(encoded, at);
            at += varintLength(encoded, at);
            if (length < 0 || at + length > encoded.length) {
                throw new IOException("A Cast message ends in the middle of a field");
            }
            final String text = new String(encoded, at, length, StandardCharsets.UTF_8);
            switch (field) {
                case SOURCE -> source = text;
                case DESTINATION -> destination = text;
                case NAMESPACE -> namespace = text;
                case PAYLOAD_UTF8, PAYLOAD_BINARY -> payload = text;
                default -> { }
            }
            at += length;
        }
        return new CastMessage(source, destination, namespace, payload);
    }

    private static void string(ByteArrayOutputStream out, int field, String text) {
        final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarint(out, (long) field << TAG_SHIFT | WIRE_LENGTH);
        writeVarint(out, bytes.length);
        out.writeBytes(bytes);
    }

    private static void varint(ByteArrayOutputStream out, int field, long value) {
        writeVarint(out, (long) field << TAG_SHIFT | WIRE_VARINT);
        writeVarint(out, value);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        long left = value;
        do {
            final int low = (int) (left & SEVEN_BITS);
            left >>>= 7;
            out.write(left == 0 ? low : low | CONTINUES);
        } while (left != 0);
    }

    private static long readVarint(byte[] bytes, int at) throws IOException {
        long value = 0;
        for (int shift = 0, index = at; index < bytes.length && shift < Long.SIZE; index++, shift += 7) {
            value |= (long) (bytes[index] & SEVEN_BITS) << shift;
            if ((bytes[index] & CONTINUES) == 0) {
                return value;
            }
        }
        throw new IOException("A Cast message ends in the middle of a number");
    }

    private static int varintLength(byte[] bytes, int at) throws IOException {
        for (int index = at; index < bytes.length; index++) {
            if ((bytes[index] & CONTINUES) == 0) {
                return index - at + 1;
            }
        }
        throw new IOException("A Cast message ends in the middle of a number");
    }
}
