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
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.services.satellite.MemoryComputer;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.services.satellite.ServicesInstallationSorter;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;
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
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile("!test-nodes-conf")
public class NodesConfigurationServiceImpl implements NodesConfigurationService {

    private static final Logger logger = Logger.getLogger(NodesConfigurationServiceImpl.class);

    public static final String ESKIMO_TOPOLOGY_SH = "/etc/eskimo_topology.sh";

    @Autowired
    private ServicesInstallationSorter servicesInstallationSorter;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private SystemService systemService;

    @Autowired
    private MemoryComputer memoryComputer;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConnectionManagerService connectionManagerService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private KubernetesService kubernetesService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Value("${eskimo.enableKubernetesSubsystem}")
    private String enableKubernetes = "true";

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.baseInstallWaitTimoutSeconds}")
    private int baseInstallWaitTimout = 1000;

    @Value("${system.operationWaitTimoutSeconds}")
    private int operationWaitTimoutSeconds = 800; // ~ 13 minutes (for an individual step)

    @Value("${system.servicesSetupPath}")
    private String servicesSetupPath = "./services_setup";

    @Value("${system.packageDistributionPath}")
    private String packageDistributionPath = "./packages_distrib";

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void applyNodesConfig(ServiceOperationsCommand command)
            throws NodesConfigurationException {

        boolean success = false;
        try {

            logger.info ("Starting System Deployment Operations.");

            operationsMonitoringService.startCommand(command);

            NodesConfigWrapper rawNodesConfig = command.getRawConfig();
            NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

            Set<Node> liveNodes = new HashSet<>();
            Set<Node> deadNodes = new HashSet<>();

            List<Pair<String, Node>> nodeSetupPairs = systemService.discoverAliveAndDeadNodes(command.getAllNodes(), nodesConfig, liveNodes, deadNodes);
            if (nodeSetupPairs == null) {
                return;
            }

            KubernetesServicesConfigWrapper kubeServicesConfig = configurationService.loadKubernetesServicesConfig();
            ServicesInstallStatusWrapper servicesInstallStatus = configurationService.loadServicesInstallationStatus();

            MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, kubeServicesConfig, deadNodes);

            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            // Nodes setup
            systemService.performPooledOperation(command.getNodesCheckOperation(nodesConfig), parallelismInstallThreadCount, baseInstallWaitTimout,
                    (operation, error) -> {
                        Node node = operation.getNode();
                        if (nodesConfig.getAllNodes().contains(node) && liveNodes.contains(node)) {

                            systemOperationService.applySystemOperation(
                                    operation,
                                    ml -> {

                                        if (!operationsMonitoringService.isInterrupted() && error.get() == null) {
                                            operationsMonitoringService.addInfo(operation, "Checking / Installing Base system");
                                            if (isMissingOnNode("base_system", node)) {
                                                installEskimoBaseSystem(ml, node);
                                                flagInstalledOnNode("base_system", node);
                                            }
                                        }

                                        // topology
                                        if (!operationsMonitoringService.isInterrupted() && (error.get() == null)) {
                                            operationsMonitoringService.addInfo(operation, "Installing Topology and settings");
                                            installTopologyAndSettings(ml, nodesConfig, kubeServicesConfig, servicesInstallStatus, memoryModel, node);
                                        }

                                        if (StringUtils.isNotBlank(enableKubernetes) && enableKubernetes.equals("true")
                                            && !operationsMonitoringService.isInterrupted() && (error.get() == null)) {
                                                operationsMonitoringService.addInfo(operation, "Checking / Installing Kubernetes");
                                            if (isMissingOnNode("k8s", node)) {
                                                uploadKubernetes(node);
                                                ml.addInfo(installK8s(node));
                                                flagInstalledOnNode("k8s", node);
                                            }
                                        }

                                    }, null);
                        }
                    });

            // first thing first, flag services that need to be restarted as "needing to be restarted"
            for (ServiceOperationsCommand.ServiceOperationId restart :
                    command.getOperationsGroupInOrder(servicesInstallationSorter, nodesConfig).stream()
                            .flatMap(Collection::stream)
                            .filter(op -> op.getOperation().equals(ServiceOperationsCommand.ServiceOperation.RESTART))
                            .collect(Collectors.toList())) {
                try {
                    configurationService.updateAndSaveServicesInstallationStatus(servicesInstallationStatus -> {
                        Node node = restart.getNode();
                        if (restart.getNode().equals(Node.KUBERNETES_FLAG)) {
                            node = Node.KUBERNETES_NODE;
                        }
                        servicesInstallationStatus.setInstallationFlagRestart(restart.getService(), node);
                    });
                } catch (FileException | SetupException e) {
                    logger.error(e, e);
                    throw new SystemException(e);
                }
            }

            // Installation in batches (groups following dependencies)
            for (List<ServiceOperationsCommand.ServiceOperationId> operationGroup : command.getOperationsGroupInOrder(servicesInstallationSorter, nodesConfig)) {

                systemService.performPooledOperation(operationGroup, parallelismInstallThreadCount, operationWaitTimoutSeconds,
                        (operation, error) -> {
                            if (operation.getOperation().equals(ServiceOperationsCommand.ServiceOperation.INSTALLATION)) {
                                if (liveNodes.contains(operation.getNode())) {
                                    installService(operation);
                                }

                            } else if (operation.getOperation().equals(ServiceOperationsCommand.ServiceOperation.UNINSTALLATION)) {
                                if (!deadNodes.contains(operation.getNode())) {
                                    uninstallService(operation);
                                } else {
                                    if (!liveNodes.contains(operation.getNode())) {
                                        // this means that the node has been de-configured
                                        // (since if it is neither dead nor alive then it just hasn't been tested since it's not
                                        // in the config anymore)
                                        // just consider it uninstalled
                                        uninstallServiceNoOp(operation);
                                    }
                                }
                            } else { // restart
                                if (operation.getNode().equals(Node.KUBERNETES_FLAG) || liveNodes.contains(operation.getNode())) {
                                    restartServiceForSystem(operation);
                                }
                            }
                        });
            }

            if (!operationsMonitoringService.isInterrupted() && (!Collections.disjoint(deadNodes, nodesConfig.getAllNodes()))) {
                operationsMonitoringService.addGlobalInfo("At least one configured node was found dead");
                throw new NodesConfigurationException("At least one configured node was found dead");
            }

            success = true;

        } catch (SetupException | SystemException | FileException | ServiceDefinitionException e) {
            logger.error (e, e);
            throw new NodesConfigurationException(e);
        } finally {
            operationsMonitoringService.endCommand(success);
            logger.info ("System Deployment Operations Completed.");
        }
    }

    @Override
    public void installEskimoBaseSystem(MessageLogger ml, Node node) throws SSHCommandException {

        try (SSHConnection connection = connectionManagerService.getPrivateConnection(node)) {

            ml.addInfo(" - Calling install-eskimo-base-system.sh");
            ml.addInfo(sshCommandService.runSSHScriptPath(connection, servicesSetupPath + "/base-eskimo/install-eskimo-base-system.sh"));

            ml.addInfo(" - Copying jq program");
            copyCommand("jq-1.6-linux64", "/usr/local/bin/jq", connection);

            for (String script : new String[]{
                    "kube_do",
                    "eskimo-kube-exec"}) {
                ml.addInfo(" - Copying script " + script);
                copyCommand(script, "/usr/local/bin/" + script, connection);
            }

            for (String script : new String[] {
                    "gluster-mount.sh",
                    "eskimo-utils.sh",
                    "glusterMountChecker.sh",
                    "glusterMountCheckerPeriodic.sh",
                    "inContainerMountGluster.sh",
                    "settingsInjector.sh",
                    "containerWatchDog.sh",
                    "import-hosts.sh"}) {
                ml.addInfo(" - Copying script " + script);
                copyCommand(script, "/usr/local/sbin/" + script, connection);
            }

            if (StringUtils.isNotBlank(enableKubernetes) && enableKubernetes.equals("true")) {
                ml.addInfo(" - Copying eskimo-kubectl script");
                copyCommand("eskimo-kubectl", "/usr/local/bin/eskimo-kubectl", connection);
            }

        } catch (ConnectionManagerException e) {
            throw new SSHCommandException(e);
        }
    }

    private String installK8s(Node node) throws SSHCommandException {
        return sshCommandService.runSSHScriptPath(node, servicesSetupPath + "/base-eskimo/install-kubernetes.sh");
    }

    @Override
    public void copyCommand (String source, String target, SSHConnection connection) throws SSHCommandException {
        try {
            sshCommandService.copySCPFile(connection, servicesSetupPath + "/base-eskimo/" + source);
            sshCommandService.runSSHCommand(connection, new String[]{"sudo", "mv", source, target});
            sshCommandService.runSSHCommand(connection, new String[]{"sudo", "chown", "root.root", target});
            sshChmod755(connection, target);
        } catch (SSHCommandException e) {
            throw new SSHCommandException("Error during copy of " + source + " to " + target, e);
        }
    }

    private boolean isMissingOnNode(String installation, Node node) {
        try {
            String result = sshCommandService.runSSHCommand(node, "cat /etc/eskimo_flag_" + installation + "_installed");
            return StringUtils.isBlank(result) || !result.contains("OK");
        } catch (SSHCommandException e) {
            logger.debug(e.getMessage());
            return true;
        }
    }

    @Override
    public void installTopologyAndSettings(
            MessageLogger ml,
            NodesConfigWrapper nodesConfig,
            KubernetesServicesConfigWrapper kubeServicesConfig,
            ServicesInstallStatusWrapper servicesInstallStatus,
            MemoryModel memoryModel, Node node)
            throws SystemException, SSHCommandException, IOException {

        try (SSHConnection connection = connectionManagerService.getPrivateConnection(node)) {

            ml.addInfo(" - (Re-)Creating topology File");
            File tempTopologyFile = systemService.createTempFile(Service.TOPOLOGY_FLAG, ".sh");
            deleteTempFile(tempTopologyFile);
            try {
                FileUtils.writeFile(tempTopologyFile, servicesDefinition
                        .getTopology(nodesConfig, kubeServicesConfig, node)
                        .getTopologyScriptForNode(nodesConfig, kubeServicesConfig, servicesInstallStatus, servicesDefinition, memoryModel, nodesConfig.getNodeNumber (node)));
            } catch (ServiceDefinitionException | NodesConfigurationException | FileException e) {
                logger.error (e, e);
                throw new SystemException(e);
            }
            sshCommandService.copySCPFile(connection, tempTopologyFile.getAbsolutePath());
            sshCommandService.runSSHCommand(connection, new String[]{"sudo", "mv", tempTopologyFile.getName(), ESKIMO_TOPOLOGY_SH});
            sshChmod755(connection, ESKIMO_TOPOLOGY_SH);

            deleteTempFile(tempTopologyFile);

            ml.addInfo(" - (Re-)Creating settings File");
            ServicesSettingsWrapper servicesConfig = configurationService.loadServicesConfigNoLock();

            File tempServicesSettingsFile = systemService.createTempFile(Service.SERVICES_SETTINGS_FLAG, ".json");
            deleteTempFile(tempServicesSettingsFile);

            FileUtils.writeFile(tempServicesSettingsFile, servicesConfig.getFormattedValue());

            sshCommandService.copySCPFile(connection, tempServicesSettingsFile.getAbsolutePath());
            sshCommandService.runSSHCommand(connection, new String[]{"sudo", "mv", tempServicesSettingsFile.getName(), "/etc/eskimo_services-settings.json"});
            sshChmod755(connection, "/etc/eskimo_services-settings.json");

            deleteTempFile(tempServicesSettingsFile);

            ml.addInfo(" - Checking / fixing system users");
            sshCommandService.runSSHCommand(connection, new String[]{"sudo", "/usr/local/sbin/eskimo-system-checks.sh"});

        } catch (FileException | SetupException e) {
            logger.error (e, e);
            throw new SystemException(e);

        } catch (ConnectionManagerException e) {
            throw new SystemException(e);
        }
    }

    private void deleteTempFile(File tempServicesSettingsFile) throws SystemException {
        try {
            FileUtils.delete(tempServicesSettingsFile);
        } catch (FileUtils.FileDeleteFailedException e) {
            logger.error (e, e);
            throw new SystemException(e);
        }
    }

    private void flagInstalledOnNode(String installation, Node node) throws SystemException {
        try {
            sshCommandService.runSSHCommand(node, "sudo bash -c \"echo OK > /etc/eskimo_flag_" + installation + "_installed\"");
        } catch (SSHCommandException e) {
            logger.error(e, e);
            throw new SystemException(e.getMessage(), e);
        }
    }

    private void uploadKubernetes(Node node) throws SSHCommandException, SystemException {
        try (SSHConnection connection = connectionManagerService.getPrivateConnection(node)) {

            File packageDistributionDir = new File (packageDistributionPath);

            String kubeFileName = setupService.findLastPackageFile("_", "kube");
            File kubeDistrib = new File (packageDistributionDir, kubeFileName);

            sshCommandService.copySCPFile(connection, kubeDistrib.getAbsolutePath());

        } catch (ConnectionManagerException e) {
            throw new SystemException(e);
        }
    }

    @Override
    public void uninstallService(AbstractStandardOperationId<?> operationId) throws SystemException {
        systemOperationService.applySystemOperation(operationId,
                ml -> proceedWithServiceUninstallation(ml, operationId.getNode(), operationId.getService()),
                status -> status.removeInstallationFlag(operationId.getService(), operationId.getNode()));
        proxyManagerService.removeServerForService(operationId.getService(), operationId.getNode());
    }

    void uninstallServiceNoOp(AbstractStandardOperationId<?> operationId) throws SystemException {
        systemOperationService.applySystemOperation(operationId,
                builder -> {},
                status -> status.removeInstallationFlag(operationId.getService(), operationId.getNode()));
        proxyManagerService.removeServerForService(operationId.getService(), operationId.getNode());
    }

    @Override
    public void installService(AbstractStandardOperationId<?> operationId) throws SystemException {
        systemOperationService.applySystemOperation(operationId,
                ml -> proceedWithServiceInstallation(ml, operationId.getNode(), operationId.getService()),
                status -> status.setInstallationFlagOK(operationId.getService(), operationId.getNode()));
    }

    @Override
    public void restartServiceForSystem(AbstractStandardOperationId<?> operationId) throws SystemException {
        if (servicesDefinition.getServiceDefinition(operationId.getService()).isKubernetes()) {

            systemOperationService.applySystemOperation(operationId,
                    ml -> {
                        try {
                            ml.addInfo(kubernetesService.restartServiceInternal(servicesDefinition.getServiceDefinition(operationId.getService()), operationId.getNode()));
                        } catch (KubernetesException e) {
                            logger.error (e, e);
                            throw new SystemException (e);
                        }
                    },
                    status -> status.setInstallationFlagOK(operationId.getService(), Node.KUBERNETES_NODE) );

        } else {
            systemOperationService.applySystemOperation(operationId,
                    ml -> ml.addInfo(sshCommandService.runSSHCommand(operationId.getNode(),
                            "sudo bash -c 'systemctl reset-failed " + operationId.getService() + " && systemctl restart " + operationId.getService() + "'")),
                    status -> status.setInstallationFlagOK(operationId.getService(), operationId.getNode()));
        }
    }

    private void proceedWithServiceUninstallation(MessageLogger ml, Node node, Service service)
            throws SSHCommandException, SystemException {
        try (SSHConnection connection = connectionManagerService.getPrivateConnection(node)) {

            // 1. Calling uninstall.sh script if it exists
            systemService.callUninstallScript(ml, connection, service);

            // 2. Stop service
            ml.addInfo(" - Stopping Service");
            sshCommandService.runSSHCommand(connection, "sudo systemctl stop " + service);

            // 3. Uninstall systemd service file
            ml.addInfo(" - Removing systemd Service File");
            // Find systemd unit config files directory
            String foundStandardFlag = sshCommandService.runSSHScript(connection, "if [[ -d /lib/systemd/system/ ]]; then echo found_standard; fi");
            if (StringUtils.isNotBlank(foundStandardFlag) && foundStandardFlag.contains("found_standard")) {
                sshCommandService.runSSHCommand(connection, "sudo rm -f  /lib/systemd/system/" + service + ".service");
            } else {
                sshCommandService.runSSHCommand(connection, "sudo rm -f  /usr/lib/systemd/system/" + service + ".service");
            }

            // 4. Delete docker container
            ml.addInfo(" - Removing docker container");
            sshCommandService.runSSHCommand(connection, "sudo docker rm -f " + service + " || true ");

            // 5. Delete docker image
            ml.addInfo(" - Removing docker image");
            String image = servicesDefinition.getServiceDefinition(service).getImageName();
            sshCommandService.runSSHCommand(connection, "sudo bash -c '. /usr/local/sbin/eskimo-utils.sh && docker image rm -f eskimo/" + image + ":$(get_last_tag " + image + ")'");

            // 6. Reloading systemd daemon
            ml.addInfo(" - Reloading systemd daemon");
            sshCommandService.runSSHCommand(connection, "sudo systemctl daemon-reload");
            sshCommandService.runSSHCommand(connection, "sudo systemctl reset-failed");

        } catch (ConnectionManagerException e) {
            throw new SSHCommandException(e);
        }
    }

    private void proceedWithServiceInstallation(MessageLogger ml, Node node, Service service)
            throws IOException, SystemException, SSHCommandException {

        String imageName = servicesDefinition.getServiceDefinition(service).getImageName();

        try (SSHConnection connection = connectionManagerService.getPrivateConnection(node)) {

            ml.addInfo(" - Creating archive and copying it over");
            File tmpArchiveFile = systemService.createRemotePackageFolder(ml, connection, service, imageName);

            // 4. call setup script
            ml.addInfo(" - Calling setup script");
            systemService.installationSetup(ml, connection, node, service);

            // 5. cleanup
            ml.addInfo(" - Performing cleanup");
            systemService.installationCleanup(ml, connection, service, imageName, tmpArchiveFile);

        } catch (ConnectionManagerException e) {
            throw new SSHCommandException(e);
        }
    }

    private void sshChmod755 (SSHConnection connection, String file) throws SSHCommandException {
        sshChmod (connection, file, "755");
    }

    private void sshChmod (SSHConnection connection, String file, String mode) throws SSHCommandException {
        sshCommandService.runSSHCommand(connection, new String[]{"sudo", "chmod", mode, file});
    }

    @Override
    public String getNodeFlavour(SSHConnection connection) throws SSHCommandException, SystemException {
        // Find out if debian or RHEL or SUSE
        String flavour = null;
        String rawIsDebian = sshCommandService.runSSHScript(connection, "if [[ -f /etc/debian_version ]]; then echo debian; fi");
        if (rawIsDebian.contains("debian")) {
            flavour = "debian";
        }

        if (flavour == null) {
            String rawIsRedHat = sshCommandService.runSSHScript(connection, "if [[ -f /etc/redhat-release ]]; then echo redhat; fi");
            if (rawIsRedHat.contains("redhat")) {
                flavour = "redhat";
            }
        }

        if (flavour == null) {
            String rawIsSuse = sshCommandService.runSSHScript(connection, "if [[ -f /etc/SUSE-brand ]]; then echo suse; fi");
            if (rawIsSuse.contains("suse")) {
                flavour = "suse";
            }
        }

        if (flavour == null) {
            throw new SystemException ("Unknown OS flavour. None of the known OS type marker files has been found.");
        }
        return flavour;
    }

}
