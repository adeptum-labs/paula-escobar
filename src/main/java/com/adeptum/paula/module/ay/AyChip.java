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

/**
 * The General Instrument AY-3-8910 and its Yamaha twin the YM2149, the chip behind ZX Spectrum and Atari ST
 * music. Ported from Peter Sovietov's ayumi, which runs the generators at the chip's own clock and carries
 * the result down to the output rate through an interpolator and a 192-tap filter rather than sampling it
 * coarsely, so that the tones do not alias.
 */
public final class AyChip {

    /**
     * The two chips differ only in how a volume step becomes a voltage: the AY's steps come in pairs, the
     * YM's are all distinct.
     */
    public enum Voicing {
        AY, YM
    }

    public static final int CHANNELS = 3;

    private static final int DECIMATE_FACTOR = 8;
    private static final int FIR_SIZE = 192;
    private static final int FIR_HALF = FIR_SIZE / 2;
    private static final int DC_FILTER_SIZE = 1024;
    private static final int LOUDEST = 31;
    private static final double CENTRE_TAP = 0.125;

    private static final double[] AY_LEVELS = {
            0.0, 0.0, 0.00999465934234, 0.00999465934234,
            0.0144502937362, 0.0144502937362, 0.0210574502174, 0.0210574502174,
            0.0307011520562, 0.0307011520562, 0.0455481803616, 0.0455481803616,
            0.0644998855573, 0.0644998855573, 0.107362478065, 0.107362478065,
            0.126588845655, 0.126588845655, 0.20498970016, 0.20498970016,
            0.292210269322, 0.292210269322, 0.372838941024, 0.372838941024,
            0.492530708782, 0.492530708782, 0.635324635691, 0.635324635691,
            0.805584802014, 0.805584802014, 1.0, 1.0
    };

    private static final double[] YM_LEVELS = {
            0.0, 0.0, 0.00465400167849, 0.00772106507973,
            0.0109559777218, 0.0139620050355, 0.0169985503929, 0.0200198367285,
            0.024368657969, 0.029694056611, 0.0350652323186, 0.0403906309606,
            0.0485389486534, 0.0583352407111, 0.0680552376593, 0.0777752346075,
            0.0925154497597, 0.111085679408, 0.129747463188, 0.148485542077,
            0.17666895552, 0.211551079576, 0.246387426566, 0.281101701381,
            0.333730067903, 0.400427252613, 0.467383840696, 0.53443198291,
            0.635172045472, 0.75800717174, 0.879926756695, 1.0
    };

    private static final double[] FIR = {
            -0.0000046183113992051936, -0.00001117761640887225, -0.000018610264502005432, -0.000025134586135631012,
            -0.000028494281690666197, -0.000026396828793275159, -0.000017094212558802156, 0.000023798193576966866,
            0.000051281160242202183, 0.00007762197826243427, 0.000096759426664120416, 0.00010240229300393402,
            0.000089344614218077106, 0.000054875700118949183, -0.000069839082210680165, -0.0001447966132360757,
            -0.00021158452917708308, -0.00025535069106550544, -0.00026228714374322104, -0.00022258805927027799,
            -0.00013323230495695704, 0.00016182578767055206, 0.00032846175385096581, 0.00047045611576184863,
            0.00055713851457530944, 0.00056212565121518726, 0.00046901918553962478, 0.00027624866838952986,
            -0.00032564179486838622, -0.00065182310286710388, -0.00092127787309319298, -0.0010772534348943575,
            -0.0010737727700273478, -0.00088556645390392634, -0.00051581896090765534, 0.00059548767193795277,
            0.0011803558710661009, 0.0016527320270369871, 0.0019152679330965555, 0.0018927324805381538,
            0.0015481870327877937, 0.00089470695834941306, -0.0010178225878206125, -0.0020037400552054292,
            -0.0027874356824117317, -0.003210329988021943, -0.0031540624117984395, -0.0025657163651900345,
            -0.0014750752642111449, 0.0016624165446378462, 0.0032591192839069179, 0.0045165685815867747,
            0.0051838984346123896, 0.0050774264697459933, 0.0041192521414141585, 0.0023628575417966491,
            -0.0026543507866759182, -0.0051990251084333425, -0.0072020238234656924, -0.0082672928192007358,
            -0.0081033739572956287, -0.006583111539570221, -0.0037839040415292386, 0.0042781252851152507,
            0.0084176358598320178, 0.01172566057463055, 0.013550476647788672, 0.013388189369997496,
            0.010979501242341259, 0.006381274941685413, -0.007421229604153888, -0.01486456304340213,
            -0.021143584622178104, -0.02504275058758609, -0.025473530942547201, -0.021627310017882196,
            -0.013104323383225543, 0.017065133989980476, 0.036978919264451952, 0.05823318062093958,
            0.079072012081405949, 0.097675998716952317, 0.11236045936950932, 0.12176343577287731
    };

