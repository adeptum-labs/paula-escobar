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
 *
 * The conversion follows Load_ult.cpp of OpenMPT, Copyright © 2004-2026 the
 * OpenMPT project developers and Copyright © 1997-2003 Olivier Lapicque,
 * which ports Storlek's reader from Schism Tracker; licensed under the
 * three-clause BSD licence and used here under the GNU General Public
 * License.
 */

package com.adeptum.paula.module.ult;

import de.quippy.javamod.io.ModfileInputStream;
import de.quippy.javamod.multimedia.mod.ModConstants;
import de.quippy.javamod.multimedia.mod.loader.instrument.InstrumentsContainer;
import de.quippy.javamod.multimedia.mod.loader.instrument.Sample;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternContainer;
import de.quippy.javamod.multimedia.mod.loader.pattern.PatternElement;
import de.quippy.javamod.multimedia.mod.midi.MidiMacros;
import de.quippy.javamod.multimedia.mod.mixer.BasicModMixer;
import de.quippy.javamod.multimedia.mod.mixer.ScreamTrackerMixer;
import java.io.IOException;
import java.util.Arrays;

/**
 * An UltraTracker module as the Impulse Tracker module it plays like, handed to the Scream Tracker mixer.
 *
 * <p>UltraTracker has no player here of its own. Its samples, its hertz tuning and its two effects to an event
 * fit Impulse Tracker closely enough that the module is rewritten into one, as OpenMPT does when it loads
 * one, and the loop of an UltraTracker sample is a sustain loop so that stopping it lets the sample run
 * out.</p>
 */
public final class UltModule extends de.quippy.javamod.multimedia.mod.loader.Module {

    private static final String[] EXTENSIONS = {"ult"};
    private static final String MOD_ID = "ULT";
    private static final String[] VERSIONS = {"<1.4", "1.4", "1.5", "1.6"};
    private static final byte[] MAGIC = "MAS_UTrack_V00".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private static final int DEFAULT_SPEED = 6;
    private static final int DEFAULT_TEMPO = 125;
    private static final int PANNING_SEPARATION = 128;
    private static final int SPEED = 'A' - 'A' + 1;
    private static final int TEMPO = 'T' - 'A' + 1;
    private static final int PANNING = 'X' - 'A' + 1;

    /**
     * UltraTracker's first note sits two octaves above Impulse Tracker's, and its samples are tuned an octave
     * up to meet it.
     */
    private static final int NOTE_OFFSET = 24;
    private static final int SPEED_SCALE = 2;
    private static final double FINETUNE_PER_OCTAVE = 12.0 * 32768.0;
    private static final int LOWEST_FREQUENCY = 256;

    /**
     * OpenMPT mixes a module of a tracker that kept no mixing volume of its own twice as loud as the mixer
     * here does at Impulse Tracker's lowest pre-amp.
     */
    private static final int MIXING_PRE_AMP = ModConstants.MIN_MIXING_PREAMP * 2;

    /**
     * An UltraTracker sample volume runs to 255 where Impulse Tracker's runs to 64.
     */
    private static final int VOLUME_SCALE = 4;

    private String message = "";
    private int[] panning = new int[0];

    public UltModule() {
    }

    private UltModule(String fileName) {
        super(fileName);
    }

    static UltModule of(String fileName, byte[] bytes) throws IOException {
        final UltModule module = new UltModule(fileName);
        module.load(bytes);
        return module;
    }

