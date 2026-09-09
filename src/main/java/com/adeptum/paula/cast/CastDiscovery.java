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

import com.adeptum.paula.playback.DaemonExecutors;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds the Cast devices on the network and keeps what it found.
 *
 * <p>A scan asks on every network interface that could reach one, marked to be answered straight back, and
 * listens for a few seconds; devices are remembered by their identity, so one seen again keeps its place and
 * one that has moved keeps its name. Scans run on a thread of their own and the screen asks how they are
 * going.</p>
 */
@Slf4j
public final class CastDiscovery implements AutoCloseable {

    private static final Duration SCAN = Duration.ofSeconds(3);
    private static final Duration ASK_AGAIN = Duration.ofSeconds(1);

    /**
     * How long a device may say nothing before it is taken to be gone.
     */
    private static final Duration FORGET = Duration.ofSeconds(15);
    private static final int LARGEST_ANSWER = 9000;

    private final Map<String, CastDevice> devices = new ConcurrentHashMap<>();
    private final Map<String, Long> heardAt = new ConcurrentHashMap<>();
    private final ExecutorService worker = DaemonExecutors.singleThread("paula-cast-discovery");
    private volatile boolean scanning;
    private volatile boolean closed;

    private CastDiscovery() {
    }

    /**
     * Starts looking straight away, so the screen can offer casting as soon as anything answers.
     */
    public static CastDiscovery start() {
        final CastDiscovery discovery = new CastDiscovery();
        discovery.scan();
        return discovery;
    }

    public boolean isScanning() {
        return scanning;
    }

    public List<CastDevice> devices() {
        return devices.values().stream().sorted(Comparator.comparing(CastDevice::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /**
     * A device by the name its owner gave it or by its address, however it was asked for on the command line.
     */
    public Optional<CastDevice> find(String nameOrAddress) {
        final String wanted = nameOrAddress.toLowerCase(Locale.ROOT);
        return devices.values().stream()
                .filter(device -> device.name().toLowerCase(Locale.ROOT).equals(wanted)
                        || device.address().getHostAddress().equals(wanted))
                .findFirst();
    }

    public synchronized void scan() {
        if (scanning || closed) {
            return;
        }
        scanning = true;
        worker.execute(this::runScan);
    }

    @Override
    public void close() {
        closed = true;
        worker.shutdownNow();
    }

    private void runScan() {
        try (Selector selector = Selector.open()) {
            final List<Asking> askings = openChannels(selector);
            final long deadline = System.currentTimeMillis() + SCAN.toMillis();
            long askAt = 0;
            while (System.currentTimeMillis() < deadline && !closed) {
                if (System.currentTimeMillis() >= askAt) {
                    ask(askings);
                    askAt = System.currentTimeMillis() + ASK_AGAIN.toMillis();
                }
                receive(selector, Math.max(1, Math.min(askAt, deadline) - System.currentTimeMillis()));
            }
            selector.keys().forEach(key -> closeQuietly((DatagramChannel) key.channel()));
            forgetWhatHasGoneQuiet();
        } catch (IOException e) {
            log.debug("Looking for cast devices failed", e);
        } finally {
            scanning = false;
        }
    }

    /**
     * The socket the network answers on, shared with whatever else on the machine listens there and joined to
     * the group on every interface that could reach a device. The question goes out from it too, since a
     * device answers a question from any other port privately if at all, and most not at all.
     */
    private List<Asking> openChannels(Selector selector) throws IOException {
        final DatagramChannel shared = DatagramChannel.open(StandardProtocolFamily.INET);
        shared.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        if (shared.supportedOptions().contains(StandardSocketOptions.SO_REUSEPORT)) {
            shared.setOption(StandardSocketOptions.SO_REUSEPORT, true);
        }
        shared.bind(new InetSocketAddress(Mdns.PORT));
        shared.configureBlocking(false);
        shared.register(selector, SelectionKey.OP_READ);

        final List<Asking> askings = new ArrayList<>();
        for (final NetworkInterface network : reachable()) {
            try {
                shared.join(Mdns.GROUP, network);
                askings.add(new Asking(shared, network));
            } catch (IOException e) {
                log.debug("Cannot look for cast devices on {}", network.getName(), e);
            }
        }
        return askings;
    }

    /**
     * The question as it goes out on one interface.
     */
    private record Asking(DatagramChannel channel, NetworkInterface network) {

        void ask() throws IOException {
            channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, network);
            channel.send(ByteBuffer.wrap(Mdns.query()), new InetSocketAddress(Mdns.GROUP, Mdns.PORT));
        }
    }

    private void ask(List<Asking> askings) {
        for (final Asking asking : askings) {
            try {
                asking.ask();
            } catch (IOException e) {
                log.debug("Asking for cast devices on {} failed", asking.network().getName(), e);
            }
        }
    }

    private void receive(Selector selector, long timeoutMillis) throws IOException {
        if (selector.select(timeoutMillis) == 0) {
            return;
        }
        final ByteBuffer packet = ByteBuffer.allocate(LARGEST_ANSWER);
        for (final SelectionKey key : selector.selectedKeys()) {
            final DatagramChannel channel = (DatagramChannel) key.channel();
            while (channel.receive(packet.clear()) != null) {
                heard(Mdns.devices(Arrays.copyOf(packet.array(), packet.flip().limit())));
                packet.clear();
            }
        }
        selector.selectedKeys().clear();
    }

    private void heard(List<CastDevice> heard) {
        for (final CastDevice device : heard) {
            heardAt.put(device.id(), System.currentTimeMillis());
            devices.put(device.id(), device);
        }
    }

    /**
     * A device nothing has been heard from for a while has been unplugged or switched off, so it goes from
     * the list rather than sitting there to be chosen and failed on. It is counted in time rather than in
     * scans because a device answers the same question only now and then, however often it is asked, so a
     * scan or two with nothing from it says nothing about whether it is there.
     */
    private void forgetWhatHasGoneQuiet() {
        if (closed) {
            return;
        }
        final long gone = System.currentTimeMillis() - FORGET.toMillis();
        for (final Map.Entry<String, Long> heard : heardAt.entrySet()) {
            if (heard.getValue() < gone) {
                devices.remove(heard.getKey());
                heardAt.remove(heard.getKey());
            }
        }
    }

    private static List<NetworkInterface> reachable() throws SocketException {
        return NetworkInterface.networkInterfaces()
                .filter(network -> {
                    try {
                        return network.isUp() && !network.isLoopback() && network.supportsMulticast()
                                && network.inetAddresses().anyMatch(Inet4Address.class::isInstance);
                    } catch (SocketException e) {
                        return false;
                    }
                })
                .toList();
    }

    private void closeQuietly(DatagramChannel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            log.debug("Closing a discovery socket", e);
        }
    }
}
