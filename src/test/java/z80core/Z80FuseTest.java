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
package z80core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Runs the Fuse emulator's Z80 test suite (z80/tests, GPL-2-or-later) against the vendored core: registers, flags,
 * memory and T-states after every opcode.
 */
class Z80FuseTest {

    private static final int REGISTER_WORDS = 13;
    private static final int RAM_SIZE = 0x10000;

    private static final int AF = 0;
    private static final int PC = 11;
    private static final int MEMPTR = 12;
    private static final int FLAG_BIT_2 = 0x04;
    private static final int FLAG_BIT_3 = 0x08;
    private static final int FLAG_BIT_4 = 0x10;

    private record Case(String name, int[] registers, int[] state, Map<Integer, Integer> memory) {
    }

    /**
     * Fields of a case that are not compared: AF bits, MEMPTR, and a shift added to the expected PC.
     */
    private record Tolerance(int ignoredAfBits, boolean ignoresMemPtr, int expectedPcShift) {
    }

    private static final Tolerance REPEATED_BLOCK_IO = new Tolerance(FLAG_BIT_2 | FLAG_BIT_4, true, 0);

    /**
     * Cases where the core and Fuse disagree. The core follows the hardware-verified flags and MEMPTR of repeated
     * block I/O where Fuse keeps the older behaviour; they also disagree on the undocumented X flag of CPDR and INIR repeats;
     * the core leaves PC after a HALT opcode where Fuse leaves it on the opcode.
     */
    private static final Map<String, Tolerance> KNOWN_DIFFERENCES = Map.of(
            "edb9_2", new Tolerance(FLAG_BIT_3, false, 0),
            "edb2_1", new Tolerance(REPEATED_BLOCK_IO.ignoredAfBits() | FLAG_BIT_3, true, 0),
            "edb3_1", REPEATED_BLOCK_IO,
            "edba_1", REPEATED_BLOCK_IO,
            "edbb_1", REPEATED_BLOCK_IO,
            "76", new Tolerance(0, false, 1));

    private static final class Bus extends MemIoOps {
        Bus() {
            super(RAM_SIZE, RAM_SIZE);
        }

        @Override
        public int inPort(int port) {
            addressOnBus(port, 4);
            return port >>> 8;
        }

        @Override
        public void outPort(int port, int value) {
            addressOnBus(port, 4);
        }

        int at(int address) {
            return super.peek8(address);
        }
    }

