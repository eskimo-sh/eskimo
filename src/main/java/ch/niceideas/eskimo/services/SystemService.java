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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public interface SystemService {

    String SERVICE_PREFIX = "Service ";
    String SHOULD_NOT_HAPPEN_FROM_HERE = " should not happen from here.";

    void delegateApplyNodesConfig(NodeServiceOperationsCommand command) throws NodesConfigurationException;

    void showJournal(ServiceDefinition serviceDef, Node node) throws SystemException;

    void startService(ServiceDefinition serviceDef, Node node) throws SystemException;

    void stopService(ServiceDefinition serviceDef, Node node) throws SystemException;

    void restartService(ServiceDefinition serviceDef, Node node) throws SystemException;

    void callCommand(String commandId, Service serviceDef, Node node) throws SystemException;

    SystemStatusWrapper getStatus() throws StatusExceptionWrapperException;

    void updateStatus();

    void handleStatusChanges(
            ServicesInstallStatusWrapper servicesInstallationStatus, SystemStatusWrapper systemStatus,
            Set<Node> configuredNodesAndOtherLiveNodes)
                throws FileException, SetupException;

    NodesStatus discoverAliveAndDeadNodes(Set<Node> allNodes, NodesConfigWrapper nodesConfig) throws SystemException;

    <T extends Serializable> void performPooledOperation(
            List<T> operations, int parallelism, long operationWaitTimout, PooledOperation<T> operation)
            throws SystemException;

    boolean isNodeUp(Node node);

    File createTempFile(Service service, String extension) throws IOException;

    void callUninstallScript(MessageLogger ml, SSHConnection connection, Service service) throws SystemException;

    File createRemotePackageFolder(MessageLogger ml, SSHConnection connection, Service service, String imageName) throws SystemException, IOException, SSHCommandException;

    void installationSetup(MessageLogger ml, SSHConnection connection, Node node, Service service) throws SystemException;

    void installationCleanup(MessageLogger ml, SSHConnection connection, Service service, String imageName, File tmpArchiveFile) throws SSHCommandException, SystemException;

    void applyServiceOperation(Service service, Node node, SimpleOperationCommand.SimpleOperation labelledOp, ServiceOperation<String> operation) throws SystemException;

    void feedInServiceStatus (
            Map<String, String> statusMap,
            ServicesInstallStatusWrapper servicesInstallationStatus,
            Node node,
            Node referenceNode,
            Service service,
            boolean shall,
            boolean installed,
            boolean running) throws ConnectionManagerException;

    void runPreUninstallHooks(MessageLogger ml, OperationId<?> operation) throws SystemException;

    interface PooledOperation<T> {
        void call(T operation, AtomicReference<Exception> error)
                throws SystemException;
    }

    interface ServiceOperation<V> {
        V call() throws SSHCommandException, KubernetesException;
    }

    interface StatusUpdater {
        void updateStatus (ServicesInstallStatusWrapper servicesInstallationStatus);
    }

    class PooledOperationException extends RuntimeException {

        static final long serialVersionUID = -3317632123352229248L;

        PooledOperationException(Throwable cause) {
            super(cause);
        }
    }

    class StatusExceptionWrapperException extends Exception {

        static final long serialVersionUID = -3317632123352221248L;

        public StatusExceptionWrapperException(Exception cause) {
            super(cause);
        }

    }
}
