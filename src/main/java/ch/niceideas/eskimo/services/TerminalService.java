/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.terminal.ScreenImage;
import ch.niceideas.eskimo.terminal.Session;
import ch.niceideas.eskimo.terminal.SshProcessWithPty;
import com.trilead.ssh2.Connection;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;


@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class TerminalService {

    private static final Logger logger = Logger.getLogger(TerminalService.class);

    @Value("${terminal.defaultSessionWidth}")
    private int defaultSessionWidth = 120;

    @Value("${terminal.defaultSessionHeight}")
    private int defaultSessionWHeight = 30;

    @Value("${terminal.idleTimeoutSeconds}")
    private long idleTimeoutSeconds = 1800;

    @Autowired
    private ConnectionManagerService connectionManagerService;

    /* Controlers are singleton */
    private Map<String, Session> sessions = new ConcurrentHashMap<>();

    private final Timer timer;

    public TerminalService () {

        this.timer = new Timer(true);

        logger.info ("Initializing terminal closer scheduler ...");
        timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    for (String sessionId : new ArrayList<String>(sessions.keySet())) {
                        Session session = sessions.get (sessionId);
                        if (System.currentTimeMillis() - session.getLastAccess() > idleTimeoutSeconds * 1000) {
                            try {
                                logger.info("Closing session with ID " + sessionId + " (idle tineout)");
                                removeTerminal(sessionId);
                            } catch (IOException e) {
                                logger.debug (e, e);
                            }
                        }
                    }
                }
            }, idleTimeoutSeconds / (long) 10 * 1000, idleTimeoutSeconds / (long) 10 * 1000);
    }

    @PreDestroy
    public void destroy() {
        logger.info ("Cancelling terminal closer scheduler");
        timer.cancel();
    }

    /** For tests */
    void setConnectionManagerService(ConnectionManagerService connectionManagerService) {
        this.connectionManagerService = connectionManagerService;
    }

    public void removeTerminal (String sessionId) throws IOException {
        logger.debug(sessionId);

        Session session = sessions.get(sessionId);
        if (session == null) {
            logger.error("Session not found : " + sessionId);
        } else {

            sessions.remove(sessionId);

            try {
                session.getChildProcess().getOutputStream().close();
            } catch (IOException e) {
                logger.error(e, e);
                throw new IOException(e.getMessage(), e);
            } finally {
                session.getChildProcess().destroy();

                // Closing private connection
                session.getConnection().close();
            }
        }
    }

    public ScreenImage postUpdate(String terminalBody) throws IOException {
       logger.debug(terminalBody);

       try {

           String s = extractArgument (terminalBody, "s");

           String w = extractArgument (terminalBody, "w");
           String h = extractArgument (terminalBody, "h");

           Session session = getSession(
                   terminalBody,
                   StringUtils.isBlank(w) ? defaultSessionWidth : Integer.parseInt(w),
                   StringUtils.isBlank(h) ? defaultSessionWHeight : Integer.parseInt(h),
                   s);

           String k = urlDecode(terminalBody);
           String c = extractArgument (terminalBody, "c");
           String tStr = extractArgument (terminalBody, "t");
           int t = StringUtils.isBlank(tStr) || tStr.equals("null") ? 0 : Integer.parseInt(tStr);

           return session.handleUpdate(k, StringUtils.isNotBlank(c), t);

       } catch (IOException | InterruptedException | ConnectionManagerException e) {
           logger.error (e, e);
           throw new IOException(e.getMessage(), e);

       }
    }

    String urlDecode(String terminalBody) {
        try {
            return URLDecoder.decode(extractArgument (terminalBody,  "k"), StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            logger.error (e, e);
            throw new TerminalException(e);
        }
    }

    synchronized Session getSession(String terminalBody, int sessionWidth, int sessionHeight, String sessionId) throws ConnectionManagerException, IOException {
        Session session = sessions.get(sessionId);

        if (session == null) {

            String node = extractArgument (terminalBody, "node");

            Connection con = connectionManagerService.getPrivateConnection(node);

            com.trilead.ssh2.Session innerSession = con.openSession();

            innerSession.requestPTY(Session.AJAX_TERM_NAME, sessionWidth, sessionHeight, 0, 0, null);
            innerSession.startShell();

            session = new Session(con, sessionWidth, sessionHeight, new SshProcessWithPty(innerSession));

            sessions.put(sessionId, session);

        } else {

            // test it
            try {
                ((SshProcessWithPty) session.getChildProcess()).getSession().ping();
            } catch (IOException e) {
                logger.warn ("Session got into problems. Recreating");
                logger.warn (e.getMessage());
                logger.debug (e, e);

                try {
                    removeTerminal(sessionId);
                } catch (IOException ignored) {
                    logger.debug (ignored);
                }
                return getSession(terminalBody, sessionWidth, sessionHeight, sessionId);
            }
        }
        return session;
    }

    private static String extractArgument(String source, String argument) {
        int start = source.indexOf(argument+"=");
        int end = source.indexOf('&', start + (argument.length() + 1));
        if (end == -1) {
            end = source.length();
        }
        return source.substring(start + (argument.length() + 1), end);
    }

    public static class TerminalException extends RuntimeException {

        static final long serialVersionUID = -331151672312431248L;

        TerminalException(Throwable cause) {
            super(cause);
        }
    }
}
