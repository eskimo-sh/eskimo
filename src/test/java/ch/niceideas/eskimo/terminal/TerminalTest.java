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
    public void testDump() throws Exception {
        Terminal term = new Terminal(80, 24);
        term.write("A");
        assertEquals (1920, term.dump().length());
    }

    @Test
    public void testDumpLatin1() throws Exception {
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
    public void testWrite() throws Exception {
        Terminal term = new Terminal(80, 24);

        term.write("A");

        ScreenImage si = term.dumpHtml(false, 0);

        assertTrue(si.screen.contains ("<span class='f7 b1'>A</span><span class='f7 b1 cur'>"));
    }

    @Test
    public void testCsiA() throws Exception {
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
    public void testCsiB() throws Exception {
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
    public void testCsiC() throws Exception {
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
    public void testCsiD() throws Exception {
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
    public void testCsiE() throws Exception {
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
    public void testCsiF() throws Exception {
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
    public void testCsiG() throws Exception {
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
    public void testCsiH() throws Exception {
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
    public void testCsiJ() throws Exception {
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
    public void testCsiLowerA() throws Exception {
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
    public void testCsiLowerD() throws Exception {
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
    public void testCsiLowerE() throws Exception {
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
    public void testCsiLowerF() throws Exception {
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
    public void testCsiLowerHAndL() throws Exception {
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
    public void testCsiLowerM() throws Exception {
        Terminal term = new Terminal(80, 24);

        term.write("\u001B[?39m");

        assertEquals (0x0700, term.getSgr());

        term.write("\u001B[?1m");

        assertEquals (0x0700 | 0x0800, term.getSgr());
    }

    @Test
    public void testCsiLowerN() throws Exception {
        Terminal term = new Terminal(80, 24);

        term.write("\u001B[?39m");

        assertEquals (0x0700, term.getSgr());

        term.write("\u001B[?1m");

        assertEquals (0x0700 | 0x0800, term.getSgr());
    }

    @Test
    public void testCsiLowerR() throws Exception {
        Terminal term = new Terminal(80, 24);

        term.write("\u001B[?12;18r");

        assertEquals (17, term.getSb());
        assertEquals (11, term.getSt());
    }

    @Test
    public void testCsiLowerS() throws Exception {
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
    public void testEscRi() throws Exception {
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
    public void testEscDa() throws Exception {
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
    public void testEsc0x08() throws Exception {
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
    public void testEsc0x09() throws Exception {
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
    public void testEscBackslashR() throws Exception {
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
    public void testEscCursorDown() throws Exception {
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


