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

/**
 * The Z80 core of JSpeccy by José Luis Sánchez (https://github.com/jsanchezv/Z80Core, GPL-3, commit
 * dcd133716c723e81489a2794f6652d6b3b6186a3), vendored unmodified. It passes ZEXALL and the Fuse test suite and
 * counts T-states per bus cycle, which is what lets the Spectrum machine model contention. See Z80CORE-LICENSE.txt.
 */
package z80core;
