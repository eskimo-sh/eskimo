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
import com.trilead.ssh2.Connection;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ConnectionManagerService {

    private static final Logger logger = Logger.getLogger(ConnectionManagerService.class);

    @Autowired
    private SetupService setupService;

    private ReentrantLock connectionMapLock = new ReentrantLock();

    private Map<String, Connection> connectionMap = new HashMap<>();
    private Map<String, Long> connectionAges = new HashMap<>();

    private String privateSShKeyContent = null;

    @Value("${connectionManager.defaultSSHPort}")
    private int sshPort = 22;

    @Value("${connectionManager.tcpConnectionTimeout}")
    private int tcpConnectionTimeout = 20000;

    @Value("${connectionManager.sshKeyExchangeTimeout}")
    private int sshKeyExchangeTimeout = 20000;

    @Value("${connectionManager.maximumConnectionAge}")
    private int maximumConnectionAge = 1800000;

    @Value("${connectionManager.sshOperationTimeout}")
    private int sshOperationTimeout = 60000;

    // constructor for spring
    public ConnectionManagerService() {}

    // constructor for tests
    public ConnectionManagerService(String privateSShKeyContent, int sshPort) {
        this.privateSShKeyContent = privateSShKeyContent;
        this.sshPort = sshPort;
    }
    void setSetupService (SetupService setupService) {
        this.setupService = setupService;
    }


    public Connection getConnection (String hostAddress) throws ConnectionManagerException {

        connectionMapLock.lock();

        try {

            Connection connection = connectionMap.get(hostAddress);

            // if connection doesn't exist, create it
            if (connection == null) {
                connection = new Connection(hostAddress, sshPort);
                connection.setTCPNoDelay(true);
                connection.connect(null, tcpConnectionTimeout, tcpConnectionTimeout, sshKeyExchangeTimeout); // TCP timeout, Key exchange timeout

                JsonWrapper systemConfig = new JsonWrapper(setupService.loadSetupConfig());

                if (privateSShKeyContent == null) {
                    privateSShKeyContent = (String)systemConfig.getValueForPath("content-ssh-key");
                }

                connection.authenticateWithPublicKey(
                        (String)systemConfig.getValueForPath("ssh_username"),
                        privateSShKeyContent.toCharArray(),
                        null);

                if (!connection.isAuthenticationComplete()) {
                    throw new IOException("Authentication failed");
                }

                connectionMap.put(hostAddress, connection);
                connectionAges.put(hostAddress, System.currentTimeMillis());
            }

            // otherwise test it and attempt to recreate it if it is down or too old
            else {

                try {

                    Long connectionAge = connectionAges.get(hostAddress);

                    if (connectionAge + maximumConnectionAge < System.currentTimeMillis()) {
                        logger.warn ("Previous connection to " + hostAddress + " is too old. Recreating ...");
                        connectionMap.remove(hostAddress);
                        connectionAges.remove(hostAddress);
                        return getConnection(hostAddress);
                    }

                    ConnectionOperationWatchDog sg = new ConnectionOperationWatchDog(connection);
                    //connection.ping(); // this is too buggy !!! Waits for the socket outputStream result like forever and seems impossible to kill
                    connection.sendIgnorePacket();
                    sg.close();

                    // update connection age
                    connectionAges.put(hostAddress, System.currentTimeMillis());

                } catch (IOException | IllegalStateException e) {
                    logger.warn ("Previous connection to " + hostAddress + " got into problems. Recreating ...");
                    connectionMap.remove(hostAddress);
                    connectionAges.remove(hostAddress);
                    return getConnection(hostAddress);
                }
            }

            return connection;

        } catch (IOException | JSONException | FileException | SetupException e) {
            logger.error (e.getMessage());
            logger.debug (e, e);
            throw new ConnectionManagerException(e);

        } finally  {
            connectionMapLock.unlock();
        }

    }

    public void forceRecreateConnection(String hostAddress) {
        connectionMapLock.lock();

        try {

            Connection connection = connectionMap.get(hostAddress);

            if (connection == null) {
                throw new IllegalStateException();
            }

            connection.close();

            connectionMap.remove(hostAddress);
            connectionAges.remove(hostAddress);

        } finally  {
            connectionMapLock.unlock();
        }
    }

    private class ConnectionOperationWatchDog {

        private final Timer timer;

        public ConnectionOperationWatchDog(Connection connection) {
            this.timer = new Timer(true);

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        logger.warn ("Force closing connection");
                        connection.close();
                    } catch (Exception e) {
                        logger.debug (e, e);
                    }
                }
            }, sshOperationTimeout);
        }

        public void close() {
            timer.cancel();
        }
    }
}
