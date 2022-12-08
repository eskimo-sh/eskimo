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


package ch.niceideas.eskimo.test.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.test.StandardSetupHelpers;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-system")
public class SystemServiceTestImpl implements SystemService {

    private boolean returnEmptySystemStatus = false;
    private boolean returnOKSystemStatus = false;

    private boolean startServiceError = false;

    private boolean standard2NodesStatus = false;

    private boolean pingError = false;

    private boolean mockCalls = false;

    public void setMockCalls (boolean mockCalls) {
        this.mockCalls = mockCalls;
    }

    private final List<String> executedActions = new ArrayList<>();

    public void reset() {
        this.returnEmptySystemStatus = false;
        this.returnOKSystemStatus = false;
        this.startServiceError = false;
        this.standard2NodesStatus = false;
        this.pingError = false;
        this.executedActions.clear();
    }

    public List<String> getExecutedActions() {
        return Collections.unmodifiableList(executedActions);
    }

    public void setPingError() {
        this.pingError = true;
    }

    public void setReturnEmptySystemStatus() {
        this.returnEmptySystemStatus = true;
    }

    public void setReturnOKSystemStatus() {
        this.returnOKSystemStatus = true;
    }

    public void setStartServiceError() {
        this.startServiceError = true;
    }

    public void setStandard2NodesStatus() {
        this.standard2NodesStatus = true;
    }

    private List<Pair<? extends Serializable, PooledOperation<? extends Serializable>>> appliedOperations = new ArrayList<>();

    @Override
    public void delegateApplyNodesConfig(ServiceOperationsCommand command) throws SystemException, NodesConfigurationException {

    }

    @Override
    public void showJournal(Service service, String node) throws SystemException {
        executedActions.add ("Show Journal - " + service + " - " + node);
    }

    @Override
    public void startService(Service service, String node) throws SystemException {
        executedActions.add ("Start service - " + service + " - " + node);
        if (startServiceError) {
            throw new SystemException("Test Error");
        }
    }

    @Override
    public void stopService(Service service, String node) throws SystemException {
        executedActions.add ("Stop service - " + service + " - " + node);
    }

    @Override
    public void restartService(Service service, String node) throws SystemException {
        executedActions.add ("restart service - " + service + " - " + node);
    }

    @Override
    public void callCommand(String commandId, String serviceName, String node) throws SystemException {
        executedActions.add ("Call command  - " + commandId + " - " + serviceName + " - " + node);
    }

    @Override
    public SystemStatusWrapper getStatus() throws StatusExceptionWrapperException {
        if (returnEmptySystemStatus) {
            return SystemStatusWrapper.empty();
        } else  if (returnOKSystemStatus) {
            return new SystemStatusWrapper("{\"status\":\"OK\"}");
        } else if (standard2NodesStatus) {
            return StandardSetupHelpers.getStandard2NodesSystemStatus();
        }
        return null;
    }

    @Override
    public void updateStatus() {

    }

    public List<Pair<? extends Serializable, PooledOperation<? extends Serializable>>> getAppliedOperations() {
        return Collections.unmodifiableList(appliedOperations);
    }

    @Override
    public void handleStatusChanges(ServicesInstallStatusWrapper servicesInstallationStatus, SystemStatusWrapper systemStatus, Set<String> configuredNodesAndOtherLiveNodes) throws FileException, SetupException {

    }

    @Override
    public List<Pair<String, String>> buildDeadIps(Set<String> allNodes, NodesConfigWrapper nodesConfig, Set<String> liveIps, Set<String> deadIps) {
        liveIps.addAll(nodesConfig.getNodeAddresses());

        List<Pair<String, String>> nodesSetup = new ArrayList<>();

        // Find out about dead IPs
        Set<String> nodesToTest = new HashSet<>(allNodes);
        nodesToTest.addAll(nodesConfig.getNodeAddresses());
        for (String node : nodesToTest) {

            nodesSetup.add(new Pair<>("node_setup", node));
        }

        return nodesSetup;
    }

    @Override
    public <T extends Serializable> void performPooledOperation(List<T> operations, int parallelism, long operationWaitTimout, PooledOperation<T> operation) throws SystemException {
        operations.forEach(op ->  {
            appliedOperations.add(new Pair<>(op, operation));
            if (!this.mockCalls) {
                try {
                    operation.call(op, new AtomicReference<>());
                } catch (SystemException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public String sendPing(String node) throws SSHCommandException {
        if (pingError) {
            throw new SSHCommandException("Node dead");
        }
        return "OK";
    }

    @Override
    public File createTempFile(String service, String node, String extension) throws IOException {
        return File.createTempFile(service, extension);
    }

    @Override
    public void callUninstallScript(MessageLogger ml, SSHConnection connection, String service) throws SystemException {
        executedActions.add ("call Uninstall script  - " + service + " - " + connection.getHostname());
    }

    @Override
    public File createRemotePackageFolder(MessageLogger ml, SSHConnection connection, String node, String service, String imageName) throws SystemException, IOException, SSHCommandException {
        return null;
    }

    @Override
    public void installationSetup(MessageLogger ml, SSHConnection connection, String node, String service) throws SystemException {
        executedActions.add ("Installation setup  - " + service + " - " + node + " - " + connection.getHostname());
    }

    @Override
    public void installationCleanup(MessageLogger ml, SSHConnection connection, String service, String imageName, File tmpArchiveFile) throws SSHCommandException, SystemException {
        executedActions.add ("Installation cleanup  - " + service + " - " + imageName + " - " + connection.getHostname());
    }

    @Override
    public void applyServiceOperation(String service, String node, String opLabel, ServiceOperation<String> operation) throws SystemException {
        executedActions.add ("Apply service operation  - " + service + " - " + node + " - " + opLabel);
    }

    @Override
    public void feedInServiceStatus(Map<String, String> statusMap, ServicesInstallStatusWrapper servicesInstallationStatus, String node, String nodeName, String referenceNodeName, String service, boolean shall, boolean installed, boolean running) throws ConnectionManagerException {
        if (StringUtils.isBlank(nodeName)) {
            throw new IllegalArgumentException("nodeName can't be null");
        }
        if (StringUtils.isBlank(service)) {
            throw new IllegalArgumentException("service can't be null");
        }

        if (shall) {
            if (!installed) {

                statusMap.put(SystemStatusWrapper.buildStatusFlag(service, nodeName), "NA");

            } else {

                // check if services is running ?
                // check if service running using SSH

                if (!running) {
                    statusMap.put(SystemStatusWrapper.buildStatusFlag (service, nodeName), "KO");

                } else {

                    if (servicesInstallationStatus.isServiceOK (service, referenceNodeName)) {
                        statusMap.put(SystemStatusWrapper.buildStatusFlag (service, nodeName), "OK");
                    } else {
                        statusMap.put(SystemStatusWrapper.buildStatusFlag (service, nodeName), "restart");
                    }
                }
            }
        } else {
            if (installed) {
                statusMap.put(SystemStatusWrapper.buildStatusFlag (service, nodeName), "TD"); // To Be Deleted
            }
        }
    }
}