    private void load(byte[] bytes) throws IOException {
        final UltFile file = UltReader.read(bytes);
        setModType(ModConstants.MODTYPE_IT);
        setModID(MOD_ID);
        setTrackerName("UltraTracker " + VERSIONS[file.version() - '1']);
        setSongName(file.title());
        message = file.message();
        panning = file.panning();

        setNChannels(file.channels());
        setNPattern(file.patterns().length);
        setNInstruments(file.samples().length);
        setNSamples(file.samples().length);
        setTempo(DEFAULT_SPEED);
        setBPMSpeed(DEFAULT_TEMPO);
        setBaseVolume(ModConstants.MAXGLOBALVOLUME);
        setMixingPreAmp(MIXING_PRE_AMP);
        setSongRestart(0);
        setSongFlags(ModConstants.SONG_ISSTEREO | ModConstants.SONG_ITOLDEFFECTS | ModConstants.SONG_ITCOMPATMODE);

        readArrangement(file);
        setPatternContainer(patterns(file));
        setInstrumentContainer(samples(file));
    }

    /**
     * An order naming a pattern the file does not have is passed over.
     */
    private void readArrangement(UltFile file) throws IOException {
        final int[] orders = Arrays.stream(file.orders()).filter(pattern -> pattern < getNPattern()).toArray();
        if (orders.length == 0) {
            throw new IOException("UltraTracker module that plays no pattern");
        }
        setSongLength(orders.length);
        allocArrangement(orders.length);
        System.arraycopy(orders, 0, getArrangement(), 0, orders.length);
    }

    /**
     * The file writes one channel's events through every pattern before the next channel's, and a command
     * governing the whole song that found no room in its own event is written into the first free effect on
     * its row or a later one. A channel read after that overwrites its own events there, which is how the
     * tracker's own conversion leaves them too.
     */
    private PatternContainer patterns(UltFile file) {
        final Placed[][][] placed = new Placed[file.patterns().length][UltFile.ROWS][file.channels()];
        for (final Placed[][] rows : placed) {
            for (final Placed[] row : rows) {
                Arrays.fill(row, Placed.EMPTY);
            }
        }
        boolean speedZero = false;
        for (int channel = 0; channel < file.channels(); channel++) {
            for (int pattern = 0; pattern < placed.length; pattern++) {
                for (int row = 0; row < UltFile.ROWS; row++) {
                    final UltEvent event = file.event(pattern, channel, row);
                    final UltCell cell = UltCommands.convert(event, file.version());
                    if (cell.lostEffect() != UltCell.NO_EFFECT && !repeatsTheRowBefore(file, pattern, channel, row)) {
                        placeFromRow(placed[pattern], row, cell.lostEffect(), cell.lostParam());
                    }
                    placed[pattern][row][channel] = new Placed(event.note(), event.instrument(), cell);
                    speedZero |= cell.effect() == SPEED && cell.param() == 0;
                }
            }
        }
        if (speedZero) {
            resetSpeedZero(placed);
        }
        final PatternContainer patterns = new PatternContainer(this, placed.length);
        for (int pattern = 0; pattern < placed.length; pattern++) {
            patterns.createPattern(pattern, UltFile.ROWS, file.channels());
            for (int row = 0; row < UltFile.ROWS; row++) {
                for (int channel = 0; channel < file.channels(); channel++) {
                    fill(patterns.createPatternElement(pattern, row, channel), placed[pattern][row][channel]);
                }
            }
        }
        return patterns;
    }

    /**
     * The reader hands out one event for all the rows a repeated event stands for, and the tracker's own
     * conversion writes a displaced command only on the first of them.
     */
    private static boolean repeatsTheRowBefore(UltFile file, int pattern, int channel, int row) {
        return row > 0 && file.event(pattern, channel, row) == file.event(pattern, channel, row - 1);
    }

    /**
     * A row that already carries the command keeps its own; otherwise the command takes the first free effect
     * column, or failing that pushes a panning into its volume column, and failing both tries the next row.
     */
    private static void placeFromRow(Placed[][] rows, int from, int effect, int param) {
        for (int row = from; row < rows.length; row++) {
            final Placed[] cells = rows[row];
            if (Arrays.stream(cells).anyMatch(cell -> cell.cell().effect() == effect)) {
                return;
            }
            for (int channel = 0; channel < cells.length; channel++) {
                if (cells[channel].hasFreeEffect()) {
                    cells[channel] = cells[channel].withEffect(effect, param);
                    return;
                }
            }
            for (int channel = 0; channel < cells.length; channel++) {
                if (cells[channel].cell().effect() == PANNING) {
                    cells[channel] = cells[channel].withPanningInColumn().withEffect(effect, param);
                    return;
                }
            }
        }
    }

