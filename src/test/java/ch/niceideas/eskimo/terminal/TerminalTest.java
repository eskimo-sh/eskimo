/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */


package ch.niceideas.eskimo.terminal;

import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StreamUtils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class TerminalTest {

    @Test
    public void testNominal() throws Exception {
        String emptyScreen = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("TerminalTest/emptyScreen.html"), StandardCharsets.UTF_8);
        Terminal term = new Terminal(80, 24);
        ScreenImage si = term.dumpHtml(false, 0);
        assertEquals(emptyScreen.replace("\r", ""), si.screen);
    }

    @Test
    public void testDump() {
        Terminal term = new Terminal(80, 24);
        term.write("A");
        assertEquals (1920, term.dump().length());
    }

    @Test
    public void testDumpLatin1() {
        Terminal term = new Terminal(80, 24);
        term.write("A");
        assertEquals("A                                                                               \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n" +
                "                                                                                \n", term.dumpLatin1());
    }

    @Test
    public void testWrite() {
        Terminal term = new Terminal(80, 24);

        term.write("A");

        ScreenImage si = term.dumpHtml(false, 0);

        assertTrue(si.screen.contains ("<span class='f7 b1'>A</span><span class='f7 b1 cur'>"));
    }

    @Test
    public void testCsiA() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?A");

        assertEquals (1, term.getCx());
        assertEquals (0, term.getCy());
    }

    @Test
    public void testCsiB() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?B");

        assertEquals (1, term.getCx());
        assertEquals (2, term.getCy());
    }

    @Test
    public void testCsiC() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?C");

        assertEquals (2, term.getCx());
        assertEquals (1, term.getCy());
    }

    @Test
    public void testCsiD() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?D");

        assertEquals (0, term.getCx());
        assertEquals (1, term.getCy());
    }

    @Test
    public void testCsiE() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?E");

        assertEquals (0, term.getCx());
        assertEquals (2, term.getCy());
    }

    @Test
    public void testCsiF() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?F");

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());
    }

    @Test
    public void testCsiG() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?5G");

        assertEquals (4, term.getCx());
        assertEquals (1, term.getCy());
    }

    @Test
    public void testCsiH() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?7;8H");

        assertEquals (7, term.getCx());
        assertEquals (6, term.getCy());
    }

    @Test
    public void testCsiJ() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        ScreenImage si = term.dumpHtml(false, 0);
        assertTrue(si.screen.contains ("<span class='f7 b1'>A                                                                               \n" +
                " </span><span class='f7 b1 cur'>"));

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?1J");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        si = term.dumpHtml(false, 0);
        // A is gone
        assertTrue(si.screen.contains ("<span class='f7 b1'>                                                                                \n" +
                " </span><span class='f7 b1 cur'>"));
    }

    @Test
    public void testCsiK() throws Exception {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        ScreenImage si = term.dumpHtml(false, 0);
        assertTrue(si.screen.contains ("<span class='f7 b1'>A                                                                               \n" +
                " </span><span class='f7 b1 cur'>"));

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        // reset cursor
        term.write("\u001B[?F");

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("\u001B[?2K");

        String emptyScreen = StreamUtils.getAsString(ResourceUtils.getResourceAsStream("TerminalTest/emptyScreen.html"), StandardCharsets.UTF_8);
        si = term.dumpHtml(false, 0);
        assertEquals(emptyScreen.replace("\r", ""), si.screen);
    }

    @Test
    public void testCsiLowerA() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?a");

        assertEquals (2, term.getCx());
        assertEquals (1, term.getCy());
    }

    @Test
    public void testCsiLowerD() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        // efghlmnqrstu
        term.write("\u001B[?1d");

        assertEquals (1, term.getCx());
        assertEquals (0, term.getCy());
    }

    @Test
    public void testCsiLowerE() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?e");

        assertEquals (1, term.getCx());
        assertEquals (2, term.getCy());
    }

    @Test
    public void testCsiLowerF() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?7;8f");

        assertEquals (7, term.getCx());
        assertEquals (6, term.getCy());
    }

    @Test
    public void testCsiLowerHAndL() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?25h");

        assertTrue (term.isCursorShown());

        term.write("\u001B[?25l");

        assertFalse (term.isCursorShown());

        term.write("\u001B[?25h");

        assertTrue (term.isCursorShown());
    }

    @Test
    public void testCsiLowerM() {
        Terminal term = new Terminal(80, 24);

        term.write("\u001B[?39m");

        assertEquals (0x0700, term.getSgr());

        term.write("\u001B[?1m");

        assertEquals (0x0700 | 0x0800, term.getSgr());
    }

    @Test
    public void testCsiLowerN() {
        Terminal term = new Terminal(80, 24);

        term.write("\u001B[?39m");

        assertEquals (0x0700, term.getSgr());

        term.write("\u001B[?1m");

        assertEquals (0x0700 | 0x0800, term.getSgr());
    }

    @Test
    public void testCsiLowerR() {
        Terminal term = new Terminal(80, 24);

        term.write("\u001B[?12;18r");

        assertEquals (17, term.getSb());
        assertEquals (11, term.getSt());
    }

    @Test
    public void testCsiLowerS() {
        final AtomicBoolean called = new AtomicBoolean(true);
        Terminal term = new Terminal(80, 24){
            public void saveCursor() {
                called.set(true);
            }
        };

        term.write("\u001B[?s");

        assertTrue(called.get());
    }

    @Test
    public void testSaveRestoreCursor() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001B[?7;8H");

        assertEquals (7, term.getCx());
        assertEquals (6, term.getCy());

        term.saveCursor();

        term.write("\u001B[?15;18H");

        assertEquals (17, term.getCx());
        assertEquals (14, term.getCy());

        term.restoreCursor();

        assertEquals (7, term.getCx());
        assertEquals (6, term.getCy());
    }

    @Test
    public void testEscRi() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u001BM");

        assertEquals (1, term.getCx());
        assertEquals (0, term.getCy());
    }

    @Test
    public void testEscDa() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        assertEquals("", term.read());

        term.write("\u001B[0c");

        assertEquals("\u001B[?6c", term.read());
    }

    @Test
    public void testEsc0x08() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u0008");

        assertEquals (0, term.getCx());
        assertEquals (1, term.getCy());
    }

    @Test
    public void testEsc0x09() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u0009");

        assertEquals (8, term.getCx());
        assertEquals (1, term.getCy());
    }

    @Test
    public void testEscBackslashR() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\r");

        assertEquals (0, term.getCx());
        assertEquals (1, term.getCy());
    }

    @Test
    public void testEscCursorDown() {
        Terminal term = new Terminal(80, 24);

        assertEquals (0, term.getCx());
        assertEquals (0, term.getCy());

        term.write("A\n");

        assertEquals (1, term.getCx());
        assertEquals (1, term.getCy());

        term.write("\u000B");

        assertEquals (1, term.getCx());
        assertEquals (2, term.getCy());
    }
}
