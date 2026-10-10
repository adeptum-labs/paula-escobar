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

import static com.adeptum.paula.testing.TestTaps.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpectrumTest {

    private record Edge(long tstate, double level) {
    }

    private record AyWrite(long tstate, int register, int value) {
    }

    private static final class Recorder implements MachineOutput {
        final List<Edge> edges = new ArrayList<>();
        final List<AyWrite> ay = new ArrayList<>();

        @Override
        public void beeper(long tstate, double level) {
            edges.add(new Edge(tstate, level));
        }

        @Override
        public void ayRegister(long tstate, int register, int value) {
            ay.add(new AyWrite(tstate, register, value));
        }
    }

    private final Recorder recorder = new Recorder();

    private Spectrum machine(Spectrum.Model model, byte[] code) throws Exception {
        final byte[] tape = tape(codePart("music", ENTRY, code));
        return new Spectrum(model, TapProgram.of(Path.of("x.tap"), TapReader.read(Path.of("x.tap"), tape)), recorder);
    }

    @Test
    void reportsTheSpeakerEdgesAtTheirTStates() throws Exception {
        machine(Spectrum.Model.K48, SQUARE_WAVE).runUntil(3500);

        assertEquals(List.of(19L, 1700L, 3396L), recorder.edges.stream().map(Edge::tstate).toList());
        assertEquals(List.of(50 / 52.0, 0.0, 50 / 52.0), recorder.edges.stream().map(Edge::level).toList());
    }

    @Test
    void endsWhenTheProgramReturnsToBasic() throws Exception {
        final Spectrum spectrum = machine(Spectrum.Model.K48, ONE_PULSE_THEN_RETURN);

        assertEquals(Optional.of(EndReason.RETURNED), spectrum.runUntil(100_000));
        assertEquals(881, spectrum.endedAt());
    }

    @Test
    void endsWhenTheProgramHaltsWithInterruptsOff() throws Exception {
        assertEquals(Optional.of(EndReason.HALTED), machine(Spectrum.Model.K48, PULSE_THEN_HALT).runUntil(100_000));
    }

    @Test
    void endsWhenTheProgramLeavesForTheRom() throws Exception {
        assertEquals(Optional.of(EndReason.ROM_CALL), machine(Spectrum.Model.K48, PULSE_THEN_JUMP_TO_ROM).runUntil(100_000));
    }

    @Test
    void keepsRunningThroughTheFrameInterrupt() throws Exception {
        final byte[] interruptsOn = bytes(0xFB, 0x76, 0x18, 0xFD);
        final Spectrum spectrum = machine(Spectrum.Model.K48, interruptsOn);

        assertEquals(Optional.empty(), spectrum.runUntil(3L * Spectrum.Model.K48.frame()));
        assertTrue(spectrum.tstates() >= 3L * Spectrum.Model.K48.frame());
    }

    @Test
    void countsFramesInTheFramesSystemVariable() throws Exception {
        final Spectrum spectrum = machine(Spectrum.Model.K48, bytes(0xFB, 0x76, 0x18, 0xFD));

        spectrum.runUntil(3L * Spectrum.Model.K48.frame() + 100);

        assertEquals(4, spectrum.read(23672), "one interrupt at the start of each of four frames");
    }

    @Test
    void leavesTheFramesSystemVariableAloneWithInterruptsOff() throws Exception {
        final Spectrum spectrum = machine(Spectrum.Model.K48, SQUARE_WAVE);
        spectrum.write(23672, 0x55);

        spectrum.runUntil(3L * Spectrum.Model.K48.frame() + 100);

        assertEquals(0x55, spectrum.read(23672));
        assertEquals(0, spectrum.read(23673));
    }

    @Test
    void runsSlowerFromContendedMemory() throws Exception {
        final byte[] toggler = bytes(0xF3, 0xAF, 0xD3, 0xFE, 0xEE, 0x10, 0x18, 0xFA);
        final Recorder fast = new Recorder();
        final Recorder slow = new Recorder();
        new Spectrum(Spectrum.Model.K48, TapProgram.of(Path.of("x"), TapReader.read(Path.of("x"), tape(codePart("a", 0x8000, toggler)))), fast).runUntil(70_000);
        new Spectrum(Spectrum.Model.K48, TapProgram.of(Path.of("x"), TapReader.read(Path.of("x"), tape(codePart("a", 0x6000, toggler)))), slow).runUntil(70_000);

        assertTrue(slow.edges.size() < fast.edges.size(), slow.edges.size() + " edges contended, " + fast.edges.size() + " not");
    }

    @Test
    void reportsAyRegisterWrites() throws Exception {
        final Spectrum spectrum = machine(Spectrum.Model.K128, AY_WRITE);

        spectrum.runUntil(100_000);

        assertEquals(1, recorder.ay.size());
        assertEquals(7, recorder.ay.get(0).register());
        assertEquals(0x3E, recorder.ay.get(0).value());
        assertTrue(spectrum.ayWritten());
    }

    @Test
    void reportsWritesToTheLastSoundRegister() throws Exception {
        machine(Spectrum.Model.K128, AY_WRITE_ENVELOPE_SHAPE).runUntil(100_000);

        assertEquals(List.of(13), recorder.ay.stream().map(AyWrite::register).toList());
    }

    @Test
    void keepsWritesToTheAyIoPortsToItself() throws Exception {
        final Spectrum spectrum = machine(Spectrum.Model.K128, AY_WRITE_PORT_A);

        spectrum.runUntil(100_000);

        assertEquals(List.of(), recorder.ay);
        assertTrue(spectrum.ayWritten());
    }

    @Test
    void pagesTheBankAtC000OnA128() throws Exception {
        final Spectrum spectrum = machine(Spectrum.Model.K128, PAGE_BANK_ONE);
        spectrum.write(0xC000, 0x55);

        spectrum.runUntil(100_000);

        assertEquals(0, spectrum.read(0xC000), "bank 1 is empty");
        assertTrue(spectrum.pagingSeen());
    }

    @Test
    void ignoresWritesToTheRom() throws Exception {
        final Spectrum spectrum = machine(Spectrum.Model.K48, SQUARE_WAVE);

        final int before = spectrum.read(0x0100);

        spectrum.write(0x0100, before ^ 0xFF);

        assertEquals(before, spectrum.read(0x0100));
        assertFalse(spectrum.pagingSeen());
    }

    @Test
    void keepsTheInterruptHandlerStubInTheNoisyRom() throws Exception {
        final Spectrum spectrum = machine(Spectrum.Model.K48, SQUARE_WAVE);

        assertEquals(0xFB, spectrum.read(0x0038));
        assertEquals(0xC9, spectrum.read(0x0039));
    }

    @Test
    void startsTheRomWithDisableInterruptsAsTheRealOneDoes() throws Exception {
        assertEquals(0xF3, machine(Spectrum.Model.K48, SQUARE_WAVE).read(0x0000));
    }

    @Test
    void leavesTheUnusedRomBlockAtFf() throws Exception {
        final Spectrum spectrum = machine(Spectrum.Model.K48, SQUARE_WAVE);

        assertEquals(0xFF, spectrum.read(0x386E));
        assertEquals(0xFF, spectrum.read(0x3CFF));
    }

    @Test
    void fillsTheRestOfTheRomWithTheSameNoiseOnEveryMachine() throws Exception {
        final Spectrum first = machine(Spectrum.Model.K48, SQUARE_WAVE);
        final Spectrum second = machine(Spectrum.Model.K128, WAIT_FOREVER);

        final Set<Integer> distinct = new HashSet<>();
        for (int address = 0; address < 0x4000; address++) {
            assertEquals(first.read(address), second.read(address));
            if (address < 0x100) {
                distinct.add(first.read(address));
            }
        }
        assertTrue(distinct.size() >= 100, distinct.size() + " distinct values in the first 256 bytes");
    }

    @Test
    void letsAnEngineUseTheRomAsItsNoiseSource() throws Exception {
        final byte[] copyRomBitToSpeaker = bytes(0xF3, 0x21, 0x00, 0x00, 0x7E, 0xE6, 0x10, 0xD3, 0xFE, 0x23, 0x18, 0xF8);

        machine(Spectrum.Model.K48, copyRomBitToSpeaker).runUntil(3000);

        assertTrue(recorder.edges.size() > 10, recorder.edges.size() + " edges");
    }
}