    /**
     * A speed of nothing puts UltraTracker back at its starting speed and tempo, which Impulse Tracker only
     * knows as the two said outright.
     */
    private static void resetSpeedZero(Placed[][][] placed) {
        for (final Placed[][] rows : placed) {
            for (int row = 0; row < rows.length; row++) {
                for (int channel = 0; channel < rows[row].length; channel++) {
                    final UltCell cell = rows[row][channel].cell();
                    if (cell.effect() == SPEED && cell.param() == 0) {
                        rows[row][channel] = rows[row][channel].withEffect(SPEED, DEFAULT_SPEED);
                        placeFromRow(rows, row, TEMPO, DEFAULT_TEMPO);
                    }
                }
            }
        }
    }

    private record Placed(int note, int instrument, UltCell cell) {

        private static final Placed EMPTY = new Placed(0, 0, new UltCell(UltCell.NO_EFFECT, 0, UltCell.COLUMN_NONE,
                0, false, UltCell.NO_EFFECT, 0));

        /**
         * A volume or a stopped loop sits in the effect column where the tracker's conversion leaves it, so
         * that column is only free where neither is on the row.
         */
        private boolean hasFreeEffect() {
            return cell.effect() == UltCell.NO_EFFECT && !cell.keyOff() && cell.volumeEffect() != UltCell.COLUMN_VOLUME;
        }

        private Placed withPanningInColumn() {
            return new Placed(note, instrument, new UltCell(cell.effect(), cell.param(), UltCell.COLUMN_PANNING,
                    (cell.param() + 2) / 4, cell.keyOff(), UltCell.NO_EFFECT, 0));
        }

        private Placed withEffect(int effect, int param) {
            return new Placed(note, instrument, new UltCell(effect, param, cell.volumeEffect(), cell.volumeParam(),
                    cell.keyOff(), UltCell.NO_EFFECT, 0));
        }
    }

    /**
     * A stopped loop on a row with no note of its own lets the sounding note go; on a row with a note it has
     * nothing yet to stop.
     */
    private static void fill(PatternElement element, Placed placed) {
        final UltCell cell = placed.cell();
        if (placed.note() > 0) {
            final int note = placed.note() + NOTE_OFFSET;
            element.setNoteIndex(note);
            element.setPeriod(note <= ModConstants.noteValues.length ? ModConstants.noteValues[note - 1] : 1);
        } else if (cell.keyOff()) {
            element.setNoteIndex(ModConstants.KEY_OFF);
            element.setPeriod(ModConstants.KEY_OFF);
        }
        element.setInstrument(placed.instrument());
        element.setEffekt(cell.effect());
        element.setEffektOp(cell.param());
        element.setVolumeEffekt(cell.volumeEffect());
        element.setVolumeEffektOp(cell.volumeParam());
    }

    private InstrumentsContainer samples(UltFile file) {
        final InstrumentsContainer container = new InstrumentsContainer(this, 0, file.samples().length);
        for (int index = 0; index < file.samples().length; index++) {
            container.setSample(index, sample(file.samples()[index]));
        }
        return container;
    }