    private final double[] levels;
    private final double step;
    private final Voice[] voices = {new Voice(), new Voice(), new Voice()};
    private final double[] firLeft = new double[FIR_SIZE * 2];
    private final double[] firRight = new double[FIR_SIZE * 2];
    private final Interpolator interpolatorLeft = new Interpolator();
    private final Interpolator interpolatorRight = new Interpolator();
    private final DcFilter dcLeft = new DcFilter();
    private final DcFilter dcRight = new DcFilter();

    private int noisePeriod = 1;
    private int noiseCounter;
    private int noise = 1;
    private int envelopePeriod = 1;
    private int envelopeCounter;
    private int envelopeShape;
    private int envelopeSegment;
    private int envelope;
    private int firIndex;
    private int dcIndex;
    private double phase;
    private double left;
    private double right;

    public AyChip(Voicing voicing, double clockRate, int sampleRate) {
        this.levels = voicing == Voicing.YM ? YM_LEVELS : AY_LEVELS;
        this.step = clockRate / (sampleRate * 8.0 * DECIMATE_FACTOR);
    }

    /**
     * Zero to the left, one to the right. The chip is mono, so a ZX or Atari tune is spread by giving each
     * channel a place of its own.
     */
    public void pan(int channel, double pan) {
        voices[channel].panLeft = 1 - pan;
        voices[channel].panRight = pan;
    }

    public void tone(int channel, int period) {
        voices[channel].tonePeriod = atLeastOne(period & 0xfff);
    }

    public void noise(int period) {
        noisePeriod = atLeastOne(period & 0x1f);
    }

    public void mixer(int channel, boolean toneOff, boolean noiseOff, boolean envelopeOn) {
        final Voice voice = voices[channel];
        voice.toneOff = toneOff ? 1 : 0;
        voice.noiseOff = noiseOff ? 1 : 0;
        voice.envelopeOn = envelopeOn;
    }

    public void volume(int channel, int volume) {
        voices[channel].volume = volume & 0xf;
    }

    public void envelope(int period) {
        envelopePeriod = atLeastOne(period & 0xffff);
    }

    public void envelopeShape(int shape) {
        envelopeShape = shape & 0xf;
        envelopeCounter = 0;
        envelopeSegment = 0;
        resetSegment();
    }

    public double left() {
        return left;
    }

    public double right() {
        return right;
    }

    /**
     * Carries the chip forward by one frame of the output rate, leaving the sound in {@link #left()} and
     * {@link #right()} with its standing offset taken off.
     */
    public void next() {
        process();
        left = dcLeft.pass(dcIndex, left);
        right = dcRight.pass(dcIndex, right);
        dcIndex = (dcIndex + 1) & (DC_FILTER_SIZE - 1);
    }

    /**
     * A period of zero counts as one, since the counters compare against it after stepping.
     */
    private static int atLeastOne(int period) {
        return Math.max(1, period);
    }

    private void process() {
        final int base = FIR_SIZE - firIndex * DECIMATE_FACTOR;
        firIndex = (firIndex + 1) % (FIR_SIZE / DECIMATE_FACTOR - 1);
        for (int at = DECIMATE_FACTOR - 1; at >= 0; at--) {
            phase += step;
            if (phase >= 1) {
                phase -= 1;
                mix();
                interpolatorLeft.take(left);
                interpolatorRight.take(right);
            }
            firLeft[base + at] = interpolatorLeft.at(phase);
            firRight[base + at] = interpolatorRight.at(phase);
        }
        left = decimate(firLeft, base);
        right = decimate(firRight, base);
    }

    /**
     * The filter is symmetric about its middle and every eighth tap is zero, so only the first half of the
     * non-zero taps is carried; the terms are summed in the order ayumi sums them, so that the arithmetic
     * rounds the same way.
     */
    private static double decimate(double[] window, int base) {
        double sum = FIR[0] * (window[base + 1] + window[base + FIR_SIZE - 1]);
        int tap = 1;
        for (int at = 2; at < FIR_HALF; at++) {
            if (at % DECIMATE_FACTOR != 0) {
                sum += FIR[tap++] * (window[base + at] + window[base + FIR_SIZE - at]);
            }
        }
        final double decimated = sum + CENTRE_TAP * window[base + FIR_HALF];
        System.arraycopy(window, base, window, base + FIR_SIZE - DECIMATE_FACTOR, DECIMATE_FACTOR);
        return decimated;
    }

