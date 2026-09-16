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
 * The conversion follows Load_ult.cpp and modcommand.cpp of OpenMPT,
 * Copyright © 2004-2026 the OpenMPT project developers and Copyright ©
 * 1997-2003 Olivier Lapicque, which ports Storlek's reader from Schism
 * Tracker; licensed under the three-clause BSD licence and used here under
 * the GNU General Public License.
 */

package com.adeptum.paula.module.ult;

/**
 * Turns the two effects of an UltraTracker event into the one effect and one volume-column command Impulse
 * Tracker has room for.
 *
 * <p>Where both effects fit, one moves to the volume column; where they do not, the weightier one stays and
 * the other is lost, unless it governs the whole song, in which case it is handed back to be written
 * elsewhere on the row.</p>
 */
final class UltCommands {

    private static final char WITH_ARPEGGIO = '3';
    private static final char WITH_TREMOLO = '4';
    private static final char WITH_DELAY = '4';

    /**
     * The volume-column tone portamento speeds, each the effect speed its step stands for.
     */
    private static final int[] PORTAMENTO_STEPS = {0x00, 0x01, 0x04, 0x08, 0x10, 0x20, 0x40, 0x60, 0x80, 0xFF};
    private static final int LOUDEST = 64;
    private static final int REVERSE = 0x9F;

    /**
     * The commands in order of how much they matter when only one of two can stay, each with the Impulse
     * Tracker letter it plays as; the volumes always find room in the volume column and have no letter.
     */
    private enum Command {
        NONE(0), KEY_OFF(0), TREMOLO('R'), VIBRATO('H'), EXTENDED('S'), PANNING('X'), PORTAMENTO_UP('F'),
        PORTAMENTO_DOWN('E'), VOLUME_SLIDE('D'), VIBRATO_VOLUME('K'), VOLUME(0), VOLUME8(0), REVERSE_OFFSET('O'),
        OFFSET('O'), RETRIG('Q'), ARPEGGIO('J'), TONE_PORTAMENTO('G'), TONE_PORTAMENTO_VOLUME('L'), TEMPO('T'),
        SPEED('A'), PATTERN_BREAK('C');

        private final int letter;

        Command(int letter) {
            this.letter = letter == 0 ? UltCell.NO_EFFECT : letter - 'A' + 1;
        }

        private boolean outweighs(Command other) {
            return ordinal() > other.ordinal();
        }
    }

    private static final Command[] BY_NIBBLE = {
        Command.ARPEGGIO, Command.PORTAMENTO_UP, Command.PORTAMENTO_DOWN, Command.TONE_PORTAMENTO,
        Command.VIBRATO, Command.NONE, Command.NONE, Command.TREMOLO, Command.NONE, Command.OFFSET,
        Command.VOLUME_SLIDE, Command.PANNING, Command.VOLUME8, Command.PATTERN_BREAK, Command.NONE, Command.SPEED
    };

    private record Effect(Command command, int param) {

        private static final Effect NONE = new Effect(Command.NONE, 0);
    }

    private record Column(int effect, int param) {

        private static final Column NONE = new Column(UltCell.COLUMN_NONE, 0);
    }

    private UltCommands() {
    }

    static UltCell convert(UltEvent event, char version) {
        Effect first = translate(event.firstEffect(), event.firstParam(), version);
        Effect second = translate(event.secondEffect(), event.secondParam(), version);
        if (first.command() == Command.OFFSET && second.command() == Command.OFFSET) {
            return cell(new Effect(Command.OFFSET, ((second.param() << 8 | first.param()) >> 6) & 0xff),
                    Column.NONE, Effect.NONE);
        } else if (first.command() == Command.OFFSET) {
            first = scaledOffset(first, second);
            if (first.param() > 0xff) {
                return cell(new Effect(Command.OFFSET, first.param() & 0xff), Column.NONE, Effect.NONE);
            }
        } else if (second.command() == Command.OFFSET) {
            second = scaledOffset(second, first);
            if (second.param() > 0xff) {
                return cell(new Effect(Command.OFFSET, second.param() & 0xff), Column.NONE, Effect.NONE);
            }
        } else if (first.command() == second.command()) {
            second = Effect.NONE;
        }
        if (second.command() == Command.VOLUME
                || second.command() == Command.NONE && first.command() != Command.VOLUME) {
            final Effect swapped = first;
            first = second;
            second = swapped;
        }
        final Effect combined = combined(second, first);
        return combined == null ? fillIn(first, second) : fillIn(Effect.NONE, combined);
    }

