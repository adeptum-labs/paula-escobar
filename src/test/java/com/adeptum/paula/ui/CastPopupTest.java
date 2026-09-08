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

package com.adeptum.paula.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.paula.cast.CastDevice;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.Test;

class CastPopupTest {

    private static final int WIDTH = 80;
    private static final int HEIGHT = 24;
    private static final CastDevice KITCHEN = device("Kök", "Nest Audio");
    private static final CastDevice BEDROOM = device("Sovrum", "Google Nest Mini");
    private static final List<AttributedString> BLANK = Collections.nCopies(HEIGHT, AttributedString.EMPTY);

    private final CastPopup popup = new CastPopup();

    @Test
    void offersThisMachineAndEveryDeviceFound() {
        final String drawn = text(popup.draw(BLANK, List.of(KITCHEN, BEDROOM), false, null, WIDTH, HEIGHT));

        assertTrue(drawn.contains("Cast to"));
        assertTrue(drawn.contains("▶ This machine"), "marked as where the sound plays");
        assertTrue(drawn.contains("Kök (Nest Audio)"));
        assertTrue(drawn.contains("Sovrum (Google Nest Mini)"));
    }

    @Test
    void marksTheDeviceTheSoundPlaysOn() {
        final String drawn = text(popup.draw(BLANK, List.of(KITCHEN), false, KITCHEN, WIDTH, HEIGHT));

        assertTrue(drawn.contains("▶ Kök"));
        assertFalse(drawn.contains("▶ This machine"));
    }

    @Test
    void showsTheNetworkBeingAsked() {
        final String scanning = text(popup.draw(BLANK, List.of(), true, null, WIDTH, HEIGHT));
        final String quiet = text(popup.draw(BLANK, List.of(), false, null, WIDTH, HEIGHT));

        assertTrue(scanning.contains("Scanning the network"));
        assertTrue(quiet.contains("No device has answered"));
    }

    @Test
    void leavesTheScreenShowingAroundIt() {
        final List<AttributedString> screen = Collections.nCopies(HEIGHT, new AttributedString("x".repeat(WIDTH)));
        final List<AttributedString> drawn = popup.draw(screen, List.of(KITCHEN), false, null, WIDTH, HEIGHT);

        assertEquals(HEIGHT, drawn.size());
        assertTrue(drawn.getFirst().toString().startsWith("xxxx"), "the first row is untouched");
        assertTrue(drawn.get(HEIGHT / 2).toString().startsWith("xxxx"), "and the rows the panel is on keep their edges");
        assertTrue(drawn.get(HEIGHT / 2).toString().contains("│"));
    }

    @Test
    void choosesWithTheArrowsAndEnter() {
        popup.draw(BLANK, List.of(KITCHEN, BEDROOM), false, null, WIDTH, HEIGHT);

        assertTrue(popup.handle(Key.of(Key.Special.DOWN)).isEmpty());
        assertTrue(popup.handle(Key.of(Key.Special.DOWN)).isEmpty());
        final Optional<CastPopup.Choice> choice = popup.handle(Key.of(Key.Special.ENTER));

        assertEquals(BEDROOM, assertInstanceOf(CastPopup.Device.class, choice.orElseThrow()).device());
    }

    @Test
    void choosesThisMachineAtTheTop() {
        popup.draw(BLANK, List.of(KITCHEN), false, KITCHEN, WIDTH, HEIGHT);

        assertInstanceOf(CastPopup.Local.class, popup.handle(Key.of(Key.Special.ENTER)).orElseThrow());
    }

    @Test
    void choosesWithAClickOnTheRow() {
        popup.open();
        popup.draw(BLANK, List.of(KITCHEN), false, null, WIDTH, HEIGHT);
        final int top = (HEIGHT - 7) / 2;

        final Optional<CastPopup.Choice> choice = popup.handle(Key.of(new Mouse(WIDTH / 2, top + 2, false)));

        assertEquals(KITCHEN, assertInstanceOf(CastPopup.Device.class, choice.orElseThrow()).device());
    }

    @Test
    void closesOnEscapeAndOnAClickOutside() {
        popup.open();
        popup.draw(BLANK, List.of(KITCHEN), false, null, WIDTH, HEIGHT);
        popup.handle(Key.of(Key.Special.ESCAPE));
        assertFalse(popup.isOpen());

        popup.open();
        popup.handle(Key.of(new Mouse(0, 0, false)));
        assertFalse(popup.isOpen());
    }

    @Test
    void asksForAnotherLookOnR() {
        popup.draw(BLANK, List.of(), false, null, WIDTH, HEIGHT);

        assertInstanceOf(CastPopup.Rescan.class, popup.handle(Key.of('r')).orElseThrow());
    }

    private static String text(List<AttributedString> lines) {
        return lines.stream().map(AttributedString::toString).collect(Collectors.joining("\n"));
    }

    private static CastDevice device(String name, String model) {
        return new CastDevice(name, name, model, InetAddress.getLoopbackAddress(), CastDevice.CAST_PORT);
    }
}