    private Sample sample(UltSample ult) {
        final Sample sample = new Sample();
        final boolean wide = ult.has(UltSample.SIXTEEN_BIT);
        final int loopDivisor = wide ? 2 : 1;
        sample.name = ult.name();
        sample.dosFileName = ult.fileName();
        sample.ITPingPongCorrection = 1;
        sample.sampleLength = sample.byteLength = ult.data().length;
        sample.sustainLoopStart = ult.loopStart() / loopDivisor;
        sample.sustainLoopStop = ult.loopEnd() / loopDivisor;
        sample.sustainLoopLength = sample.sustainLoopStop - sample.sustainLoopStart;
        sample.loopType = (ult.has(UltSample.LOOP) ? ModConstants.LOOP_SUSTAIN_ON : 0)
                | (ult.has(UltSample.PING_PONG_LOOP) ? ModConstants.LOOP_SUSTAIN_IS_PINGPONG : 0);
        sample.volume = Math.min((ult.volume() + VOLUME_SCALE / 2) / VOLUME_SCALE, ModConstants.MAXSAMPLEVOLUME);
        sample.globalVolume = ModConstants.MAXSAMPLEVOLUME;
        sample.defaultPanning = ModConstants.PANNING_CENTER;
        sample.sampleType = ModConstants.SM_PCMS | (wide ? ModConstants.SM_16BIT : 0);
        sample.baseFrequency = frequency(ult);
        if (sample.sampleLength > 0) {
            sample.allocSampleData();
            for (int frame = 0; frame < ult.data().length; frame++) {
                sample.sampleL[frame] = wide
                        ? ModConstants.promoteSigned16BitToSigned32Bit(ult.data()[frame])
                        : ModConstants.promoteSigned8BitToSigned32Bit(ult.data()[frame]);
            }
        }
        sample.fixSampleLoops(getModType());
        return sample;
    }

    /**
     * The finetune is a fraction of a semitone in steps of 32768 to it.
     */
    private static int frequency(UltSample ult) {
        final long frequency = Math.round(ult.speed() * SPEED_SCALE * Math.pow(2, ult.finetune() / FINETUNE_PER_OCTAVE));
        return frequency == 0 ? ModConstants.BASEFREQUENCY : (int) Math.max(frequency, LOWEST_FREQUENCY);
    }

    @Override
    public BasicModMixer getModMixer(int sampleRate, int doISP, int doAmigaEmulation, int doNoLoops,
            int maxNNAChannels) {
        return new ScreamTrackerMixer(this, sampleRate, doISP, doAmigaEmulation, doNoLoops, maxNNAChannels);
    }

    @Override
    public String[] getFileExtensionList() {
        return EXTENSIONS;
    }

    @Override
    public int getFrequencyTable() {
        return ModConstants.IT_AMIGA_TABLE;
    }

    @Override
    public int getPanningValue(int channel) {
        return panning[channel];
    }

    @Override
    public int getChannelVolume(int channel) {
        return ModConstants.MAXSAMPLEVOLUME;
    }

    @Override
    public int getPanningSeparation() {
        return PANNING_SEPARATION;
    }

    @Override
    public String getSongMessage() {
        return message.isEmpty() ? null : message;
    }

    @Override
    public MidiMacros getMidiConfig() {
        return null;
    }

    @Override
    public boolean getFT2Tremolo() {
        return false;
    }

    @Override
    public boolean getModSpeedIsTicks() {
        return false;
    }

    @Override
    public boolean supportsAmigaFilter() {
        return false;
    }

    @Override
    public boolean checkLoadingPossible(ModfileInputStream inputStream) throws IOException {
        final byte[] magic = new byte[MAGIC.length];
        inputStream.seek(0);
        inputStream.read(magic);
        inputStream.seek(0);
        return Arrays.equals(magic, MAGIC);
    }

    @Override
    protected de.quippy.javamod.multimedia.mod.loader.Module getNewInstance(String fileName) {
        return new UltModule(fileName);
    }

    @Override
    protected void loadModFileInternal(ModfileInputStream inputStream) throws IOException {
        final byte[] bytes = new byte[(int) inputStream.getLength()];
        inputStream.seek(0);
        inputStream.read(bytes);
        load(bytes);
    }
}
