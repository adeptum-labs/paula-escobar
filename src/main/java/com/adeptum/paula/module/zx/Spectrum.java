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

import java.util.Optional;
import z80core.MemIoOps;
import z80core.NotifyOps;
import z80core.Z80;

/**
 * A 48K or 128K Spectrum around the Z80 core, without its ROM: the programs on tapes are music engines that
 * start from a BASIC loader and never call back into it, so the ROM is a stub that answers the frame interrupt
 * and ends the run if the program jumps anywhere else in it. Memory is banked, so the 128K pages are the same
 * model with the paging port wired up. The ULA delays memory in the banks it shares with the screen, and the
 * ports it answers, exactly as the table of the real machine says, since the engines count T-states.
 */
final class Spectrum extends MemIoOps implements NotifyOps {

    enum Model {
        K48(69888, 3_500_000, 14335, 224),
        K128(70908, 3_546_900, 14361, 228);

        private final int frame;
        private final int clock;
        private final int firstContended;
        private final int line;

        Model(int frame, int clock, int firstContended, int line) {
            this.frame = frame;
            this.clock = clock;
            this.firstContended = firstContended;
            this.line = line;
        }

        int frame() {
            return frame;
        }

        int clock() {
            return clock;
        }
    }

    private static final int BANK_SIZE = 0x4000;
    private static final int ROM_BANK = 8;
    private static final int RAM_BANKS = 8;
    private static final int SCREEN_BANK = 5;
    private static final int ROM_END = 0x4000;
    private static final int IM1_HANDLER = 0x0038;
    private static final byte ENABLE_INTERRUPTS = (byte) 0xFB;
    private static final byte RETURN = (byte) 0xC9;
    private static final int BASIC_RETURN = 0x1303;
    private static final int FRAMES_VARIABLE = 0x5C78;
    private static final int FRAMES_BYTES = 3;
    private static final int INTERRUPT_LENGTH = 32;
    private static final int PIXELS_PER_LINE = 128;
    private static final int[] CONTENTION = {6, 5, 4, 3, 2, 1, 0, 0};
    private static final int SCREEN_LINES = 192;
    private static final double[] BEEPER_LEVELS = {0, 0, 50 / 52.0, 1};
    private static final int EAR_BIT = 4;
    private static final int MIC_BIT = 3;
    private static final int ULA_PORT_MASK = 1;
    private static final int PAGING_MASK = 0x8002;
    private static final int AY_MASK = 0xC002;
    private static final int AY_SELECT = 0xC000;
    private static final int AY_DATA = 0x8000;
    private static final int AY_REGISTERS = 0x0F;
    private static final int BANK_MASK = 0x07;
    private static final int PAGING_LOCK = 0x20;
    private static final int KEYS_UP = 0xBF;
    private static final int KEMPSTON_MASK = 0x21;
    private static final int KEMPSTON = 0x01;
    private static final int FLOATING = 0xFF;
    private static final int IY_SYSTEM = 0x5C3A;
    private static final int HL_ALTERNATE = 0x2758;
    private static final int INTERRUPT_VECTOR = 0x3F;
    private static final int STACK_ROOM = 2;
    private static final int PORT_CYCLES_AFTER = 3;

    private final Model model;
    private final MachineOutput output;
    private final byte[][] banks = new byte[RAM_BANKS + 1][BANK_SIZE];
    private final int[] slots = {ROM_BANK, SCREEN_BANK, 2, 0};
    private final int[] delays;
    private final Z80 z80;
    private double beeperLevel;
    private int selectedRegister;
    private boolean pagingLocked;
    private boolean pagingSeen;
    private boolean ayWritten;
    private long frames;
    private EndReason ended;
    private long endedAt;

    Spectrum(Model model, TapProgram program, MachineOutput output) {
        super(0, 0);
        this.model = model;
        this.output = output;
        this.delays = contentionTable(model);
        banks[ROM_BANK][IM1_HANDLER] = ENABLE_INTERRUPTS;
        banks[ROM_BANK][IM1_HANDLER + 1] = RETURN;
        this.z80 = new Z80(this, this);
        boot(program);
    }

    Optional<EndReason> runUntil(long tstate) {
        while (ended == null && getTstates() < tstate) {
            z80.execute();
            countFrames();
            leaveIfDone();
        }
        return Optional.ofNullable(ended);
    }

    long tstates() {
        return getTstates();
    }

    long endedAt() {
        return endedAt;
    }

    boolean pagingSeen() {
        return pagingSeen;
    }

    boolean ayWritten() {
        return ayWritten;
    }

    int read(int address) {
        return banks[slots[address >>> 14 & 3]][address & (BANK_SIZE - 1)] & 0xFF;
    }

    void write(int address, int value) {
        final int bank = slots[address >>> 14 & 3];
        if (bank != ROM_BANK) {
            banks[bank][address & (BANK_SIZE - 1)] = (byte) value;
        }
    }

