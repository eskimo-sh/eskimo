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
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON, proxyMode = ScopedProxyMode.TARGET_CLASS)
@Profile("test-system")
public class SystemServiceTestImpl implements SystemService {

    private boolean returnEmptySystemStatus = false;
    private boolean returnOKSystemStatus = false;

    private boolean startServiceError = false;

    private boolean standard2NodesStatus = false;

    public void reset() {
        this.returnEmptySystemStatus = false;
        this.returnOKSystemStatus = false;
        this.startServiceError = false;
        this.standard2NodesStatus = false;
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

    @Override
    public void delegateApplyNodesConfig(ServiceOperationsCommand command) throws SystemException, NodesConfigurationException {

    }

    @Override
    public void showJournal(Service service, String node) throws SystemException {

    }

    @Override
    public void startService(Service service, String node) throws SystemException {
        if (startServiceError) {
            throw new SystemException("Test Error");
        }
    }

    @Override
    public void stopService(Service service, String node) throws SystemException {

    }

    @Override
    public void restartService(Service service, String node) throws SystemException {

    }

    @Override
    public void callCommand(String commandId, String serviceName, String node) throws SystemException {

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

    @Override
    public void handleStatusChanges(ServicesInstallStatusWrapper servicesInstallationStatus, SystemStatusWrapper systemStatus, Set<String> configuredNodesAndOtherLiveNodes) throws FileException, SetupException {

    }

    @Override
    public List<Pair<String, String>> buildDeadIps(Set<String> allNodes, NodesConfigWrapper nodesConfig, Set<String> liveIps, Set<String> deadIps) {
        return null;
    }

    @Override
    public <T> void performPooledOperation(List<T> operations, int parallelism, long operationWaitTimout, PooledOperation<T> operation) throws SystemException {

    }

    @Override
    public String sendPing(String node) throws SSHCommandException {
        return null;
    }

    @Override
    public File createTempFile(String service, String node, String extension) throws IOException {
        return null;
    }

    @Override
    public void callUninstallScript(MessageLogger ml, SSHConnection connection, String service) throws SystemException {

    }

    @Override
    public File createRemotePackageFolder(MessageLogger ml, SSHConnection connection, String node, String service, String imageName) throws SystemException, IOException, SSHCommandException {
        return null;
    }

    @Override
    public void installationSetup(MessageLogger ml, SSHConnection connection, String node, String service) throws SystemException {

    }

    @Override
    public void installationCleanup(MessageLogger ml, SSHConnection connection, String service, String imageName, File tmpArchiveFile) throws SSHCommandException, SystemException {

    }

    @Override
    public void applyServiceOperation(String service, String node, String opLabel, ServiceOperation<String> operation) throws SystemException {

    }

    @Override
    public void feedInServiceStatus(Map<String, String> statusMap, ServicesInstallStatusWrapper servicesInstallationStatus, String node, String nodeName, String referenceNodeName, String service, boolean shall, boolean installed, boolean running) throws ConnectionManagerException {

    }
}
