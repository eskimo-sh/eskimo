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
import ch.niceideas.eskimo.model.ProxyTunnelConfig;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.LocalPortForwarder;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ConnectionManagerService {

    private static final Logger logger = Logger.getLogger(ConnectionManagerService.class);

    @Autowired
    private SetupService setupService;

    @Autowired
    private ProxyManagerService proxyManagerService;

    private ReentrantLock connectionMapLock = new ReentrantLock();

    private Map<String, Connection> connectionMap = new HashMap<>();
    private Map<Connection, List<LocalPortForwarderWrapper>> portForwardersMap = new HashMap<>();

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
    void setProxyManagerService (ProxyManagerService proxyManagerService) {
        this.proxyManagerService = proxyManagerService;
    }


    public Connection getConnection (String ipAddress) throws ConnectionManagerException {

        connectionMapLock.lock();

        try {

            Connection connection = connectionMap.get(ipAddress);

            // if connection doesn't exist, create it
            if (connection == null) {
                logger.info ("Creating connection to " + ipAddress);
                connection = new Connection(ipAddress, sshPort);
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

                recreateTunnels(connection, ipAddress);

                if (!connection.isAuthenticationComplete()) {
                    throw new IOException("Authentication failed");
                }

                connectionMap.put(ipAddress, connection);
                connectionAges.put(ipAddress, System.currentTimeMillis());
            }

            // otherwise test it and attempt to recreate it if it is down or too old
            else {

                try {

                    Long connectionAge = connectionAges.get(ipAddress);

                    if (connectionAge + maximumConnectionAge < System.currentTimeMillis()) {
                        logger.warn ("Previous connection to " + ipAddress + " is too old. Recreating ...");
                        connectionMap.remove(ipAddress);
                        connectionAges.remove(ipAddress);
                        closeConnection(connection);
                        return getConnection(ipAddress);
                    }

                    ConnectionOperationWatchDog sg = new ConnectionOperationWatchDog(connection);
                    //connection.ping(); // this is too buggy !!! Waits for the socket outputStream result like forever and seems impossible to kill
                    connection.sendIgnorePacket();
                    sg.close();

                    // update connection age
                    connectionAges.put(ipAddress, System.currentTimeMillis());

                } catch (IOException | IllegalStateException e) {
                    logger.warn ("Previous connection to " + ipAddress + " got into problems. Recreating ...");
                    connectionMap.remove(ipAddress);
                    connectionAges.remove(ipAddress);
                    return getConnection(ipAddress);
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


    public void forceRecreateConnection(String ipAddress) {
        connectionMapLock.lock();

        try {

            Connection connection = connectionMap.get(ipAddress);

            if (connection == null) {
                throw new IllegalStateException();
            }

            closeConnection(connection);

            connectionMap.remove(ipAddress);
            connectionAges.remove(ipAddress);

        } finally  {
            connectionMapLock.unlock();
        }
    }

    private void closeConnection (Connection connection) {
        try {
            logger.info ("Closing connection");

            for (LocalPortForwarderWrapper localPortForwarder : portForwardersMap.get(connection)) {
                localPortForwarder.close();
            }

            connection.close();
        } catch (Exception e) {
            logger.debug (e, e);
        }
    }


    private void recreateTunnels(Connection connection, String ipAddress) throws ConnectionManagerException {

        // Find out about declared forwarders to be handled
        List<ProxyTunnelConfig> tunnelConfigs = proxyManagerService.getTunnelConfigForHost(ipAddress);

        // close port forwarders that are not declared anymore
        final List<LocalPortForwarderWrapper> previousForwarders = getForwarders(connection);
        List<LocalPortForwarderWrapper> toBeClosed = previousForwarders.stream()
                .filter(forwarder -> notIn (forwarder, tunnelConfigs))
                .collect(Collectors.toList());

        try {
            for (LocalPortForwarderWrapper forwarder : toBeClosed) {
                forwarder.close();
                previousForwarders.remove(forwarder);
            }
        } catch (Exception e) {
            logger.warn(e.getMessage());
            logger.debug(e, e);
        }

        // recreate those that need to be recreated
        List<ProxyTunnelConfig> toBeCreated = tunnelConfigs.stream()
                .filter(config -> notIn (config, previousForwarders))
                .collect(Collectors.toList());

        for (ProxyTunnelConfig config : toBeCreated) {
            previousForwarders.add (new LocalPortForwarderWrapper(
                    connection, config.getLocalPort(), config.getRemoteAddress(), config.getRemotePort()));
        }
    }

    private List<LocalPortForwarderWrapper> getForwarders(Connection connection) {
        List<LocalPortForwarderWrapper> previousForwarders = portForwardersMap.get(connection);
        if (previousForwarders == null) {
            previousForwarders = new ArrayList<>();
            portForwardersMap.put(connection, previousForwarders);
        }
        return previousForwarders;
    }

    private boolean notIn(ProxyTunnelConfig config, List<LocalPortForwarderWrapper> previousForwarders) {
        return !previousForwarders.stream().anyMatch(forwarder -> forwarder.matches(config));
    }

    private boolean notIn(LocalPortForwarderWrapper forwarder, List<ProxyTunnelConfig> tunnelConfigs) {
        return !tunnelConfigs.stream().anyMatch(config -> forwarder.matches(config));
    }

    public void recreateTunnels(String host) throws ConnectionManagerException {
        recreateTunnels(getConnection(host), host);
    }

    private class ConnectionOperationWatchDog {

        private final Timer timer;

        public ConnectionOperationWatchDog(Connection connection) {
            this.timer = new Timer(true);

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    closeConnection(connection);
                }
            }, sshOperationTimeout);
        }

        public void close() {
            timer.cancel();
        }
    }

    private class LocalPortForwarderWrapper {

        private final LocalPortForwarder forwarder;

        private final int localPort;
        private final String targetHost;
        private final int targetPort;

        public LocalPortForwarderWrapper (Connection connection, int localPort, String targetHost, int targetPort) throws ConnectionManagerException {
            this.localPort = localPort;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            try {
                logger.info ("Creating tunnel from " + localPort + " to " + targetHost + ":" + targetPort);
                this.forwarder = connection.createLocalPortForwarder(localPort, targetHost, targetPort);
            } catch (IOException e) {
                logger.error (e, e);
                throw new ConnectionManagerException(e);
            }
        }

        public void close() {
            try {
                forwarder.close();
            } catch (IOException e) {
                logger.warn (e.getMessage());
                logger.debug (e, e);
            }
        }

        public boolean matches (String targetHost, int targetPort) {
            return targetHost.equals(this.targetHost) && (targetPort == this.targetPort);
        }

        public boolean matches(ProxyTunnelConfig config) {
            return matches(config.getRemoteAddress(), config.getRemotePort());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            LocalPortForwarderWrapper that = (LocalPortForwarderWrapper) o;
            return targetPort == that.targetPort &&
                    Objects.equals(targetHost, that.targetHost);
        }

        @Override
        public int hashCode() {
            return Objects.hash(targetHost, targetPort);
        }
    }
}