    @Override
    public int fetchOpcode(int address) {
        contend(address);
        tick(4);
        return read(address);
    }

    @Override
    public int peek8(int address) {
        contend(address);
        tick(3);
        return read(address);
    }

    @Override
    public void poke8(int address, int value) {
        contend(address);
        tick(3);
        write(address, value);
    }

    @Override
    public void addressOnBus(int address, int tstates) {
        if (contended(address)) {
            for (int i = 0; i < tstates; i++) {
                tick(delay());
                tick(1);
            }
        } else {
            tick(tstates);
        }
    }

    @Override
    public boolean isActiveINT() {
        return getTstates() % model.frame < INTERRUPT_LENGTH;
    }

    @Override
    public int inPort(int port) {
        beforePort(port);
        final int value = (port & ULA_PORT_MASK) == 0 ? KEYS_UP : (port & KEMPSTON_MASK) == KEMPSTON ? 0 : FLOATING;
        afterPort(port);
        return value;
    }

    @Override
    public void outPort(int port, int value) {
        beforePort(port);
        if ((port & ULA_PORT_MASK) == 0) {
            speaker(value);
        } else if ((port & PAGING_MASK) == 0) {
            page(value);
        } else if ((port & AY_MASK) == AY_SELECT) {
            selectedRegister = value & AY_REGISTERS;
        } else if ((port & AY_MASK) == AY_DATA) {
            ayWritten = true;
            output.ayRegister(getTstates(), selectedRegister, value & 0xFF);
        }
        afterPort(port);
    }

    @Override
    public int breakpoint(int address, int opcode) {
        return opcode;
    }

    @Override
    public void execDone() {
    }

    private void boot(TapProgram program) {
        program.segments().forEach(segment -> {
            for (int i = 0; i < segment.data().length; i++) {
                write(segment.address() + i & 0xFFFF, segment.data()[i]);
            }
        });
        final int stack = program.ramTop() - STACK_ROOM & 0xFFFF;
        write(stack, BASIC_RETURN & 0xFF);
        write(stack + 1, BASIC_RETURN >>> 8);
        z80.setRegSP(stack);
        z80.setRegPC(program.entry());
        z80.setRegBC(program.entry());
        z80.setRegIY(IY_SYSTEM);
        z80.setRegHLx(HL_ALTERNATE);
        z80.setRegI(INTERRUPT_VECTOR);
        z80.setIM(Z80.IntMode.IM1);
        z80.setIFF1(true);
        z80.setIFF2(true);
    }

    private void leaveIfDone() {
        final int pc = z80.getRegPC();
        if (pc == BASIC_RETURN) {
            ended = EndReason.RETURNED;
        } else if (pc < ROM_END && pc != IM1_HANDLER && pc != IM1_HANDLER + 1) {
            ended = EndReason.ROM_CALL;
        } else if (z80.isHalted() && !z80.isIFF1()) {
            ended = EndReason.HALTED;
        }
        if (ended != null) {
            endedAt = getTstates();
        }
    }

    private void countFrames() {
        while (frames < getTstates() / model.frame) {
            frames++;
            final byte[] screen = banks[SCREEN_BANK];
            int carry = 0;
            while (carry < FRAMES_BYTES && ++screen[FRAMES_VARIABLE - ROM_END + carry] == 0) {
                carry++;
            }
        }
    }

    private void speaker(int value) {
        final double level = BEEPER_LEVELS[(value >> EAR_BIT & 1) << 1 | (value >> MIC_BIT & 1)];
        if (level != beeperLevel) {
            beeperLevel = level;
            output.beeper(getTstates(), level);
        }
    }

    private void page(int value) {
        pagingSeen = true;
        if (model == Model.K128 && !pagingLocked) {
            slots[3] = value & BANK_MASK;
            pagingLocked = (value & PAGING_LOCK) != 0;
        }
    }

    private void beforePort(int port) {
        contend(port);
        tick(1);
    }

    private void afterPort(int port) {
        if ((port & ULA_PORT_MASK) == 0) {
            tick(delay());
            tick(PORT_CYCLES_AFTER);
        } else {
            addressOnBus(port, PORT_CYCLES_AFTER);
        }
    }

    private boolean contended(int address) {
        final int bank = slots[address >>> 14 & 3];
        return bank < RAM_BANKS && (bank & 1) == 1;
    }

    private void contend(int address) {
        if (contended(address)) {
            tick(delay());
        }
    }

    private int delay() {
        return delays[(int) (getTstates() % model.frame)];
    }

    private void tick(int tstates) {
        super.addressOnBus(0, tstates);
    }

    private static int[] contentionTable(Model model) {
        final int[] table = new int[model.frame];
        for (int line = 0; line < SCREEN_LINES; line++) {
            for (int pixel = 0; pixel < PIXELS_PER_LINE; pixel++) {
                table[model.firstContended + line * model.line + pixel] = CONTENTION[pixel % CONTENTION.length];
            }
        }
        return table;
    }
}
