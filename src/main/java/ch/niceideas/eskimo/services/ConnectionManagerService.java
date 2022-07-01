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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.model.SSHConnection;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
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
import java.net.BindException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ConnectionManagerService {

    private static final Logger logger = Logger.getLogger(ConnectionManagerService.class);

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private ConfigurationService configurationService;

    @Value("${connectionManager.defaultSSHPort}")
    private int sshPort = 22;

    @Value("${connectionManager.tcpConnectionTimeout}")
    private int tcpConnectionTimeout = 30000;

    @Value("${connectionManager.sshKeyExchangeTimeout}")
    private int sshKeyExchangeTimeout = 20000;

    @Value("${connectionManager.maximumConnectionAge}")
    private int maximumConnectionAge = 600000;

    @Value("${connectionManager.sshOperationTimeout}")
    private int sshOperationTimeout = 120000;

    @Value("${connectionManager.scriptOperationTimeout}")
    private int scriptOperationTimeout = 1800000;

    @Value("${connectionManager.statusOperationTimeout}")
    private int statusOperationTimeout = 1800000;

    private final ReentrantLock connectionMapLock = new ReentrantLock();

    private final Map<String, SSHConnection> connectionMap = new HashMap<>();
    private final Map<SSHConnection, List<LocalPortForwarderWrapper>> portForwardersMap = new HashMap<>();

    private final Map<String, Long> connectionAges = new HashMap<>();

    private String privateSShKeyContent = null;

    private final List<SSHConnection> connectionsToCloseLazily = new ArrayList<>();

    private final Timer timer;

    // constructor for spring
    public ConnectionManagerService() {
        this.timer = new Timer(true);

        logger.info ("Initializing connection closer scheduler ...");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                for (SSHConnection connection : connectionsToCloseLazily) {
                    logger.info ("Lazily closing connection to " + connection.getHostname());
                    closeConnection(connection);
                }
                connectionsToCloseLazily.clear();
            }
        }, maximumConnectionAge, maximumConnectionAge);
    }

    private void __dumpPortForwardersMap () {
        portForwardersMap.keySet().forEach(sshConnection -> {
           logger.debug(" - " + sshConnection.getHostname());
           portForwardersMap.get(sshConnection).forEach(forwarder -> logger.debug("   + " + forwarder.toString()));
        });
        logger.debug("");
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
    public void setProxyManagerService (ProxyManagerService proxyManagerService) {
        this.proxyManagerService = proxyManagerService;
    }
    public void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public SSHConnection getPrivateConnection (String node) throws ConnectionManagerException {
        try {
            return createConnectionInternal(node, scriptOperationTimeout);
        } catch (IOException | JSONException | FileException | SetupException e) {
            logger.error (e.getMessage());
            logger.debug (e, e);
            throw new ConnectionManagerException(e);
        }
    }

    public SSHConnection getSharedConnection (String node) throws ConnectionManagerException {
        return getConnectionInternal(node);
    }

    private void closeConnection (SSHConnection connection) {
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

    private void removeConnectionAndRegisterClose(String node, SSHConnection connection) {
        connectionMap.remove(node);
        connectionAges.remove(node);

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

        connectionsToCloseLazily.add (connection);
    }

    public void recreateTunnels(String host) throws ConnectionManagerException {
        recreateTunnels(getConnectionInternal(host), host);
    }

    public void dropTunnelsToBeClosed(String host) {
        SSHConnection connection = connectionMap.get(host);
        if (connection != null) {
            dropTunnelsToBeClosed(connection, host);
        }
    }

    private SSHConnection getConnectionInternal (String node) throws ConnectionManagerException {

        connectionMapLock.lock();

        try {

            SSHConnection connection = connectionMap.get(node);

            // if connection doesn't exist, create it
            if (connection == null) {
                connection = createConnectionInternal(node, statusOperationTimeout);

                recreateTunnels(connection, node);

                connectionMap.put(node, connection);
                connectionAges.put(node, System.currentTimeMillis());
            }

            // otherwise test it and attempt to recreate it if it is down or too old
            else {

                try {

                    Long connectionAge = connectionAges.get(node);

                    if (connectionAge + maximumConnectionAge < System.currentTimeMillis()) {
                        logger.warn ("Previous connection to " + node + " is too old. Recreating ...");
                        removeConnectionAndRegisterClose(node, connection);
                        return getConnectionInternal(node);
                    }

                    try (ConnectionOperationWatchDog sg = new ConnectionOperationWatchDog(connection)) {
                        //connection.ping(); // this is too buggy !!! Waits for the socket outputStream result like forever and seems impossible to kill
                        connection.sendIgnorePacket();
                    }

                    // update connection age
                    connectionAges.put(node, System.currentTimeMillis());

                } catch (IOException | IllegalStateException e) {
                    logger.warn ("Previous connection to " + node + " got into problems ("+e.getMessage()+"). Recreating ...");
                    removeConnectionAndRegisterClose(node, connection);
                    return getConnectionInternal(node);
                }
            }

            return connection;

        } catch (IOException | JSONException | FileException | SetupException e) {
            logger.error ("When recreating connection to " + node +" - got " + e.getClass() + ":" + e.getMessage());
            logger.debug (e, e);
            throw new ConnectionManagerException(e);

        } finally  {
            connectionMapLock.unlock();
        }

    }

    protected SSHConnection createConnectionInternal(String node, int operationTimeout) throws IOException, FileException, SetupException {

        logger.info ("Creating connection to " + node);
        SSHConnection connection = new SSHConnection(node, sshPort, operationTimeout);
        connection.setTCPNoDelay(true);
        connection.connect(null, tcpConnectionTimeout, sshKeyExchangeTimeout); // TCP timeout, Key exchange timeout

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


    protected void dropTunnelsToBeClosed(SSHConnection connection, String node) {

        // Find out about declared forwarders to be handled (those that need to stay)
        List<ProxyTunnelConfig> keptTunnelConfigs = proxyManagerService.getTunnelConfigForHost(node);

        // close port forwarders that are not declared anymore
        final List<LocalPortForwarderWrapper> currentForwarders = getForwarders(connection);
        List<LocalPortForwarderWrapper> toBeClosed = currentForwarders.stream()
                .filter(forwarder -> notIn (forwarder, keptTunnelConfigs))
                .collect(Collectors.toList());

        try {
            for (LocalPortForwarderWrapper forwarder : toBeClosed) {
                forwarder.close();
                currentForwarders.remove(forwarder);
            }
        } catch (Exception e) {
            logger.warn(e.getMessage());
            logger.debug(e, e);
        }
    }

    protected void recreateTunnels(SSHConnection connection, String node) throws ConnectionManagerException {

        if (logger.isDebugEnabled()){
            logger.debug("------ BEFORE ---- recreateTunnels (" + node + ") ----------- ");
            __dumpPortForwardersMap();
        }

        final List<LocalPortForwarderWrapper> currentForwarders = getForwarders(connection);

        dropTunnelsToBeClosed(connection, node);

        // Find out about declared forwarders to be handled
        List<ProxyTunnelConfig> tunnelConfigs = proxyManagerService.getTunnelConfigForHost(node);

        // recreate those that need to be recreated
        List<ProxyTunnelConfig> toBeCreated = tunnelConfigs.stream()
                .filter(config -> notIn (config, currentForwarders))
                .collect(Collectors.toList());

        for (ProxyTunnelConfig config : toBeCreated) {
            try {
                currentForwarders.add(createPortForwarder(connection, config));
            } catch (RemoveForwarderException e) {
                logger.warn ("Not trying any further to recreate forwarder for "
                        + config.getServiceName() + " - " + config.getNode() + " - " + config.getRemotePort());
            }
        }

        if (logger.isDebugEnabled()){
            logger.debug("------ AFTER ---- recreateTunnels (" + node + ") ----------- ");
            __dumpPortForwardersMap();
        }
    }

    protected LocalPortForwarderWrapper createPortForwarder(SSHConnection connection, ProxyTunnelConfig config) throws ConnectionManagerException {
        return new LocalPortForwarderWrapper(
                config.getServiceName(), connection, config.getLocalPort(), config.getNode(), config.getRemotePort());
    }

    private List<LocalPortForwarderWrapper> getForwarders(SSHConnection connection) {
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

        public ConnectionOperationWatchDog(SSHConnection connection) {
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

    public static class LocalPortForwarderWrapper {

        private final LocalPortForwarder forwarder;

        private final String serviceName;
        private final int localPort;
        private final String targetHost;
        private final int targetPort;

        public LocalPortForwarderWrapper (String serviceName, SSHConnection connection, int localPort, String targetHost, int targetPort) throws ConnectionManagerException {
            this.serviceName = serviceName;
            this.localPort = localPort;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            try {
                logger.info("Creating tunnel for service " + serviceName + " - from " + localPort + " to " + targetHost + ":" + targetPort);
                this.forwarder = connection.createLocalPortForwarder(localPort, targetHost, targetPort);
            } catch (BindException e) {
                logger.error (e, e);
                throw new RemoveForwarderException (e);
            } catch (IOException e) {
                logger.error (e, e);
                throw new ConnectionManagerException(e);
            }
        }

        @Override
        public String toString() {
            return serviceName + " - from " + localPort + " to " + targetHost + ":" + targetPort;
        }

        public void close() {
            try {
                logger.info ("CLOSING tunnel for service " + toString());
                if (forwarder != null) {
                    forwarder.close();
                }
            } catch (IOException e) {
                logger.warn (e.getMessage());
                logger.debug (e, e);
            }
        }

        public boolean matches (String serviceName, int localPort, String targetHost, int targetPort) {
            return serviceName.matches(this.serviceName)
                    && (localPort == this.localPort)
                    && targetHost.equals(this.targetHost)
                    && (targetPort == this.targetPort);
        }

        public boolean matches(ProxyTunnelConfig config) {
            return matches(config.getServiceName(), config.getLocalPort(), config.getNode(), config.getRemotePort());
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

    static class RemoveForwarderException extends ConnectionManagerException {
        public RemoveForwarderException(Throwable cause) {
            super(cause);
        }
    }
}