    private void mix() {
        final int noiseBit = nextNoise();
        final int envelopeLevel = nextEnvelope();
        left = 0;
        right = 0;
        for (final Voice voice : voices) {
            final int gated = (voice.nextTone() | voice.toneOff) & (noiseBit | voice.noiseOff);
            final double level = levels[gated * (voice.envelopeOn ? envelopeLevel : voice.volume * 2 + 1)];
            left += level * voice.panLeft;
            right += level * voice.panRight;
        }
    }

    /**
     * A seventeen-bit shift register tapped at bits zero and three, stepped at half the tone rate.
     */
    private int nextNoise() {
        noiseCounter++;
        if (noiseCounter >= noisePeriod << 1) {
            noiseCounter = 0;
            noise = (noise >> 1) | (((noise ^ (noise >> 3)) & 1) << 16);
        }
        return noise & 1;
    }

    private int nextEnvelope() {
        envelopeCounter++;
        if (envelopeCounter >= envelopePeriod) {
            envelopeCounter = 0;
            step(SHAPES[envelopeShape][envelopeSegment]);
        }
        return envelope;
    }

    private void step(Slope slope) {
        switch (slope) {
            case UP -> {
                envelope++;
                if (envelope > LOUDEST) {
                    turn();
                }
            }
            case DOWN -> {
                envelope--;
                if (envelope < 0) {
                    turn();
                }
            }
            default -> {
            }
        }
    }

    private void turn() {
        envelopeSegment ^= 1;
        resetSegment();
    }

    private void resetSegment() {
        final Slope slope = SHAPES[envelopeShape][envelopeSegment];
        envelope = slope == Slope.DOWN || slope == Slope.HOLD_TOP ? LOUDEST : 0;
    }

    private enum Slope {
        UP, DOWN, HOLD_TOP, HOLD_BOTTOM
    }

    /**
     * What the envelope does in its first pass and in every pass after it, for each of the sixteen shapes.
     */
    private static final Slope[][] SHAPES = {
            {Slope.DOWN, Slope.HOLD_BOTTOM}, {Slope.DOWN, Slope.HOLD_BOTTOM},
            {Slope.DOWN, Slope.HOLD_BOTTOM}, {Slope.DOWN, Slope.HOLD_BOTTOM},
            {Slope.UP, Slope.HOLD_BOTTOM}, {Slope.UP, Slope.HOLD_BOTTOM},
            {Slope.UP, Slope.HOLD_BOTTOM}, {Slope.UP, Slope.HOLD_BOTTOM},
            {Slope.DOWN, Slope.DOWN}, {Slope.DOWN, Slope.HOLD_BOTTOM},
            {Slope.DOWN, Slope.UP}, {Slope.DOWN, Slope.HOLD_TOP},
            {Slope.UP, Slope.UP}, {Slope.UP, Slope.HOLD_TOP},
            {Slope.UP, Slope.DOWN}, {Slope.UP, Slope.HOLD_BOTTOM}
    };

    private static final class Voice {

        private int tonePeriod = 1;
        private int toneCounter;
        private int tone;
        private int toneOff;
        private int noiseOff;
        private boolean envelopeOn;
        private int volume;
        private double panLeft;
        private double panRight;

        private int nextTone() {
            toneCounter++;
            if (toneCounter >= tonePeriod) {
                toneCounter = 0;
                tone ^= 1;
            }
            return tone;
        }
    }

    /**
     * Four samples of the chip's own output fitted with a parabola, so that the sound between two of them is
     * a curve rather than a step.
     */
    private static final class Interpolator {

        private final double[] coefficients = new double[3];
        private final double[] samples = new double[4];

        private void take(double sample) {
            samples[0] = samples[1];
            samples[1] = samples[2];
            samples[2] = samples[3];
            samples[3] = sample;
            final double slope = samples[2] - samples[0];
            coefficients[0] = 0.5 * samples[1] + 0.25 * (samples[0] + samples[2]);
            coefficients[1] = 0.5 * slope;
            coefficients[2] = 0.25 * (samples[3] - samples[1] - slope);
        }

        private double at(double phase) {
            return (coefficients[2] * phase + coefficients[1]) * phase + coefficients[0];
        }
    }

    /**
     * Takes off the standing offset the volume table leaves behind, as the mean of the last thousand frames.
     */
    private static final class DcFilter {

        private final double[] delay = new double[DC_FILTER_SIZE];
        private double sum;

        private double pass(int index, double sample) {
            sum += -delay[index] + sample;
            delay[index] = sample;
            return sample - sum / DC_FILTER_SIZE;
        }
    }
}