    private static Effect translate(int nibble, int param, char version) {
        final Command command = BY_NIBBLE[nibble & 0x0f];
        return switch (nibble & 0x0f) {
            case 0x00 -> param == 0 || version < WITH_ARPEGGIO ? Effect.NONE : new Effect(command, param);
            case 0x05 -> sampleControl(param, version);
            case 0x07 -> version < WITH_TREMOLO ? Effect.NONE : new Effect(command, param);
            case 0x0a -> new Effect(command, (param & 0xf0) != 0 ? param & 0xf0 : param);
            case 0x0b -> new Effect(command, (param & 0x0f) * 0x11);
            case 0x0d -> new Effect(command, 10 * (param >> 4) + (param & 0x0f));
            case 0x0e -> special(param, version);
            case 0x0f -> new Effect(param > 0x2f ? Command.TEMPO : Command.SPEED, param);
            default -> new Effect(command, param);
        };
    }

    /**
     * The sample command can play a sample backwards and, from version 1.5, stop its loop; the stop is taken
     * as a key off, which lets a sustain loop go.
     */
    private static Effect sampleControl(int param, char version) {
        if (((param & 0x0f) == 0x0c || (param & 0xf0) == 0xc0) && version >= WITH_ARPEGGIO) {
            return new Effect(Command.KEY_OFF, 0);
        }
        return (param & 0x0f) == 0x02 || (param & 0xf0) == 0x20 ? new Effect(Command.EXTENDED, REVERSE) : Effect.NONE;
    }

    private static Effect special(int param, char version) {
        final int value = param & 0x0f;
        return switch (param >> 4) {
            case 0x1 -> new Effect(Command.PORTAMENTO_UP, 0xf0 | value);
            case 0x2 -> new Effect(Command.PORTAMENTO_DOWN, 0xf0 | value);
            case 0x8 -> version >= WITH_DELAY ? new Effect(Command.EXTENDED, 0x60 | value) : Effect.NONE;
            case 0x9 -> new Effect(Command.RETRIG, value);
            case 0xa -> new Effect(Command.VOLUME_SLIDE, value << 4 | 0x0f);
            case 0xb -> new Effect(Command.VOLUME_SLIDE, 0xf0 | value);
            case 0xc, 0xd -> new Effect(Command.EXTENDED, param);
            default -> Effect.NONE;
        };
    }

    /**
     * A lone offset counts in quarters of what the tracker's offsets count in. Past what one byte holds it
     * keeps its full size only where the effect beside it matters less, and is otherwise cut to the largest
     * offset a byte can say.
     */
    private static Effect scaledOffset(Effect offset, Effect beside) {
        final int scaled = offset.param() * 4;
        return new Effect(Command.OFFSET,
                scaled > 0xff && Command.OFFSET.outweighs(beside.command()) ? scaled : Math.min(scaled, 0xff));
    }

    /**
     * The pairs one Impulse Tracker effect does the work of, or null where the two stay two.
     */
    private static Effect combined(Effect first, Effect second) {
        if (first.command() == Command.VOLUME_SLIDE && sharesASlide(second)) {
            return new Effect(slideWith(second.command()), first.param());
        } else if (second.command() == Command.VOLUME_SLIDE && sharesASlide(first)) {
            return new Effect(slideWith(first.command()), second.param());
        } else if (first.command() == Command.OFFSET && isReverse(second)) {
            return new Effect(Command.REVERSE_OFFSET, first.param());
        } else if (isReverse(first) && second.command() == Command.OFFSET) {
            return new Effect(Command.REVERSE_OFFSET, second.param());
        }
        return null;
    }

