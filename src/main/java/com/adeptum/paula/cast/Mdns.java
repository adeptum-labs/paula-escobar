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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The question that finds Cast devices and the answers they give, in the DNS the local network speaks among
 * itself.
 *
 * <p>A device answers a question for the service with a pointer naming its instance, and adds the records
 * that go with it: where it listens, its address, and a text record of what it is and what its owner named
 * it. Names repeat, so the format lets a name point back at where it was first spelled out.</p>
 */
final class Mdns {

    static final String SERVICE = "_googlecast._tcp.local";
    static final InetAddress GROUP = group();
    static final int PORT = 5353;

    private static final int HEADER_LENGTH = 12;
    private static final int TYPE_A = 1;
    private static final int TYPE_PTR = 12;
    private static final int TYPE_TXT = 16;
    private static final int TYPE_SRV = 33;
    private static final int CLASS_IN = 1;

    private static final int POINTER = 0xC0;
    private static final int POINTER_MASK = 0x3FFF;
    private static final int LONGEST_NAME_HOPS = 64;
    private static final int SRV_TARGET_AT = 6;
    private static final int ADDRESS_LENGTH = 4;

    private Mdns() {
    }

    static byte[] query() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0});
        for (final String label : SERVICE.split("\\.")) {
            out.write(label.length());
            out.writeBytes(label.getBytes(StandardCharsets.US_ASCII));
        }
        out.write(0);
        out.writeBytes(new byte[] {0, TYPE_PTR, 0, CLASS_IN});
        return out.toByteArray();
    }

    /**
     * The Cast devices one answer packet describes: every instance the packet names, has an address for and
     * says which port it listens on.
     */
    static List<CastDevice> devices(byte[] packet) {
        final Map<String, Service> services = new HashMap<>();
        final Map<String, InetAddress> addresses = new HashMap<>();
        try {
            final ByteBuffer in = ByteBuffer.wrap(packet);
            final int questions = in.getShort(4) & 0xFFFF;
            final int records = (in.getShort(6) & 0xFFFF) + (in.getShort(8) & 0xFFFF) + (in.getShort(10) & 0xFFFF);
            in.position(HEADER_LENGTH);
            for (int question = 0; question < questions; question++) {
                name(in);
                in.position(in.position() + 4);
            }
            for (int record = 0; record < records; record++) {
                record(in, services, addresses);
            }
        } catch (RuntimeException malformed) {
            // What was read before the packet stopped making sense still stands.
        }
        return found(services, addresses);
    }

    private static void record(ByteBuffer in, Map<String, Service> services, Map<String, InetAddress> addresses) {
        final String owner = name(in);
        final int type = in.getShort() & 0xFFFF;
        in.getShort();
        in.getInt();
        final int length = in.getShort() & 0xFFFF;
        final int end = in.position() + length;
        switch (type) {
            case TYPE_SRV -> {
                in.position(in.position() + SRV_TARGET_AT - Short.BYTES);
                final int port = in.getShort() & 0xFFFF;
                service(services, owner).target = key(name(in));
                service(services, owner).port = port;
            }
            case TYPE_TXT -> service(services, owner).text.putAll(text(in, end));
            case TYPE_A -> addresses.put(key(owner), address(in));
            default -> { }
        }
        in.position(end);
    }

    private static Service service(Map<String, Service> services, String instance) {
        return services.computeIfAbsent(key(instance), name -> new Service(instance));
    }

    /**
     * Names compare regardless of case, and a device is free to spell its own differently from one record to
     * the next.
     */
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static List<CastDevice> found(Map<String, Service> services, Map<String, InetAddress> addresses) {
        final List<CastDevice> devices = new ArrayList<>();
        services.forEach((key, service) -> {
            final InetAddress address = service.target == null ? null : addresses.get(service.target);
            if (address != null && service.port > 0 && key.endsWith(SERVICE)) {
                final String instance = service.instance;
                devices.add(new CastDevice(service.text.getOrDefault("id", instance),
                        service.text.getOrDefault("fn", instance.substring(0, instance.length() - SERVICE.length() - 1)),
                        service.text.getOrDefault("md", ""), address, service.port));
            }
        });
        return devices;
    }

    private static Map<String, String> text(ByteBuffer in, int end) {
        final Map<String, String> text = new HashMap<>();
        while (in.position() < end) {
            final int length = in.get() & 0xFF;
            final byte[] entry = new byte[length];
            in.get(entry);
            final String pair = new String(entry, StandardCharsets.UTF_8);
            final int equals = pair.indexOf('=');
            if (equals > 0) {
                text.put(pair.substring(0, equals), pair.substring(equals + 1));
            }
        }
        return text;
    }

    private static InetAddress address(ByteBuffer in) {
        final byte[] address = new byte[ADDRESS_LENGTH];
        in.get(address);
        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * A name as its labels, following a pointer back to where a name was first spelled out and leaving the
     * cursor after the first pointer, which is where the record goes on.
     */
    private static String name(ByteBuffer in) {
        final StringBuilder name = new StringBuilder();
        int at = in.position();
        int after = -1;
        for (int hops = 0; hops < LONGEST_NAME_HOPS; hops++) {
            final int length = in.get(at) & 0xFF;
            if (length == 0) {
                in.position(after < 0 ? at + 1 : after);
                return name.toString();
            }
            if ((length & POINTER) == POINTER) {
                if (after < 0) {
                    after = at + 2;
                }
                at = (in.getShort(at) & POINTER_MASK);
                continue;
            }
            if (!name.isEmpty()) {
                name.append('.');
            }
            name.append(new String(in.array(), at + 1, length, StandardCharsets.UTF_8));
            at += 1 + length;
        }
        throw new IllegalStateException("A name that points at itself");
    }

    private static InetAddress group() {
        try {
            return InetAddress.getByName("224.0.0.251");
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class Service {
        private final String instance;
        private String target;
        private int port;
        private final Map<String, String> text = new HashMap<>();

        private Service(String instance) {
            this.instance = instance;
        }
    }
}
