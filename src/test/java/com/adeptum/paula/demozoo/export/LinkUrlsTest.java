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

package com.adeptum.paula.demozoo.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinkUrlsTest {

    @Test
    void buildsTheUrlOfEveryLinkClass() {
        assertEquals("https://demozoo.org/", url("BaseUrl", "https://demozoo.org/"));
        assertEquals("https://files.scene.org/view/a.zip", url("SceneOrgFile", "/a.zip"));
        assertEquals("https://files.scene.org/browse/parties", url("SceneOrgFolder", "/parties"));
        assertEquals("https://ftp.modland.com/pub/modules/x.mod", url("ModlandFile", "/pub/modules/x.mod"));
        assertEquals("https://amp.dascene.net/downmod.php?index=123", url("AmpModule", "123"));
        assertEquals("https://fujiology.org/files/x.zip", url("FujiologyFile", "/files/x.zip"));
        assertEquals("https://ftp.untergrund.net/x.zip", url("UntergrundFile", "/x.zip"));
        assertEquals("http://ftp.amigascne.org/pub/amiga/x.adz", url("AmigascneFile", "/x.adz"));
        assertEquals("ftp://ftp.padua.org/pub/c64/x.prg", url("PaduaOrgFile", "/x.prg"));
        assertEquals("https://web.archive.org/web/2010/http://x", url("WaybackMachinePage", "2010/http://x"));
        assertEquals("https://modarchive.org/module.php?123", url("ModarchiveModule", "123"));
    }

    @Test
    void quotesSceneOrgParametersFromTheirIso88591Bytes() {
        assertEquals("https://files.scene.org/view/parties/2000/a%20b%20%E4.zip",
                url("SceneOrgFile", "/parties/2000/a b ä.zip"));
        assertEquals("https://files.scene.org/browse/parties%20and%20more",
                url("SceneOrgFolder", "/parties and more"));
    }

    @Test
    void quotesCharactersBeyondIso88591FromTheirUtf8Bytes() {
        assertEquals("https://files.scene.org/view/%E2%82%AC", url("SceneOrgFile", "/€"));
    }

    @Test
    void givesNothingForAnUnknownClassOrAnEmptyParameter() {
        assertTrue(LinkUrls.url("Nope", "x").isEmpty());
        assertTrue(LinkUrls.url(null, "x").isEmpty());
        assertTrue(LinkUrls.url("BaseUrl", null).isEmpty());
        assertTrue(LinkUrls.url("BaseUrl", "  ").isEmpty());
    }

    private static String url(String linkClass, String parameter) {
        return LinkUrls.url(linkClass, parameter).orElseThrow();
    }
}