    private static boolean sharesASlide(Effect effect) {
        return (effect.command() == Command.VIBRATO || effect.command() == Command.TONE_PORTAMENTO_VOLUME)
                && effect.param() == 0;
    }

    private static Command slideWith(Command command) {
        return command == Command.VIBRATO ? Command.VIBRATO_VOLUME : Command.TONE_PORTAMENTO_VOLUME;
    }

    private static boolean isReverse(Effect effect) {
        return effect.command() == Command.EXTENDED && effect.param() == REVERSE;
    }

    /**
     * Tries each effect in the volume column, first exactly and then at whatever precision it takes, and
     * keeps the weightier in the effect column when neither fits.
     */
    private static UltCell fillIn(Effect first, Effect second) {
        if (first.command() == second.command() && isAbsolute(first.command())) {
            second = Effect.NONE;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            final Effect tried = attempt % 2 == 0 ? first : second;
            final Effect other = attempt % 2 == 0 ? second : first;
            final Column column = column(tried, attempt > 1);
            if (tried.command() == Command.NONE || column != null) {
                return cell(other, column == null ? Column.NONE : column, Effect.NONE);
            }
        }
        final Effect kept = first.command().outweighs(second.command()) ? first : second;
        final Effect lighter = kept == first ? second : first;
        if (lighter.command() == Command.OFFSET && lighter.param() == 0) {
            return cell(kept, Column.NONE, Effect.NONE);
        }
        return cell(kept, Column.NONE, lighter);
    }

    private static boolean isAbsolute(Command command) {
        return switch (command) {
            case ARPEGGIO, PANNING, OFFSET, VOLUME, PATTERN_BREAK, SPEED, TEMPO, KEY_OFF, REVERSE_OFFSET,
                    VOLUME8 -> true;
            default -> false;
        };
    }

    /**
     * The volume-column command an effect becomes, or null where it has none. Forcing it gives up precision
     * rather than the effect; a play control has no volume-column command in Impulse Tracker, so a forced
     * one is taken as placed and dropped.
     */
    private static Column column(Effect effect, boolean force) {
        final int param = effect.param();
        return switch (effect.command()) {
            case VOLUME -> new Column(UltCell.COLUMN_VOLUME, Math.min(param, LOUDEST));
            case VOLUME8 -> !force && (param & 3) != 0 ? null : new Column(UltCell.COLUMN_VOLUME, (param + 3) / 4);
            case PORTAMENTO_UP -> portamento(UltCell.COLUMN_PITCH_UP, param, force);
            case PORTAMENTO_DOWN -> portamento(UltCell.COLUMN_PITCH_DOWN, param, force);
            case TONE_PORTAMENTO -> tonePortamento(param, force);
            case VIBRATO -> vibrato(param, force);
            case PANNING -> new Column(UltCell.COLUMN_PANNING, param == 0xff ? LOUDEST : param / 4);
            case VOLUME_SLIDE -> volumeSlide(param);
            case EXTENDED -> extended(param, force);
            default -> null;
        };
    }

    /**
     * The volume column's slides are four times the effect column's, so a slide that does not divide evenly
     * or grows too large only moves there when forced.
     */
    private static Column portamento(int columnEffect, int param, boolean force) {
        return !force && ((param & 3) != 0 || param >= 0xe0) ? null : new Column(columnEffect, param / 4);
    }

    private static Column tonePortamento(int param, boolean force) {
        if (param >= 0xf0) {
            return new Column(UltCell.COLUMN_TONE_PORTAMENTO, 9);
        }
        for (int step = 0; step < PORTAMENTO_STEPS.length; step++) {
            if (force ? param <= PORTAMENTO_STEPS[step] : param == PORTAMENTO_STEPS[step]) {
                return new Column(UltCell.COLUMN_TONE_PORTAMENTO, step);
            }
        }
        return null;
    }