    @Test
    void everyOpcodeEndsInTheStateFuseExpects() throws IOException {
        final List<Case> inputs = parse("/z80/tests.in", false);
        final Map<String, Case> expected = parse("/z80/tests.expected", true).stream()
                .collect(Collectors.toMap(Case::name, c -> c));
        final List<String> failures = new ArrayList<>();
        for (final Case input : inputs) {
            final String difference = difference(input, expected.get(input.name()));
            if (difference != null) {
                failures.add(input.name() + ": " + difference);
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " of " + inputs.size() + " failed:\n" + String.join("\n", failures));
    }

    @Test
    void onlyTheSixKnownCasesAreExcusedFromFullComparison() {
        assertEquals(Set.of("76", "edb2_1", "edb3_1", "edb9_2", "edba_1", "edbb_1"), KNOWN_DIFFERENCES.keySet());
    }

    private static String difference(Case input, Case expected) {
        final Bus bus = new Bus();
        final Z80 z80 = new Z80(bus, new NotifyOps() {
            @Override
            public int breakpoint(int address, int opcode) {
                return opcode;
            }

            @Override
            public void execDone() {
            }
        });
        load(z80, bus, input);
        while (bus.getTstates() < input.state()[6]) {
            z80.execute();
        }
        final int[] registers = {z80.getRegAF(), z80.getRegBC(), z80.getRegDE(), z80.getRegHL(), z80.getRegAFx(),
            z80.getRegBCx(), z80.getRegDEx(), z80.getRegHLx(), z80.getRegIX(), z80.getRegIY(), z80.getRegSP(),
            z80.getRegPC(), z80.getMemPtr()};
        final int[] state = {z80.getRegI(), z80.getRegR(), z80.isIFF1() ? 1 : 0, z80.isIFF2() ? 1 : 0,
            z80.getIM().ordinal(), z80.isHalted() ? 1 : 0, (int) bus.getTstates()};
        final Tolerance tolerance = KNOWN_DIFFERENCES.getOrDefault(input.name(), new Tolerance(0, false, 0));
        final int[] expectedRegisters = expected.registers().clone();
        expectedRegisters[PC] += tolerance.expectedPcShift();
        for (final int[] words : List.of(registers, expectedRegisters)) {
            words[AF] &= ~tolerance.ignoredAfBits();
            words[MEMPTR] = tolerance.ignoresMemPtr() ? 0 : words[MEMPTR];
        }
        if (!Arrays.equals(registers, expectedRegisters)) {
            return "registers " + hex(registers) + " expected " + hex(expectedRegisters);
        }
        if (!Arrays.equals(state, expected.state())) {
            return "state " + Arrays.toString(state) + " expected " + Arrays.toString(expected.state());
        }
        for (final Map.Entry<Integer, Integer> cell : expected.memory().entrySet()) {
            if (bus.at(cell.getKey()) != cell.getValue()) {
                return "memory at " + Integer.toHexString(cell.getKey());
            }
        }
        return null;
    }

    private static void load(Z80 z80, Bus bus, Case input) {
        final int[] r = input.registers();
        z80.setRegAF(r[0]);
        z80.setRegBC(r[1]);
        z80.setRegDE(r[2]);
        z80.setRegHL(r[3]);
        z80.setRegAFx(r[4]);
        z80.setRegBCx(r[5]);
        z80.setRegDEx(r[6]);
        z80.setRegHLx(r[7]);
        z80.setRegIX(r[8]);
        z80.setRegIY(r[9]);
        z80.setRegSP(r[10]);
        z80.setRegPC(r[11]);
        z80.setMemPtr(r[12]);
        final int[] s = input.state();
        z80.setRegI(s[0]);
        z80.setRegR(s[1]);
        z80.setIFF1(s[2] != 0);
        z80.setIFF2(s[3] != 0);
        z80.setIM(Z80.IntMode.values()[s[4]]);
        z80.setHalted(s[5] != 0);
        input.memory().forEach(bus::poke8);
        bus.reset();
    }

    private static String hex(int[] words) {
        return Arrays.stream(words).mapToObj(w -> String.format("%04x", w)).collect(Collectors.joining(" "));
    }

    /**
     * The input file counts the T-states to run in decimal and the rest in hex; the expected file reports the
     * T-states used, also in decimal. Event lines (leading blank) carry nothing the end state does not.
     */
    private static List<Case> parse(String resource, boolean expected) throws IOException {
        final List<String> lines;
        try (InputStream in = Z80FuseTest.class.getResourceAsStream(resource)) {
            lines = new String(in.readAllBytes(), StandardCharsets.US_ASCII).lines().toList();
        }
        final List<Case> cases = new ArrayList<>();
        int at = 0;
        while (at < lines.size()) {
            if (lines.get(at).isBlank()) {
                at++;
                continue;
            }
            final String name = lines.get(at++).strip();
            while (expected && lines.get(at).startsWith(" ")) {
                at++;
            }
            final int[] registers = words(lines.get(at++), 16, REGISTER_WORDS);
            final String[] stateFields = lines.get(at++).strip().split("\\s+");
            final int[] state = new int[stateFields.length];
            for (int i = 0; i < state.length; i++) {
                state[i] = Integer.parseInt(stateFields[i], i < 2 ? 16 : 10);
            }
            final Map<Integer, Integer> memory = new LinkedHashMap<>();
            while (at < lines.size() && !lines.get(at).isBlank() && !lines.get(at).strip().equals("-1")) {
                final String[] block = lines.get(at++).strip().split("\\s+");
                int address = Integer.parseInt(block[0], 16);
                for (int i = 1; i < block.length && !block[i].equals("-1"); i++) {
                    memory.put(address++, Integer.parseInt(block[i], 16));
                }
            }
            if (at < lines.size() && lines.get(at).strip().equals("-1")) {
                at++;
            }
            cases.add(new Case(name, registers, state, memory));
        }
        return cases;
    }

    private static int[] words(String line, int radix, int count) {
        final int[] words = new int[count];
        final String[] fields = line.strip().split("\\s+");
        for (int i = 0; i < count; i++) {
            words[i] = Integer.parseInt(fields[i], radix);
        }
        return words;
    }
}
