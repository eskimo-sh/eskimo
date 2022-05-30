/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2022 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.eskimo.model.SSHConnection;
import com.trilead.ssh2.Connection;
import lombok.Getter;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 * Represents a session.
 *
 * <p>
 * A {@link Thread} is used to shuttle data back and force between the HTTP client
 * and the process that was forked. You can check the liveness of this thread to see
 * if the child process is still alive or not.
 *
 */
public final class Session extends Thread {

    private static final Logger logger = Logger.getLogger(Session.class);

    public static final String AJAX_TERM_NAME = "linux";

    private final SSHConnection connection;

    private final ProcessWithPty childProcess;

    @Getter
    private final Terminal terminal;

    @Getter
    private final long time = System.currentTimeMillis();

    /**
     * When was this session accessed the last time?
     */
    @Getter
    private long lastAccess;

    private final Reader in;
    private final Writer out;

    /**
     *
     * @param width
     *      Width of the terminal. For example, 80.
     * @param height
     *      Height of the terminal. For example, 25.
     * @param childProcessWithTty
     *      A child process forked with pty as its stdin/stdout..
     *      Make sure to set the correct terminal name in its environment variable.
     */
    public Session(SSHConnection connection, int width, int height, ProcessWithPty childProcessWithTty) throws IOException {
        this.connection = connection;
        this.terminal = new Terminal(width,height);
        this.childProcess = childProcessWithTty;
        childProcess.setWindowSize(width,height);

        in = new InputStreamReader(childProcess.getInputStream());
        out = new OutputStreamWriter(childProcess.getOutputStream());

        setName("Terminal pump thread for "+ childProcessWithTty);
        start(); // start pumping
    }

    @Override
    public void run() {
        char[] buf = new char[128];
        int len;

        try {
            try {
                while((len=in.read(buf))>=0) {
                    terminal.write(new String(buf,0,len));
                    String reply = terminal.read();
                    if(reply!=null)
                        out.write(reply);
                }
            } catch (IOException e) {
                // fd created by forkpty seems to cause I/O error when the other side is closed via kill -9
                if (!hasChildProcessFinished())
                    logger.warn ("Session pump thread is dead", e);
            } finally {
                closeQuietly(in);
                closeQuietly(out);
            }
        } catch (Exception e) {
            logger.warn ("Session pump thread is dead", e);
        }
    }

    private boolean hasChildProcessFinished() {
        try {
            childProcess.exitValue();
            return true;
        } catch (IllegalThreadStateException e) {
            logger.debug (e, e);
            return false;
        }
    }

    private void closeQuietly(Closeable c) {
        try {
            if (c!=null)    c.close();
        } catch (IOException e) {
            // silently ignore
            logger.debug (e, e);
        }
    }

    /**
     * Receives the call from the client-side JavaScript.
     */
    public ScreenImage handleUpdate(String keys, boolean color, int clientTimestamp) throws IOException, InterruptedException {
        lastAccess = System.currentTimeMillis();
        write(keys);
        Thread.sleep(20);   // give a bit of time to let the app respond. poor version of Nagel's algorithm

        terminal.setCssClass(isAlive() ? "":"dead");
        return terminal.dumpHtml(color,clientTimestamp);
    }

    /**
     * Write to the child process.
     */
    public void write(String k) throws IOException {
        if(k!=null && k.length()!=0) {
            out.write(k);
            out.flush();
        }
    }

    public Process getChildProcess() {
        return childProcess;
    }

    public SSHConnection getConnection() {
        return connection;
    }


}