    private static Column vibrato(int param, boolean force) {
        if (force) {
            return new Column(UltCell.COLUMN_VIBRATO_DEPTH, Math.min(param & 0x0f, 9));
        }
        return (param & 0x0f) > 9 || (param & 0xf0) != 0 ? null : new Column(UltCell.COLUMN_VIBRATO_DEPTH, param);
    }

    private static Column volumeSlide(int param) {
        if (param == 0) {
            return null;
        } else if ((param & 0x0f) == 0) {
            return new Column(UltCell.COLUMN_SLIDE_UP, param >> 4);
        } else if ((param & 0xf0) == 0) {
            return new Column(UltCell.COLUMN_SLIDE_DOWN, param);
        } else if ((param & 0x0f) == 0x0f) {
            return new Column(UltCell.COLUMN_FINE_UP, param >> 4);
        } else if ((param & 0xf0) == 0xf0) {
            return new Column(UltCell.COLUMN_FINE_DOWN, param & 0x0f);
        }
        return null;
    }

    private static Column extended(int param, boolean force) {
        if ((param & 0xf0) == 0x80) {
            return new Column(UltCell.COLUMN_PANNING, ((param & 0x0f) << 2) + 2);
        }
        return (param & 0xf0) == 0x90 && param >= 0x9e && force ? Column.NONE : null;
    }

    private static UltCell cell(Effect effect, Column column, Effect lost) {
        if (effect.command() == Command.VOLUME || effect.command() == Command.VOLUME8) {
            return cell(effectOf(column), column(effect, true), lost);
        }
        final boolean keyOff = effect.command() == Command.KEY_OFF;
        final Effect kept = keyOff ? Effect.NONE : effect;
        final Effect handedOn = governsTheSong(lost) ? lost : Effect.NONE;
        return new UltCell(kept.command().letter, kept.command().letter == UltCell.NO_EFFECT ? 0 : kept.param(),
                column.effect(), column.param(), keyOff, handedOn.command().letter, handedOn.param());
    }

    /**
     * OpenMPT plays a volume from its effect column, which Impulse Tracker cannot, so there the volume takes
     * the volume column and what was there goes back to the effect it stands for.
     */
    private static Effect effectOf(Column column) {
        final int param = column.param();
        return switch (column.effect()) {
            case UltCell.COLUMN_SLIDE_UP -> new Effect(Command.VOLUME_SLIDE, param << 4);
            case UltCell.COLUMN_SLIDE_DOWN -> new Effect(Command.VOLUME_SLIDE, param);
            case UltCell.COLUMN_FINE_UP -> new Effect(Command.VOLUME_SLIDE, param << 4 | 0x0f);
            case UltCell.COLUMN_FINE_DOWN -> new Effect(Command.VOLUME_SLIDE, 0xf0 | param);
            case UltCell.COLUMN_PITCH_UP -> new Effect(Command.PORTAMENTO_UP, param * 4);
            case UltCell.COLUMN_PITCH_DOWN -> new Effect(Command.PORTAMENTO_DOWN, param * 4);
            case UltCell.COLUMN_TONE_PORTAMENTO -> new Effect(Command.TONE_PORTAMENTO, PORTAMENTO_STEPS[param]);
            case UltCell.COLUMN_VIBRATO_DEPTH -> new Effect(Command.VIBRATO, param);
            case UltCell.COLUMN_PANNING -> new Effect(Command.PANNING, param == LOUDEST ? 0xff : param * 4);
            default -> Effect.NONE;
        };
    }

    private static boolean governsTheSong(Effect effect) {
        return switch (effect.command()) {
            case PATTERN_BREAK, SPEED, TEMPO -> true;
            case EXTENDED -> switch (effect.param() & 0xf0) {
                case 0x60, 0x90, 0xb0, 0xe0 -> true;
                default -> false;
            };
            default -> false;
        };
    }
}
