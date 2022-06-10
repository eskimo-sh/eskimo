package ch.niceideas.eskimo.terminal;

import ch.niceideas.common.utils.FileException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class SessionTest {

    private AtomicReference<String> sizeSet = new AtomicReference<>();

    /** Run Test on Linux only */
    @BeforeEach
    public void beforeMethod() {
        Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    @Test
    public void testNominal() throws Exception {
        Session s = new Session(null,80,25, getTestProcessWithPty());
        assertTrue(s.isAlive());
        s.write("export PS1=:\n");
        s.write("echo hello world\n");
        Thread.sleep(1000);
        String dump = s.getTerminal().dumpLatin1();
        assertTrue(dump.contains("hello world"));
        assertEquals("80x25", sizeSet.get());
        s.write("exit 3\n");
        s.join(2000);
        assertFalse(s.isAlive());
        Thread.sleep(1000);
        assertEquals(3, s.getChildProcess().exitValue());
    }


    private ProcessWithPty getTestProcessWithPty() throws IOException, FileException {

        ProcessBuilder pb = new ProcessBuilder();
        pb.redirectErrorStream(true);
        List<String> pyCmds = pb.command();
        pyCmds.add("/bin/bash");
        final Process base = pb.start();

        return new ProcessWithPty() {
            final DataOutputStream out = new DataOutputStream(base.getOutputStream());
            @Override
            public void setWindowSize(int width, int height) throws IOException {
                sizeSet.set(width+"x"+height);
            }

            @Override
            public void kill(int signal) throws IOException {
                out.write(0x02);
                out.writeShort(2);
                out.writeShort(signal);
                out.flush();
            }

            @Override
            public OutputStream getOutputStream() {
                return base.getOutputStream();
            }

            @Override
            public InputStream getInputStream() {
                return base.getInputStream();
            }

            @Override
            public InputStream getErrorStream() {
                return base.getErrorStream();
            }

            @Override
            public int waitFor() throws InterruptedException {
                return base.waitFor();
            }

            @Override
            public int exitValue() {
                return base.exitValue();
            }

            @Override
            public void destroy() {
                base.destroy();
            }
        };
    }
}
