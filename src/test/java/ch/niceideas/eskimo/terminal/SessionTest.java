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

import ch.niceideas.eskimo.utils.ActiveWaiter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class SessionTest {

    private final AtomicReference<String> sizeSet = new AtomicReference<>();

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
        // FIXME How can I do this one with ActiveWaiter.wait() ?
        Thread.sleep(1000);
        String dump = s.getTerminal().dumpLatin1();
        assertTrue(dump.contains("hello world"));
        assertEquals("80x25", sizeSet.get());
        s.write("exit 3\n");
        s.join(2000);
        assertFalse(s.isAlive());
        ActiveWaiter.wait(() -> s.getChildProcess().exitValue() == 3);
        assertEquals(3, s.getChildProcess().exitValue());
    }


    private ProcessWithPty getTestProcessWithPty() throws IOException {

        ProcessBuilder pb = new ProcessBuilder();
        pb.redirectErrorStream(true);
        List<String> pyCmds = pb.command();
        pyCmds.add("/bin/bash");
        final Process base = pb.start();

        return new ProcessWithPty() {
            final DataOutputStream out = new DataOutputStream(base.getOutputStream());
            @Override
            public void setWindowSize(int width, int height) {
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
