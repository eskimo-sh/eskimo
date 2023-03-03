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
import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.services.satellite.MemoryComputer;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.services.satellite.ServicesInstallationSorter;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
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

    public static final String KUBE_MASTER_NOT_INSTALLED = "Couldn't find service KUBE-MASTER in installation status";

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

    @Autowired
    private ServicesInstallationSorter servicesInstallationSorter;

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.baseInstallWaitTimoutSeconds}")
    private int baseInstallWaitTimout = 1000;

    @Value("${system.kubernetesOperationWaitTimoutSeconds}")
    private int kubernetesOperationWaitTimoutSeconds = 100 * 60; // 100 minutes

    @Override
    public void showJournal(ServiceDefinition serviceDef, Node node) throws SystemException {
        systemService.applyServiceOperation(serviceDef.toService(), Node.KUBERNETES_NODE, SimpleOperationCommand.SimpleOperation.SHOW_JOURNAL, () -> {
            if (serviceDef.isKubernetes()) {
                try {
                    Node kubeMasterNode = Optional.ofNullable(
                                configurationService.loadServicesInstallationStatus().getFirstNode(servicesDefinition.getKubeMasterServiceDef()))
                            .orElseThrow(() -> new KubernetesException(KUBE_MASTER_NOT_INSTALLED));
                    return sshCommandService.runSSHCommand(kubeMasterNode, "eskimo-kubectl logs " + serviceDef.getName() + " " + node);
                } catch (FileException | SetupException e) {
                    logger.error (e, e);
                    throw new KubernetesException(e);
                }
            } else {
                throw new UnsupportedOperationException("Showing service journal for " + serviceDef.getName()
                        + SystemService.SHOULD_NOT_HAPPEN_FROM_HERE);
            }
        });
    }

    @Override
    public void startService(ServiceDefinition serviceDef, Node node) throws SystemException {
        kubeOp (serviceDef, SimpleOperationCommand.SimpleOperation.START, "start");
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void stopService(ServiceDefinition serviceDef, Node node) throws SystemException {
        kubeOp (serviceDef, SimpleOperationCommand.SimpleOperation.STOP, "stop");
    }

    private void kubeOp (ServiceDefinition serviceDef, SimpleOperationCommand.SimpleOperation simpleOp, String op) throws SystemException {
        systemService.applyServiceOperation(serviceDef.toService(), Node.KUBERNETES_NODE, simpleOp, () -> {
            if (serviceDef.isKubernetes()) {
                try {
                    Node kubeMasterNode = Optional.ofNullable(
                                configurationService.loadServicesInstallationStatus().getFirstNode(servicesDefinition.getKubeMasterServiceDef()))
                            .orElseThrow(() -> new KubernetesException(KUBE_MASTER_NOT_INSTALLED));
                    return sshCommandService.runSSHCommand(kubeMasterNode, "eskimo-kubectl " + op + " " + serviceDef.getName() + " " + kubeMasterNode);
                } catch (FileException | SetupException e) {
                    logger.error (e, e);
                    throw new KubernetesException(e);
                }
            } else {
                throw new UnsupportedOperationException(simpleOp.getLabel() + " service for " + serviceDef.getName()
                        + SystemService.SHOULD_NOT_HAPPEN_FROM_HERE);
            }
        });
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void restartService(ServiceDefinition serviceDef, Node node) throws SystemException {
        systemService.applyServiceOperation(
                serviceDef.toService(),
                Node.KUBERNETES_NODE,
                SimpleOperationCommand.SimpleOperation.RESTART,
                () -> restartServiceInternal(serviceDef, node));
    }

    @Override
    public String restartServiceInternal(ServiceDefinition serviceDef, Node node) throws KubernetesException, SSHCommandException {
        if (serviceDef.isKubernetes()) {
            if (!serviceDef.isRegistryOnly()) {
                try {
                    Node kubeMasterNode = Optional.ofNullable(
                                configurationService.loadServicesInstallationStatus().getFirstNode(servicesDefinition.getKubeMasterServiceDef()))
                            .orElseThrow(() -> new KubernetesException(KUBE_MASTER_NOT_INSTALLED));
                    return sshCommandService.runSSHCommand(kubeMasterNode, "eskimo-kubectl restart " + serviceDef.getName() + " " + kubeMasterNode);
                } catch (FileException | SetupException e) {
                    logger.error(e, e);
                    throw new KubernetesException(e);
                }
            } else {
                logger.info ("No restarting " + serviceDef.getName() + " since it's a registry only service");
                return "";
            }
        } else {
            throw new UnsupportedOperationException("Restarting service for " + serviceDef.getName()
                    + SystemService.SHOULD_NOT_HAPPEN_FROM_HERE);
        }
    }

    void restartServiceForSystem(KubernetesOperationsCommand.KubernetesOperationId operationId) throws SystemException {
        systemOperationService.applySystemOperation(operationId,
                ml -> {
                    try {
                        ml.addInfo(restartServiceInternal(servicesDefinition.getServiceDefinition(operationId.getService()), Node.KUBERNETES_NODE));
                    } catch (KubernetesException e) {
                        logger.error (e, e);
                        throw new SystemException (e);
                    }
                },
                status -> status.setInstallationFlagOK(operationId.getService(), Node.KUBERNETES_NODE) );
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public void installService(KubernetesOperationsCommand.KubernetesOperationId operationId, Node kubeMasterNode)
            throws SystemException {
        systemOperationService.applySystemOperation(operationId,
                ml -> proceedWithKubernetesServiceInstallation(ml, kubeMasterNode, operationId.getService()),
                status -> status.setInstallationFlagOK(operationId.getService(), Node.KUBERNETES_NODE) );
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    public void uninstallService(KubernetesOperationsCommand.KubernetesOperationId operationId, Node kubeMasterNode) throws SystemException {
        Node kubeNode = null;
        try {
            Pair<Node, KubeStatusParser.KubernetesServiceStatus> nodeNameAndStatus =
                    this.getServiceRuntimeNode(servicesDefinition.getServiceDefinition(operationId.getService()), kubeMasterNode);
            kubeNode = nodeNameAndStatus.getKey();
        } catch (KubernetesException e) {
            logger.warn (e.getMessage());
            logger.debug (e, e);
        }
        systemOperationService.applySystemOperation(operationId,
                ml -> {
                    systemService.runPreUninstallHooks (ml, operationId);
                    proceedWithKubernetesServiceUninstallation(ml, kubeMasterNode, operationId.getService());
                },
                status -> status.removeInstallationFlag(operationId.getService(), Node.KUBERNETES_NODE));
        if (kubeNode != null) {
            proxyManagerService.removeServerForService(operationId.getService(), kubeNode);
        } else {
            logger.warn ("No previous IP could be found for service " + operationId.getService());
        }
    }

    private void proceedWithKubernetesServiceUninstallation(MessageLogger ml, Node kubeMasterNode, Service service)
            throws SSHCommandException {
        try (SSHConnection connection = connectionManagerService.getPrivateConnection(kubeMasterNode)){
            ml.addInfo(sshCommandService.runSSHCommand(connection, "eskimo-kubectl uninstall " + service + " " + kubeMasterNode));
        } catch (ConnectionManagerException e) {
            throw new SSHCommandException(e);
        }
    }

    @Override
    public boolean shouldInstall(KubernetesServicesConfigWrapper kubeServicesConfig, Service service) {
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

            Node kubeMasterNode = servicesInstallationStatus.getFirstNode(servicesDefinition.getKubeMasterServiceDef());
            if (kubeServicesConfig == null || kubeMasterNode == null && kubeServicesConfig.hasEnabledServices()) {
                logger.warn("Kubernetes is not installed");
            }

            // get kubectl status all at once and then below get it from Kubectl result
            KubeStatusParser parser = KubeStatusParser.getKubeStatusParser(
                    kubeMasterNode, servicesDefinition, systemService, sshCommandService);

            for (Service service : servicesDefinition.listKubernetesServices()) {

                // should service be installed on kubernetes ?
                boolean shall = this.shouldInstall(kubeServicesConfig, service);

                Pair<Node, KubeStatusParser.KubernetesServiceStatus> nodeNameAndStatus = new Pair<>(Node.KUBE_NA_FLAG, KubeStatusParser.KubernetesServiceStatus.NA);
                if (parser != null) {
                    nodeNameAndStatus = parser.getServiceRuntimeNode(servicesDefinition.getServiceDefinition(service), kubeMasterNode);
                }

                Node serviceRuntimeNode = nodeNameAndStatus.getKey();

                // if kubernetes is not answering, we assume service is still installed if it has been installed before
                // we identify it on kubernetes node then.
                if (serviceRuntimeNode != null && serviceRuntimeNode.equals(Node.KUBE_NA_FLAG)) {
                    if (servicesInstallationStatus.getFirstNode(service) != null) {
                        serviceRuntimeNode = kubeMasterNode;
                    } else {
                        serviceRuntimeNode = null;
                    }
                }

                boolean installed = serviceRuntimeNode != null;
                boolean running = nodeNameAndStatus.getValue().equals(KubeStatusParser.KubernetesServiceStatus.RUNNING);

                // if there is any kind of problem, boild down to identify service on kube master
                if (!installed || !running || servicesDefinition.getServiceDefinition(service).isRegistryOnly() || parser == null) {

                    // uninstalled services are identified on the kubernetes node
                    if (serviceRuntimeNode == null) {
                        if (kubeMasterNode != null) {
                            serviceRuntimeNode = kubeMasterNode;
                        } else {
                            serviceRuntimeNode = servicesInstallationStatus.getFirstNode(servicesDefinition.getKubeMasterServiceDef());
                        }
                        // last attempt, get it from where should theoretically be the kube master
                        if (serviceRuntimeNode == null) {
                            serviceRuntimeNode = configurationService.loadNodesConfig().getFirstNode(servicesDefinition.getKubeMasterServiceDef());
                        }
                    }

                    systemService.feedInServiceStatus(
                            statusMap, servicesInstallationStatus, serviceRuntimeNode,
                            Node.KUBERNETES_NODE,
                            service, shall, installed, running);
                }

                //otherwise show service running on nodes where it is running
                else {

                    List<Pair<Node, KubeStatusParser.KubernetesServiceStatus>> nodeNamesAndStatuses = parser.getServiceRuntimeNodes(service);

                    for (Pair<Node, KubeStatusParser.KubernetesServiceStatus> rtNnodeNameAndStatus : nodeNamesAndStatuses) {
                        Node runtimeNode = rtNnodeNameAndStatus.getKey();

                        boolean runtimeRunning = rtNnodeNameAndStatus.getValue().equals(KubeStatusParser.KubernetesServiceStatus.RUNNING);

                        systemService.feedInServiceStatus(
                                statusMap, servicesInstallationStatus, runtimeNode,
                                Node.KUBERNETES_NODE,
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

            operationsMonitoringService.startCommand(command);

            // Find out node running Kubernetes
            ServicesInstallStatusWrapper servicesInstallStatus = configurationService.loadServicesInstallationStatus();

            Node kubeMasterNode = servicesInstallStatus.getFirstNode(servicesDefinition.getKubeMasterServiceDef());
            if (kubeMasterNode == null) {

                notificationService.addError("Kube Master doesn't seem to be installed");

                String message = "Kubernetes doesn't seem to be installed. Kubernetes services configuration is saved but will need to be re-applied when k8s-master is available.";

                // special case : if some Kubernetes services are getting uninstalled, and Kubernetes is nowhere installed or anything, let's force flag them as uninstalled
                try {
                    SystemStatusWrapper lastStatus = systemService.getStatus();
                    Node lastKubeMasterNode = lastStatus.getFirstNode(servicesDefinition.getKubeMasterServiceDef());
                    if (lastKubeMasterNode == null && !command.getUninstallations().isEmpty()) {
                        logger.warn("Uninstalled Kubernetes services will be flagged as uninstalled even though no operation can be performed in kubernetes.");
                        configurationService.updateAndSaveServicesInstallationStatus(servicesInstallationStatus -> {
                            for (KubernetesOperationsCommand.KubernetesOperationId uninstalledKubeService : command.getUninstallations()) {
                                servicesInstallationStatus.removeInstallationFlag(uninstalledKubeService.getService(), Node.KUBERNETES_NODE);
                            }
                        });
                    }

                } catch (SystemService.StatusExceptionWrapperException e1) {
                    logger.debug (e1, e1);
                }

                throw new SystemException(message);
            }

            // handle potential interruption request
            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            NodesConfigWrapper rawNodesConfig = configurationService.loadNodesConfig();
            NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

            NodesStatus nodesStatus = systemService.discoverAliveAndDeadNodes(new HashSet<>(){{add(kubeMasterNode);}}, nodesConfig);
            if (nodesStatus == null) {
                return;
            }

            if (nodesStatus.isNodeDead(kubeMasterNode)) {
                notificationService.addError("The Kube Master node is dead. cannot proceed any further with installation.");
                String message = "The Kube Master node is dead. cannot proceed any further with installation. Kubernetes services configuration is saved but will need to be re-applied when k8s-master is available.";
                operationsMonitoringService.addGlobalInfo(message);
                throw new KubernetesException(message);
            }

            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, command.getRawConfig(), nodesStatus.getDeadNodes());

            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            // Nodes re-setup (topology)
            systemOperationService.applySystemOperation(new KubernetesOperationsCommand.KubernetesOperationId(
                    KubernetesOperationsCommand.KuberneteOperation.INSTALLATION, Service.TOPOLOGY_ALL_NODES),
                    ml -> systemService.performPooledOperation (new ArrayList<>(nodesStatus.getLiveNodes()), parallelismInstallThreadCount, baseInstallWaitTimout,
                            (operation, error) -> {
                                // topology
                                if (error.get() == null) {
                                    try {
                                        nodesConfigurationService.installTopologyAndSettings(
                                                ml, nodesConfig, command.getRawConfig(), servicesInstallStatus, memoryModel, operation);
                                    } catch (SSHCommandException | IOException e) {
                                        logger.error (e, e);
                                        ml.addInfo(e.getMessage());
                                        throw new SystemException(e);
                                    }
                                }
                            }), null);


            // Installation in batches (groups following dependencies)
            for (List<KubernetesOperationsCommand.KubernetesOperationId> operationGroup : command.getOperationsGroupInOrder(servicesInstallationSorter, nodesConfig)) {

                systemService.performPooledOperation(operationGroup, 1, kubernetesOperationWaitTimoutSeconds,
                        (operation, error) -> {
                            if (operation.getOperation().equals(KubernetesOperationsCommand.KuberneteOperation.INSTALLATION)) {
                                installService(operation, kubeMasterNode);

                            } else if (operation.getOperation().equals(KubernetesOperationsCommand.KuberneteOperation.UNINSTALLATION)) {
                                uninstallService(operation, kubeMasterNode);

                            } else { // restart
                                restartServiceForSystem(operation);
                            }
                        });
            }

            success = true;

        } catch (FileException | SetupException | SystemException | ServiceDefinitionException | NodesConfigurationException e) {
            logger.error (e, e);
            operationsMonitoringService.addGlobalInfo("Kubernetes Services installation failed ! " + e.getMessage());
            notificationService.addError("Kubernetes Services installation failed !");
            throw new KubernetesException(e);

        } finally {
            operationsMonitoringService.endCommand(success);
            logger.info ("Kubernetes Deployment Operations Completed.");
        }
    }

    private void proceedWithKubernetesServiceInstallation(MessageLogger ml, Node kubeMasterNode, Service service)
            throws IOException, SystemException, SSHCommandException {

        try (SSHConnection connection = connectionManagerService.getPrivateConnection(kubeMasterNode)){

            String imageName = servicesDefinition.getServiceDefinition(service).getImageName();

            ml.addInfo(" - Creating archive and copying it over to Kube Master node");
            File tmpArchiveFile = systemService.createRemotePackageFolder(ml, connection, service, imageName);

            // 4. call setup script
            ml.addInfo(" - Calling setup script");
            systemService.installationSetup(ml, connection, kubeMasterNode, service);

            // 5. cleanup
            ml.addInfo(" - Performing cleanup");
            systemService.installationCleanup(ml, connection, service, imageName, tmpArchiveFile);

        } catch (ConnectionManagerException e) {
            throw new SSHCommandException (e);
        }
    }

    private Pair<Node,KubeStatusParser.KubernetesServiceStatus> getServiceRuntimeNode(ServiceDefinition serviceDef, Node kubeMasterNode) throws KubernetesException {
        return Optional.ofNullable(KubeStatusParser.getKubeStatusParser(kubeMasterNode, servicesDefinition, systemService, sshCommandService))
                .map (parser -> parser.getServiceRuntimeNode (serviceDef, kubeMasterNode))
                .orElse(new Pair<>(Node.KUBE_NA_FLAG, KubeStatusParser.KubernetesServiceStatus.NA));
    }
}
