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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.ResourceUtils;
import ch.niceideas.common.utils.StringUtils;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.Session;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;

@Component
public class SSHCommandService {

    private static final Logger logger = Logger.getLogger(SSHCommandService.class);

    @Autowired
    private ConnectionManagerService connectionManagerService;

    @Autowired
    private ConfigurationService configurationService;

    @Value("${connectionManager.sshOperationTimeout}")
    private int sshOperationTimeout = 30000;

    /** For tests */
    public void setConnectionManagerService(ConnectionManagerService connectionManagerService) {
        this.connectionManagerService = connectionManagerService;
    }

    public String runSSHScript(String hostAddress, String script) throws SSHCommandException {
        return runSSHScript(hostAddress, script, true);
    }

    public String runSSHCommand(String hostAddress, String[] command) throws SSHCommandException {
        StringBuilder sb = new StringBuilder();
        for (String cmd : command) {
            sb.append (cmd);
            sb.append (" ");
        }
        return runSSHCommand(hostAddress, sb.toString());
    }

    public String runSSHScriptPath(String hostAddress, String scriptName) throws SSHCommandException {

        InputStream scriptIs = ResourceUtils.getResourceAsStream(scriptName);
        if (scriptIs == null) {
            throw new SSHCommandException("Impossible to load script " + scriptName);
        }

        try (BufferedReader reader = new BufferedReader (new InputStreamReader(scriptIs))) {
            String line = null;
            StringBuilder scriptBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                scriptBuilder.append(line);
                scriptBuilder.append("\n");
            }

            return runSSHScript(hostAddress, scriptBuilder.toString());

        } catch (IOException e) {
            logger.error(e, e);
            throw new SSHCommandException(e);
        }

    }

    public String runSSHScript(String hostAddress, String script, boolean throwsException) throws SSHCommandException {

        Session session = null;
        try (ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
             ByteArrayOutputStream baosErr = new ByteArrayOutputStream()) {

            Connection connection = connectionManagerService.getSharedConnection(hostAddress);

            session = connection.openSession();
            session.execCommand("bash --login -s");

            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(session.getStdin()));

            out.write(script);
            out.close();

            PumpThread t1 = new PumpThread(session.getStdout(), baosOut);
            t1.start();
            PumpThread t2 = new PumpThread(session.getStderr(), baosErr);
            t2.start();

            session.getStdin().close();
            t1.join();
            t2.join();

            // wait for some time since the delivery of the exit status often gets delayed
            session.waitForCondition(ChannelCondition.EXIT_STATUS, sshOperationTimeout);
            Integer r = session.getExitStatus();

            if (r == null) {
                throw new IOException("Could not get a return status within " + sshOperationTimeout + " milliseconds");
            }

            int retValue = r;

            String outResult = baosOut.toString();
            String errResult = baosErr.toString();

            String result = outResult
                    + (StringUtils.isNotBlank(outResult) && StringUtils.isNotBlank(errResult) ? "\n" : "")
                    + errResult;

            if (retValue == 0 || !throwsException) {
                return result;
            } else {
                throw new SSHCommandException(result);
            }

        } catch (ConnectionManagerException e) {
            logger.error(e.getMessage());
            logger.debug(e, e);
            throw new SSHCommandException(e);

        } catch (InterruptedException | IOException e) {
            logger.error(e, e);
            throw new SSHCommandException(e);

        } finally {
            if (session != null) {
                session.close();
            }
        }
    }


    public String runSSHCommand(String hostAddress, String command) throws SSHCommandException {
        try {
            Connection connection = connectionManagerService.getSharedConnection(hostAddress);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int retValue = connection.exec(command, baos);

            if (retValue == 0) {
                return baos.toString();
            } else {
                throw new SSHCommandException("Command exited with return code " + retValue + "\n" + baos.toString());
            }

        } catch (ConnectionManagerException | InterruptedException | IOException e) {
            logger.error (e, e);
            throw new SSHCommandException(e);
        }
    }

    public void copySCPFile(String hostAddress, String filePath) throws SSHCommandException {

        try {

            JsonWrapper systemConfig = new JsonWrapper(configurationService.loadSetupConfig());

            Connection connection = connectionManagerService.getSharedConnection(hostAddress);

            SCPClient scp = new SCPClient(connection);

            scp.put(filePath, "/home/" + systemConfig.getValueForPath("ssh_username"), "0755");

            // scp is stateless and doesn't nee to be closed

        } catch (ConnectionManagerException | IOException  | FileException | JSONException | SetupException e) {
            logger.error (e, e);
            throw new SSHCommandException(e);
        }
    }

    private static final class PumpThread extends Thread {
        private final InputStream in;
        private final OutputStream out;

        /**
         * Instantiates a new Pump thread.
         *
         * @param in  the in
         * @param out the out
         */
        PumpThread(InputStream in, OutputStream out) {
            super("pump thread");
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            byte[] buf = new byte[1024];
            try {
                while(true) {
                    int len = in.read(buf);
                    if(len<0) {
                        in.close();
                        return;
                    }
                    out.write(buf,0,len);
                }
            } catch (IOException e) {
                logger.warn(e.getMessage());
                logger.debug(e, e);
            }
        }
    }
}
