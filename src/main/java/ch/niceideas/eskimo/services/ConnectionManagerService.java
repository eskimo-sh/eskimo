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


public interface ConnectionManagerService {

    SSHConnection getPrivateConnection (String node) throws ConnectionManagerException;

    SSHConnection getSharedConnection (String node) throws ConnectionManagerException;

    void recreateTunnels(String host) throws ConnectionManagerException;

    void dropTunnelsToBeClosed(String host);

    class LocalPortForwarderWrapper {

        private static final Logger logger = Logger.getLogger(LocalPortForwarderWrapper.class);

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

    class RemoveForwarderException extends ConnectionManagerException {
        public RemoveForwarderException(Throwable cause) {
            super(cause);
        }
    }
}
