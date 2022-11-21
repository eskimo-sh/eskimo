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

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.services.satellite.MemoryComputer;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.utils.KubeStatusParser;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile("!test-kube")
public class KubernetesServiceImpl implements KubernetesService {

    private static final Logger logger = Logger.getLogger(KubernetesServiceImpl.class);

    public static final String KUBE_MASTER_NOT_INSTALLED = "Couldn't find service " + KUBE_MASTER + " in installation status";
    public static final String KUBE_NA_FLAG = "KUBERNETES_NA";
    public static final String KUBERNETES_NODE = "kubernetes node";

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private SystemService systemService;

    @Autowired
    private MemoryComputer memoryComputer;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private NodesConfigurationService nodesConfigurationService;

    @Autowired
    private ConnectionManagerService connectionManagerService;

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.baseInstallWaitTimoutSeconds}")
    private int baseInstallWaitTimout = 1000;

    @Value("${system.kubernetesOperationWaitTimoutSeconds}")
    private int kubernetesOperationWaitTimoutSeconds = 100 * 60; // 100 minutes

    /* For tests */
    @Deprecated
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setOperationsMonitoringService (OperationsMonitoringService operationsMonitoringService) {
        this.operationsMonitoringService = operationsMonitoringService;
    }
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }
    void setMemoryComputer(MemoryComputer memoryComputer) {
        this.memoryComputer = memoryComputer;
    }
    void setSystemOperationService(SystemOperationService systemOperationService) {
        this.systemOperationService = systemOperationService;
    }
    void setNodesConfigurationService(NodesConfigurationService nodesConfigurationService) {
        this.nodesConfigurationService = nodesConfigurationService;
    }
    void setConnectionManagerService (ConnectionManagerService connectionManagerService) {
        this.connectionManagerService = connectionManagerService;
    }
    void setProxyManagerService(ProxyManagerService proxyManagerService) {
        this.proxyManagerService = proxyManagerService;
    }
    void setSshCommandService (SSHCommandService sshCommandService) {
        this.sshCommandService = sshCommandService;
    }
    void setNodeRangeResolver (NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }

    @Override
    public void showJournal(Service service, String node) throws SystemException {
        systemService.applyServiceOperation(service.getName(), KUBERNETES_NODE, "Showing journal", () -> {
            if (service.isKubernetes()) {
                try {
                    String kubeMasterNode = configurationService.loadServicesInstallationStatus().getFirstNode(KUBE_MASTER);
                    if (StringUtils.isBlank(kubeMasterNode)) {
                        throw new KubernetesException(KUBE_MASTER_NOT_INSTALLED);
                    }
                    return sshCommandService.runSSHCommand(kubeMasterNode, "eskimo-kubectl logs " + service.getName() + " " + node);
                } catch (FileException | SetupException e) {
                    logger.error (e, e);
                    throw new KubernetesException(e);
                }
            } else {
                throw new UnsupportedOperationException("Showing service journal for " + service.getName()
                        + SystemService.SHOULD_NOT_HAPPEN_FROM_HERE);
            }
        });
    }

    @Override
    public void startService(Service service, String node) throws SystemException {
        kubeOp (service, node, "Starting", "start");
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void stopService(Service service, String node) throws SystemException {
        kubeOp (service, node, "Stopping", "stop");
    }

    private void kubeOp (Service service, String node, String opLabel, String op) throws SystemException {
        systemService.applyServiceOperation(service.getName(), KUBERNETES_NODE, opLabel, () -> {
            if (service.isKubernetes()) {
                try {
                    String kubeMasterNode = configurationService.loadServicesInstallationStatus().getFirstNode(KUBE_MASTER);
                    if (StringUtils.isBlank(kubeMasterNode)) {
                        throw new KubernetesException(KUBE_MASTER_NOT_INSTALLED);
                    }
                    return sshCommandService.runSSHCommand(kubeMasterNode, "eskimo-kubectl " + op + " " + service.getName() + " " + kubeMasterNode);
                } catch (FileException | SetupException e) {
                    logger.error (e, e);
                    throw new KubernetesException(e);
                }
            } else {
                throw new UnsupportedOperationException(opLabel + " service for " + service.getName()
                        + SystemService.SHOULD_NOT_HAPPEN_FROM_HERE);
            }
        });
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void restartService(Service service, String node) throws SystemException {
        systemService.applyServiceOperation(service.getName(), KUBERNETES_NODE, "Restart", () -> restartServiceInternal(service, node));
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public String restartServiceInternal(Service service, String node) throws KubernetesException, SSHCommandException {
        if (service.isKubernetes()) {
            if (!service.isRegistryOnly()) {
                try {
                    String kubeMasterNode = configurationService.loadServicesInstallationStatus().getFirstNode(KUBE_MASTER);
                    if (StringUtils.isBlank(kubeMasterNode)) {
                        throw new KubernetesException(KUBE_MASTER_NOT_INSTALLED);
                    }
                    return sshCommandService.runSSHCommand(kubeMasterNode, "eskimo-kubectl restart " + service.getName() + " " + kubeMasterNode);
                } catch (FileException | SetupException e) {
                    logger.error(e, e);
                    throw new KubernetesException(e);
                }
            } else {
                logger.info ("No restarting " + service.getName() + " since it's a registry only service");
                return "";
            }
        } else {
            throw new UnsupportedOperationException("Restarting service for " + service.getName()
                    + SystemService.SHOULD_NOT_HAPPEN_FROM_HERE);
        }
    }

    void restartServiceForSystem(KubernetesOperationsCommand.KubernetesOperationId operationId) throws SystemException {
        systemOperationService.applySystemOperation(operationId,
                ml -> {
                    try {
                        ml.addInfo(restartServiceInternal(servicesDefinition.getService(operationId.getService()), KUBERNETES_NODE));
                    } catch (KubernetesException e) {
                        logger.error (e, e);
                        throw new SystemException (e);
                    }
                },
                status -> status.setInstallationFlag(operationId.getService(), ServicesInstallStatusWrapper.KUBERNETES_NODE, "OK") );
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public void installService(KubernetesOperationsCommand.KubernetesOperationId operation, String kubeMasterNode)
            throws SystemException {
        systemOperationService.applySystemOperation(operation,
                logger -> proceedWithKubernetesServiceInstallation(logger, kubeMasterNode, operation.getService()),
                status -> status.setInstallationFlag(operation.getService(), ServicesInstallStatusWrapper.KUBERNETES_NODE, "OK") );
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public void uninstallService(KubernetesOperationsCommand.KubernetesOperationId operation, String kubeMasterNode) throws SystemException {
        String nodeIp = null;
        try {
            Pair<String, String> nodeNameAndStatus = this.getServiceRuntimeNode(servicesDefinition.getService(operation.getService()), kubeMasterNode);
            nodeIp = nodeNameAndStatus.getKey();
        } catch (KubernetesException e) {
            logger.warn (e.getMessage());
            logger.debug (e, e);
        }
        systemOperationService.applySystemOperation(operation,
                builder -> {
                    try {
                        proceedWithKubernetesServiceUninstallation(builder, kubeMasterNode, operation.getService());
                    } catch (KubernetesException e) {
                        logger.error (e, e);
                        throw new SystemException(e);
                    }
                },
                status -> status.removeInstallationFlag(operation.getService(), ServicesInstallStatusWrapper.KUBERNETES_NODE));
        if (nodeIp != null) {
            proxyManagerService.removeServerForService(operation.getService(), nodeIp);
        } else {
            logger.warn ("No previous IP could be found for service " + operation.getService());
        }
    }

    private void proceedWithKubernetesServiceUninstallation(MessageLogger ml, String kubeMasterNode, String serviceName)
            throws SSHCommandException, KubernetesException {
        Service service = servicesDefinition.getService(serviceName);

        SSHConnection connection = null;
        try {
            connection = connectionManagerService.getPrivateConnection(kubeMasterNode);

            sshCommandService.runSSHCommand(connection, "eskimo-kubectl uninstall " + service.getName() + " " + kubeMasterNode);

        } catch (ConnectionManagerException e) {
            throw new SSHCommandException(e);

        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Override
    public boolean shouldInstall(KubernetesServicesConfigWrapper kubeServicesConfig, String service) {
        if (kubeServicesConfig != null) {
            return kubeServicesConfig.isServiceInstallRequired(service);
        }
        return false;
    }

    @Override
    public void fetchKubernetesServicesStatus
            (Map<String, String> statusMap, ServicesInstallStatusWrapper servicesInstallationStatus)
            throws KubernetesException {

        // 3.1 Node answers
        try {

            KubernetesServicesConfigWrapper kubeServicesConfig = configurationService.loadKubernetesServicesConfig();

            String kubeMasterNode = servicesInstallationStatus.getFirstNode(KUBE_MASTER);
            if (kubeServicesConfig == null || StringUtils.isBlank(kubeMasterNode) && kubeServicesConfig.hasEnabledServices()) {
                logger.warn("Kubernetes is not installed");
            }

            // get kubectl status all at once and then below get it from Kubectl result
            KubeStatusParser parser = getKubeStatusParser();

            for (String service : servicesDefinition.listKubernetesServices()) {

                // should service be installed on kubernetes ?
                boolean shall = this.shouldInstall(kubeServicesConfig, service);

                Pair<String, String> nodeNameAndStatus = new Pair<>(KUBE_NA_FLAG, "NA");
                if (parser != null) {
                    nodeNameAndStatus = parser.getServiceRuntimeNode(servicesDefinition.getService(service), kubeMasterNode);
                }

                String nodeIp = nodeNameAndStatus.getKey();

                // if kubernetes is not answering, we assume service is still installed if it has been installed before
                // we identify it on kubernetes node then.
                if (nodeIp != null && nodeIp.equals(KUBE_NA_FLAG)) {
                    if (StringUtils.isNotBlank(servicesInstallationStatus.getFirstNode(service))) {
                        nodeIp = kubeMasterNode;
                    } else {
                        nodeIp = null;
                    }
                }

                boolean installed = StringUtils.isNotBlank(nodeIp);
                boolean running = nodeNameAndStatus.getValue().equalsIgnoreCase(STATUS_RUNNING);

                String nodeName = nodeIp != null ? nodeIp.replace(".", "-") : null;

                // if there is any kind of problem, boild down to identify service on kube master
                if (!installed || !running || servicesDefinition.getService(service).isRegistryOnly() || parser == null) {

                    // uninstalled services are identified on the kubernetes node
                    if (StringUtils.isBlank(nodeName)) {
                        if (StringUtils.isNotBlank(kubeMasterNode)) {
                            nodeName = kubeMasterNode.replace(".", "-");
                        } else {
                            nodeName = servicesInstallationStatus.getFirstNodeName(KUBE_MASTER);
                        }
                        // last attempt, get it from theoretical perspective
                        if (StringUtils.isBlank(nodeName)) {
                            nodeName = configurationService.loadNodesConfig().getFirstNodeName(KUBE_MASTER);
                        }
                    }

                    systemService.feedInServiceStatus(
                            statusMap, servicesInstallationStatus, nodeIp, nodeName,
                            ServicesInstallStatusWrapper.KUBERNETES_NODE,
                            service, shall, installed, running);
                }

                //otherwise show service running on nodes where it is running
                else {

                    List<Pair<String, String>> nodeNamesAndStatuses = parser.getServiceRuntimeNodes(service);

                    for (Pair<String, String> rtNnodeNameAndStatus : nodeNamesAndStatuses) {
                        String runtimeNodeIp = rtNnodeNameAndStatus.getKey();
                        String runtimeNodeName = runtimeNodeIp.replace(".", "-");

                        boolean runtimeRunning = rtNnodeNameAndStatus.getValue().equals(STATUS_RUNNING);

                        systemService.feedInServiceStatus(
                                statusMap, servicesInstallationStatus, runtimeNodeIp, runtimeNodeName,
                                ServicesInstallStatusWrapper.KUBERNETES_NODE,
                                service, shall, true, runtimeRunning);
                    }
                }
            }
        } catch (JSONException | ConnectionManagerException | SystemException | SetupException  e) {
            logger.error(e, e);
            throw new KubernetesException(e.getMessage(), e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void applyServicesConfig(KubernetesOperationsCommand command) throws KubernetesException {

        logger.info ("Starting Kubernetes Deployment Operations");
        boolean success = false;
        try {

            operationsMonitoringService.operationsStarted(command);

            // Find out node running Kubernetes
            ServicesInstallStatusWrapper servicesInstallStatus = configurationService.loadServicesInstallationStatus();

            String kubeMasterNode = servicesInstallStatus.getFirstNode(KUBE_MASTER);
            if (StringUtils.isBlank(kubeMasterNode)) {

                notificationService.addError("Kube Master doesn't seem to be installed");

                String message = "Kubernetes doesn't seem to be installed. Kubernetes services configuration is saved but will need to be re-applied when k8s-master is available.";

                // special case : if some Kubernetes services are getting uninstalled, and Kubernetes is nowhere installed or anything, let's force flag them as uninstalled
                try {
                    SystemStatusWrapper lastStatus = systemService.getStatus();
                    String kubeMasterNodeName = lastStatus.getFirstNodeName(KUBE_MASTER);
                    if (StringUtils.isBlank(kubeMasterNodeName)) {

                        if (!command.getUninstallations().isEmpty()) {
                            logger.warn("Uninstalled Kubernetes services will be flagged as uninstalled even though no operation can be performed in kubernetes.");
                            configurationService.updateAndSaveServicesInstallationStatus(servicesInstallationStatus -> {
                                for (KubernetesOperationsCommand.KubernetesOperationId uninstalledKubeService : command.getUninstallations()) {
                                    servicesInstallationStatus.removeInstallationFlag(uninstalledKubeService.getService(), ServicesInstallStatusWrapper.KUBERNETES_NODE);
                                }
                            });
                        }
                    }

                } catch (SystemService.StatusExceptionWrapperException e1) {
                    logger.debug (e1, e1);
                }

                throw new SystemException(message);
            }

            Set<String> liveIps = new HashSet<>();
            Set<String> deadIps = new HashSet<>();

            // handle potential interruption request
            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            NodesConfigWrapper rawNodesConfig = configurationService.loadNodesConfig();
            NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

            List<Pair<String, String>> nodesSetup = systemService.buildDeadIps(new HashSet<>(){{add(kubeMasterNode);}}, nodesConfig, liveIps, deadIps);

            if (deadIps.contains(kubeMasterNode)) {
                notificationService.addError("The Kube Master node is dead. cannot proceed any further with installation.");
                String message = "The Kube Master node is dead. cannot proceed any further with installation. Kubernetes services configuration is saved but will need to be re-applied when k8s-master is available.";
                operationsMonitoringService.addGlobalInfo(message);
                throw new KubernetesException(message);
            }

            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            if (nodesSetup == null) {
                return;
            }

            MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, command.getRawConfig(), deadIps);

            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            // Nodes re-setup (topology)
            systemOperationService.applySystemOperation(new KubernetesOperationsCommand.KubernetesOperationId("Installation", TOPOLOGY_ALL_NODES),
                    ml -> {
                        systemService.performPooledOperation (new ArrayList<String>(liveIps), parallelismInstallThreadCount, baseInstallWaitTimout,
                                (operation, error) -> {
                                    // topology
                                    if (error.get() == null) {
                                        try {
                                            nodesConfigurationService.installTopologyAndSettings(
                                                    nodesConfig, command.getRawConfig(), servicesInstallStatus, memoryModel, operation);
                                        } catch (SSHCommandException | IOException e) {
                                            logger.error (e, e);
                                            ml.addInfo(e.getMessage());
                                            throw new SystemException(e);
                                        }
                                    }
                                });
                    }, null);



            // Installation in batches (groups following dependencies) - deploying on kubernetes 1 service at a time for now
            systemService.performPooledOperation(command.getInstallations(), 1, kubernetesOperationWaitTimoutSeconds,
                    (operation, error) -> installService(operation, kubeMasterNode));

            // uninstallations - deploying on kubernetes 1 service at a time for now
            systemService.performPooledOperation(command.getUninstallations(), 1, kubernetesOperationWaitTimoutSeconds,
                    (operation, error) -> uninstallService(operation, kubeMasterNode));

            // restarts
            systemService.performPooledOperation(command.getRestarts(), 1, kubernetesOperationWaitTimoutSeconds,
                    (operation, error) -> restartServiceForSystem(operation));

            success = true;
        } catch (FileException | SetupException | SystemException | ServiceDefinitionException | NodesConfigurationException e) {
            logger.error (e, e);
            operationsMonitoringService.addGlobalInfo("Kubernetes Services installation failed ! " + e.getMessage());
            notificationService.addError("Kubernetes Services installation failed !");
            throw new KubernetesException(e);
        } finally {
            operationsMonitoringService.operationsFinished(success);
            logger.info ("Kubernetes Deployment Operations Completed.");
        }
    }

    private void proceedWithKubernetesServiceInstallation(MessageLogger ml, String kubeMasterNode, String service)
            throws IOException, SystemException, SSHCommandException {

        SSHConnection connection = null;
        try {
            connection = connectionManagerService.getPrivateConnection(kubeMasterNode);

            String imageName = servicesDefinition.getService(service).getImageName();

            ml.addInfo(" - Creating archive and copying it over to Kube Master node");
            File tmpArchiveFile = systemService.createRemotePackageFolder(ml, connection, kubeMasterNode, service, imageName);

            // 4. call setup script
            ml.addInfo(" - Calling setup script");
            systemService.installationSetup(ml, connection, kubeMasterNode, service);

            // 5. cleanup
            ml.addInfo(" - Performing cleanup");
            systemService.installationCleanup(ml, connection, service, imageName, tmpArchiveFile);

        } catch (ConnectionManagerException e) {
            throw new SSHCommandException (e);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    protected KubeStatusParser getKubeStatusParser() throws KubernetesException {

        try {
            ServicesInstallStatusWrapper servicesInstallationStatus = configurationService.loadServicesInstallationStatus();

            String kubeMasterNode = servicesInstallationStatus.getFirstNode(KUBE_MASTER);

            String ping = null;
            if (!StringUtils.isBlank(kubeMasterNode)) {

                // find out if SSH connection to host can succeeed
                try {
                    ping = systemService.sendPing(kubeMasterNode);
                } catch (SSHCommandException e) {
                    logger.warn(e.getMessage());
                    logger.debug(e, e);
                }
            }

            if (StringUtils.isBlank(ping) || !ping.startsWith("OK")) {
                return null;
            }

            String allPodStatus = sshCommandService.runSSHScript(kubeMasterNode,
                    "/usr/local/bin/kubectl get pod --all-namespaces -o wide 2>/dev/null ", false);

            String allServicesStatus = sshCommandService.runSSHScript(kubeMasterNode,
                    "/usr/local/bin/kubectl get service --all-namespaces -o wide 2>/dev/null ", false);

            String registryServices = sshCommandService.runSSHScript(kubeMasterNode,
                    "/bin/ls -1 /var/lib/kubernetes/docker_registry/docker/registry/v2/repositories/", false);

            return new KubeStatusParser(allPodStatus, allServicesStatus, registryServices, servicesDefinition);

        } catch (SSHCommandException | SetupException | FileException e) {
            logger.error (e, e);
            throw new KubernetesException(e);
        }
    }

    private Pair<String,String> getServiceRuntimeNode(Service service, String kubeIp) throws KubernetesException {

        KubeStatusParser parser = getKubeStatusParser();
        if (parser == null) {
            return new Pair<>(KUBE_NA_FLAG, "NA");
        }
        return parser.getServiceRuntimeNode (service, kubeIp);
    }
}
