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



package ch.niceideas.eskimo.test.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.SSHConnection;
import ch.niceideas.eskimo.model.service.proxy.ProxyTunnelConfig;
import ch.niceideas.eskimo.services.ConnectionManagerException;
import ch.niceideas.eskimo.services.ConnectionManagerService;
import ch.niceideas.eskimo.services.ConnectionManagerServiceImpl;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.types.Node;
import com.trilead.ssh2.LocalPortForwarder;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-connection-manager")
public class ConnectionManagerServiceTestImpl extends ConnectionManagerServiceImpl implements ConnectionManagerService {

    private final AtomicBoolean recreateTunnelsCalled = new AtomicBoolean(false);

    final List<String> openedForwarders = new ArrayList<>();
    final List<String> closedForwarders = new ArrayList<>();

    final List<String> createCalledFor = new ArrayList<>();
    final List<Node> dropCalledFor = new ArrayList<>();

    private boolean doConnect = true;

    public List<String> getCreateCallFor() {
        return Collections.unmodifiableList(createCalledFor);
    }

    public List<Node> getDropCallFor() {
        return Collections.unmodifiableList(dropCalledFor);
    }

    public boolean isRecreateTunnelsCalled() {
        return recreateTunnelsCalled.get();
    }

    public void reset() {
        recreateTunnelsCalled.set(false);
        connectionMap.clear();
        portForwardersMap.clear();
        connectionAges.clear();
        connectionsToCloseLazily.clear();
        resetCountersOnly();
        doConnect = true;
    }

    public void resetCountersOnly() {
        createCalledFor.clear();
        dropCalledFor.clear();
        openedForwarders.clear();
        closedForwarders.clear();
    }

    public void dontConnect() {
        doConnect = false;
    }

    public void setPrivateSShKeyContent(String privateSShKeyContent) {
        this.privateSShKeyContent = privateSShKeyContent;
    }

    public void setSShPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public List<String> getOpenedForwarders() {
        return Collections.unmodifiableList(openedForwarders);
    }

    public List<String> getClosedForwarders() {
        return Collections.unmodifiableList(closedForwarders);
    }

    @Override
    public SSHConnection getPrivateConnection(Node node) throws ConnectionManagerException {
        return super.getPrivateConnection(node);
    }

    @Override
    public SSHConnection getSharedConnection(Node node) throws ConnectionManagerException {
        return super.getSharedConnection(node);
    }

    @Override
    public void recreateTunnels(Node host) throws ConnectionManagerException {
        recreateTunnelsCalled.set(true);
        super.recreateTunnels(host);
    }

    @Override
    protected LocalPortForwarderWrapper createPortForwarder(SSHConnection connection, ProxyTunnelConfig config) throws ConnectionManagerException {
        openedForwarders.add (config.getLocalPort() + "/" + config.getNode() + "/" + config.getRemotePort());
        return new LocalPortForwarderWrapper(
                config.getService(), connection, config.getLocalPort(), config.getNode(), config.getRemotePort()) {
            @Override
            public void close() {
                closedForwarders.add (config.getLocalPort() + "/" + config.getNode() + "/" + config.getRemotePort());
                super.close();
            }
        };
    }

    @Override
    protected SSHConnection createConnectionInternal(Node node, int operationTimeout) throws IOException, SetupException, FileException {
        SSHConnection connection = new SSHConnection(node, sshPort, operationTimeout) {
            private boolean isClosed = false;
            @Override
            public synchronized LocalPortForwarder createLocalPortForwarder(int localPort, String hostToConnect, int portToConnect) {
                createCalledFor.add(""+ portToConnect);
                return null;
            }
            @Override
            public synchronized void sendIgnorePacket() throws IOException {
                if (isClosed) {
                    throw new IOException("channel is closed");
                }
                super.sendIgnorePacket();
            }
            public void close() {
                isClosed = true;
                super.close();
            }
        };

        if (doConnect) {
            return connect(connection);
        } else {
            return connection;
        }
    }

    @Override
    protected void dropTunnelsToBeClosed(SSHConnection connection, Node node)  {
        super.dropTunnelsToBeClosed(connection, node);
        dropCalledFor.add(node);
    }

    @Override
    public void dropTunnelsToBeClosed(Node host) {
        super.dropTunnelsToBeClosed(host);
    }
}
