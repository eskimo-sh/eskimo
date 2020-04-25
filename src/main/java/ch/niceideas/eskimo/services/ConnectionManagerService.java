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

import javax.annotation.PreDestroy;
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

    @Autowired
    private ConfigurationService configurationService;

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

    private ReentrantLock connectionMapLock = new ReentrantLock();

    private Map<String, Connection> connectionMap = new HashMap<>();
    private Map<Connection, List<LocalPortForwarderWrapper>> portForwardersMap = new HashMap<>();

    private Map<String, Long> connectionAges = new HashMap<>();

    private String privateSShKeyContent = null;

    private List<Connection> connectionsToCloseLazily = new ArrayList<>();

    private final Timer timer;

    // constructor for spring
    public ConnectionManagerService() {
        this.timer = new Timer(true);

        logger.info ("Initializing connection closer scheduler ...");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                for (Connection connection : connectionsToCloseLazily) {
                    logger.info ("Lazily closing connection to " + connection.getHostname());
                    closeConnection(connection);
                }
                connectionsToCloseLazily.clear();
            }
        }, sshOperationTimeout, sshOperationTimeout);
    }

    @PreDestroy
    public void destroy() {
        logger.info ("Cancelling connection closer scheduler");
        timer.cancel();
    }

    // constructor for tests
    public ConnectionManagerService(String privateSShKeyContent, int sshPort) {
        this.timer = new Timer(true);
        this.privateSShKeyContent = privateSShKeyContent;
        this.sshPort = sshPort;
    }
    void setSetupService (SetupService setupService) {
        this.setupService = setupService;
    }
    void setProxyManagerService (ProxyManagerService proxyManagerService) {
        this.proxyManagerService = proxyManagerService;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public Connection getPrivateConnection (String ipAddress) throws ConnectionManagerException {
        try {
            return createConnectionInternal(ipAddress);
        } catch (IOException | JSONException | FileException | SetupException e) {
            logger.error (e.getMessage());
            logger.debug (e, e);
            throw new ConnectionManagerException(e);
        }
    }

    public Connection getSharedConnection (String ipAddress) throws ConnectionManagerException {
        return getConnectionInternal(ipAddress);
    }


    public void forceRecreateConnection(String ipAddress) {

        connectionMapLock.lock();

        try {

            Connection connection = connectionMap.get(ipAddress);

            if (connection == null) {
                throw new IllegalStateException();
            }

            connectionsToCloseLazily.add (connection);

            connectionMap.remove(ipAddress);
            connectionAges.remove(ipAddress);

            // tunnels should be closed immediately !
            final List<LocalPortForwarderWrapper> previousForwarders = getForwarders(connection);
            try {
                for (LocalPortForwarderWrapper forwarder : previousForwarders) {
                    forwarder.close();
                }
            } catch (Exception e) {
                logger.warn(e.getMessage());
                logger.debug(e, e);
            }
            portForwardersMap.remove(connection);

        } finally  {
            connectionMapLock.unlock();
        }
    }

    public void recreateTunnels(String host) throws ConnectionManagerException {
        recreateTunnels(getConnectionInternal(host), host);
    }

    public void dropTunnels (String host) {
        Connection connection = connectionMap.get(host);
        if (connection != null) {
            dropTunnels(connection, host);
        }
    }

    private Connection getConnectionInternal (String ipAddress) throws ConnectionManagerException {

        connectionMapLock.lock();

        try {

            Connection connection = connectionMap.get(ipAddress);

            // if connection doesn't exist, create it
            if (connection == null) {
                connection = createConnectionInternal(ipAddress);

                recreateTunnels(connection, ipAddress);

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
                        return getConnectionInternal(ipAddress);
                    }

                    try (ConnectionOperationWatchDog sg = new ConnectionOperationWatchDog(connection)) {
                        //connection.ping(); // this is too buggy !!! Waits for the socket outputStream result like forever and seems impossible to kill
                        connection.sendIgnorePacket();
                    }

                    // update connection age
                    connectionAges.put(ipAddress, System.currentTimeMillis());

                } catch (IOException | IllegalStateException e) {
                    logger.warn ("Previous connection to " + ipAddress + " got into problems ("+e.getMessage()+"). Recreating ...");
                    connectionMap.remove(ipAddress);
                    connectionAges.remove(ipAddress);
                    return getConnectionInternal(ipAddress);
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

    protected Connection createConnectionInternal(String ipAddress) throws IOException, FileException, SetupException {

        logger.info ("Creating connection to " + ipAddress);
        Connection connection = new Connection(ipAddress, sshPort);
        connection.setTCPNoDelay(true);
        connection.connect(null, tcpConnectionTimeout, tcpConnectionTimeout, sshKeyExchangeTimeout); // TCP timeout, Key exchange timeout

        JsonWrapper systemConfig = new JsonWrapper(configurationService.loadSetupConfig());

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
        return connection;
    }

    private void closeConnection (Connection connection) {
        try {
            logger.info ("Closing connection to " + connection.getHostname());

            for (LocalPortForwarderWrapper localPortForwarder : getForwarders(connection)) {
                localPortForwarder.close();
            }

            connection.close();
        } catch (Exception e) {
            logger.debug (e, e);
        }
    }


    protected void dropTunnels(Connection connection, String ipAddress) {

        // Find out about declared forwarders to be handled (those that need to stay)
        List<ProxyTunnelConfig> keptTunnelConfigs = proxyManagerService.getTunnelConfigForHost(ipAddress);

        // close port forwarders that are not declared anymore
        final List<LocalPortForwarderWrapper> previousForwarders = getForwarders(connection);
        List<LocalPortForwarderWrapper> toBeClosed = previousForwarders.stream()
                .filter(forwarder -> notIn (forwarder, keptTunnelConfigs))
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
    }

    protected void recreateTunnels(Connection connection, String ipAddress) throws ConnectionManagerException {

        final List<LocalPortForwarderWrapper> previousForwarders = getForwarders(connection);

        dropTunnels (connection, ipAddress);

        // Find out about declared forwarders to be handled
        List<ProxyTunnelConfig> tunnelConfigs = proxyManagerService.getTunnelConfigForHost(ipAddress);

        // recreate those that need to be recreated
        List<ProxyTunnelConfig> toBeCreated = tunnelConfigs.stream()
                .filter(config -> notIn (config, previousForwarders))
                .collect(Collectors.toList());

        for (ProxyTunnelConfig config : toBeCreated) {
            previousForwarders.add (new LocalPortForwarderWrapper(
                    config.getServiceName(), connection, config.getLocalPort(), config.getRemoteAddress(), config.getRemotePort()));
        }
    }

    private List<LocalPortForwarderWrapper> getForwarders(Connection connection) {
        return portForwardersMap.computeIfAbsent(connection, k -> new ArrayList<>());
    }

    private boolean notIn(ProxyTunnelConfig config, List<LocalPortForwarderWrapper> previousForwarders) {
        return previousForwarders.stream().noneMatch(forwarder -> forwarder.matches(config));
    }

    private boolean notIn(LocalPortForwarderWrapper forwarder, List<ProxyTunnelConfig> tunnelConfigs) {
        return tunnelConfigs.stream().noneMatch(forwarder::matches);
    }

    private class ConnectionOperationWatchDog implements AutoCloseable{

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

        private final String serviceName;
        private final int localPort;
        private final String targetHost;
        private final int targetPort;

        public LocalPortForwarderWrapper (String serviceName, Connection connection, int localPort, String targetHost, int targetPort) throws ConnectionManagerException {
            this.serviceName = serviceName;
            this.localPort = localPort;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            try {
                logger.info ("Creating tunnel for service " + serviceName + " - from " + localPort + " to " + targetHost + ":" + targetPort);
                this.forwarder = connection.createLocalPortForwarder(localPort, targetHost, targetPort);
            } catch (IOException e) {
                logger.error (e, e);
                throw new ConnectionManagerException(e);
            }
        }

        public void close() {
            try {
                logger.info ("CLOSING tunnel for service " + serviceName + " - from " + localPort + " to " + targetHost + ":" + targetPort);
                if (forwarder != null) {
                    forwarder.close();
                }
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

        public String getServiceName() {
            return serviceName;
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
