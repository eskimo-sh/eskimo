package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class NodesConfigurationService {

    private static final Logger logger = Logger.getLogger(NodesConfigurationService.class);

    public static final String USR_LOCAL_BIN_JQ = "/usr/local/bin/jq";
    public static final String USR_LOCAL_BIN_MESOS_CLI_SH = "/usr/local/bin/mesos-cli.sh";
    public static final String USR_LOCAL_SBIN_GLUSTER_MOUNT_SH = "/usr/local/sbin/gluster_mount.sh";

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
    private MessagingService messagingService;

    @Autowired
    private ServicesSettingsService servicesSettingsService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConnectionManagerService connectionManagerService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private MarathonService marathonService;


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

    /* For tests */
    void setServicesInstallationSorter (ServicesInstallationSorter servicesInstallationSorter) {
        this.servicesInstallationSorter = servicesInstallationSorter;
    }
    void setNodeRangeResolver (NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }
    void setMemoryComputer (MemoryComputer memoryComputer) {
        this.memoryComputer = memoryComputer;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setSystemOperationService(SystemOperationService systemOperationService) {
        this.systemOperationService = systemOperationService;
    }
    void setSshCommandService(SSHCommandService sshCommandService) {
        this.sshCommandService = sshCommandService;
    }
    void setMessagingService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }
    void setServicesSettingsService(ServicesSettingsService servicesSettingsService) {
        this.servicesSettingsService = servicesSettingsService;
    }
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setSetupService(SetupService setupService) {
        this.setupService = setupService;
    }
    void setProxyManagerService(ProxyManagerService proxyManagerService) {
        this.proxyManagerService = proxyManagerService;
    }
    void setMarathonService (MarathonService marathonService) {
        this.marathonService = marathonService;
    }
    void setSystemService (SystemService systemService) {
        this.systemService = systemService;
    }
    void setConnectionManagerService (ConnectionManagerService connectionManagerService) {
        this.connectionManagerService = connectionManagerService;
    }

    public void applyNodesConfig(OperationsCommand command)
            throws SystemException, ServiceDefinitionException, NodesConfigurationException {

        logger.info ("Starting System Deployment Operations.");
        boolean success = false;
        systemService.setProcessingPending();
        try {

            NodesConfigWrapper rawNodesConfig = command.getRawConfig();
            NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

            Set<String> liveIps = new HashSet<>();
            Set<String> deadIps = new HashSet<>();

            List<Pair<String, String>> nodesSetup = systemService.buildDeadIps(command.getAllIpAddresses(), nodesConfig, liveIps, deadIps);
            if (nodesSetup == null) {
                return;
            }

            MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, deadIps);

            if (systemService.isInterrupted()) {
                return;
            }

            MarathonServicesConfigWrapper marathonServicesConfig = configurationService.loadMarathonServicesConfig();

            // Nodes setup
            systemService.performPooledOperation (nodesSetup, parallelismInstallThreadCount, baseInstallWaitTimout,
                    (operation, error) -> {
                        String ipAddress = operation.getValue();
                        if (nodesConfig.getIpAddresses().contains(ipAddress) && liveIps.contains(ipAddress)) {

                            if (!systemService.isInterrupted() && (error.get() == null && !isInstalledOnNode("base_system", ipAddress))) {
                                systemOperationService.applySystemOperation("Installation of Base System on " + ipAddress,
                                        builder -> installEskimoBaseSystem(builder, ipAddress), null);

                                flagInstalledOnNode("base_system", ipAddress);
                            }

                            // topology
                            if (!systemService.isInterrupted() && (error.get() == null)) {
                                systemOperationService.applySystemOperation("Installation of Topology and settings on " + ipAddress,
                                        builder -> installTopologyAndSettings(nodesConfig, marathonServicesConfig, memoryModel, ipAddress), null);
                            }

                            if (!systemService.isInterrupted() && (error.get() == null && !isInstalledOnNode("mesos", ipAddress))) {
                                systemOperationService.applySystemOperation("Installation of Mesos on " + ipAddress,
                                        builder -> {
                                            uploadMesos(ipAddress);
                                            builder.append (installMesos(ipAddress));
                                        }, null);

                                flagInstalledOnNode("mesos", ipAddress);
                            }
                        }
                    });

            // first thing first, flag services that need to be restarted as "needing to be restarted"
            for (List<Pair<String, String>> restarts : servicesInstallationSorter.orderOperations (
                    command.getRestarts(), nodesConfig)) {
                for (Pair<String, String> operation : restarts) {
                    try {
                        configurationService.updateAndSaveServicesInstallationStatus(servicesInstallationStatus -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            String nodeName = ipAddress.replace(".", "-");
                            if (ipAddress.equals(OperationsCommand.MARATHON_FLAG)) {
                                nodeName = ServicesInstallStatusWrapper.MARATHON_NODE;
                            }
                            servicesInstallationStatus.setInstallationFlag(service, nodeName, "restart");
                        });
                    } catch (FileException | SetupException e) {
                        logger.error (e, e);
                        throw new SystemException(e);
                    }
                }
            }

            // Installation in batches (groups following dependencies)
            for (List<Pair<String, String>> installations : servicesInstallationSorter.orderOperations (
                    command.getInstallations(), nodesConfig)) {

                systemService.performPooledOperation (installations, parallelismInstallThreadCount, operationWaitTimoutSeconds,
                        (operation, error) -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            if (liveIps.contains(ipAddress)) {
                                installService(service, ipAddress);
                            }
                        });
            }

            // uninstallations
            List<List<Pair<String, String>>> orderedUninstallations =  servicesInstallationSorter.orderOperations (
                    command.getUninstallations(), nodesConfig);
            Collections.reverse(orderedUninstallations);

            for (List<Pair<String, String>> uninstallations : orderedUninstallations) {
                systemService.performPooledOperation(uninstallations, parallelismInstallThreadCount, operationWaitTimoutSeconds,
                        (operation, error) -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            if (!deadIps.contains(ipAddress)) {
                                uninstallService(service, ipAddress);
                            } else {
                                if (!liveIps.contains(ipAddress)) {
                                    // this means that the node has been de-configured
                                    // (since if it is neither dead nor alive then it just hasn't been tested since it's not
                                    // in the config anymore)
                                    // just consider it uninstalled
                                    uninstallServiceNoOp(service, ipAddress);
                                }
                            }
                        });
            }

            // restarts
            for (List<Pair<String, String>> restarts : servicesInstallationSorter.orderOperations (
                    command.getRestarts(), nodesConfig)) {
                systemService.performPooledOperation(restarts, parallelismInstallThreadCount, operationWaitTimoutSeconds,
                        (operation, error) -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            if (ipAddress.equals(OperationsCommand.MARATHON_FLAG) || liveIps.contains(ipAddress)) {
                                restartServiceForSystem(service, ipAddress);
                            }
                        });
            }

            if (!systemService.isInterrupted() && (!Collections.disjoint(deadIps, nodesConfig.getIpAddresses()))) {
                throw new SystemException("At least one configured node was found dead");
            }

            success = true;
        } finally {
            systemService.setLastOperationSuccess (success);
            systemService.releaseProcessingPending();
            logger.info ("System Deployment Operations Completed.");
        }
    }

    void installEskimoBaseSystem(StringBuilder sb, String ipAddress) throws SSHCommandException {
        sb.append (sshCommandService.runSSHScriptPath(ipAddress, servicesSetupPath + "/base-eskimo/install-eskimo-base-system.sh"));

        sb.append(" - Copying jq program\n");
        copyCommand ("jq-1.6-linux64", USR_LOCAL_BIN_JQ, ipAddress);

        sb.append(" - Copying mesos-cli script\n");
        copyCommand ("mesos-cli.sh", USR_LOCAL_BIN_MESOS_CLI_SH, ipAddress);

        sb.append(" - Copying gluster-mount script\n");
        copyCommand ("gluster_mount.sh", USR_LOCAL_SBIN_GLUSTER_MOUNT_SH, ipAddress);

        connectionManagerService.forceRecreateConnection(ipAddress); // user privileges may have changed
    }

    private String installMesos(String ipAddress) throws SSHCommandException {
        return sshCommandService.runSSHScriptPath(ipAddress, servicesSetupPath + "/base-eskimo/install-mesos.sh");
    }

    void copyCommand (String source, String target, String ipAddress) throws SSHCommandException {
        sshCommandService.copySCPFile(ipAddress, servicesSetupPath + "/base-eskimo/" + source);
        sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "mv", source, target});
        sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "chown", "root.root", target});
        sshChmod755(ipAddress, target);
    }

    private boolean isInstalledOnNode(String installation, String ipAddress) {

        try {
            messagingService.addLine("\nChecking " + installation + " on node " + ipAddress);
            String result = sshCommandService.runSSHCommand(ipAddress, "cat /etc/eskimo_flag_" + installation + "_installed");
            return result.contains("OK");
        } catch (SSHCommandException e) {
            logger.debug(e, e);
            return false;
        }
    }

    String installTopologyAndSettings(NodesConfigWrapper nodesConfig, MarathonServicesConfigWrapper marathonConfig, MemoryModel memoryModel, String ipAddress)
            throws SystemException, SSHCommandException, IOException {

        File tempTopologyFile = systemService.createTempFile("eskimo_topology", ipAddress, ".sh");
        try {
            FileUtils.delete(tempTopologyFile);
        } catch (FileUtils.FileDeleteFailedException e) {
            logger.error (e, e);
            throw new SystemException(e);
        }
        try {
            FileUtils.writeFile(tempTopologyFile, servicesDefinition
                    .getTopology(nodesConfig, marathonConfig, ipAddress)
                    .getTopologyScriptForNode(nodesConfig, memoryModel, nodesConfig.getNodeNumber (ipAddress)));
        } catch (ServiceDefinitionException | NodesConfigurationException | FileException e) {
            logger.error (e, e);
            throw new SystemException(e);
        }
        sshCommandService.copySCPFile(ipAddress, tempTopologyFile.getAbsolutePath());
        sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "mv", tempTopologyFile.getName(), "/etc/eskimo_topology.sh"});
        sshChmod755(ipAddress, "/etc/eskimo_topology.sh");

        try {
            ServicesSettingsWrapper servicesConfig = configurationService.loadServicesConfigNoLock();

            File tempServicesSettingsFile = systemService.createTempFile("eskimo_services-settings", ipAddress, ".json");
            try {
                FileUtils.delete(tempServicesSettingsFile);
            } catch (FileUtils.FileDeleteFailedException e) {
                logger.error (e, e);
                throw new SystemException(e);
            }

            FileUtils.writeFile(tempServicesSettingsFile, servicesConfig.getFormattedValue());

            sshCommandService.copySCPFile(ipAddress, tempServicesSettingsFile.getAbsolutePath());
            sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "mv", tempServicesSettingsFile.getName(), "/etc/eskimo_services-settings.json"});
            sshChmod755(ipAddress, "/etc/eskimo_services-settings.json");


        } catch (FileException | SetupException e) {
            logger.error (e, e);
            throw new SystemException(e);
        }

        return null;
    }

    private void flagInstalledOnNode(String installation, String ipAddress) throws SystemException {
        try {
            sshCommandService.runSSHCommand(ipAddress, "sudo bash -c \"echo OK > /etc/eskimo_flag_" + installation + "_installed\"");
        } catch (SSHCommandException e) {
            logger.error(e, e);
            throw new SystemException(e.getMessage(), e);
        }
    }

    private void uploadMesos(String ipAddress) throws SSHCommandException, SystemException {

        messagingService.addLines(" - Uploading mesos distribution");
        String mesosFlavour = "mesos-" + getNodeFlavour(ipAddress);

        File packageDistributionDir = new File (packageDistributionPath);

        String mesosFileName = setupService.findLastPackageFile("_", mesosFlavour);
        File mesosDistrib = new File (packageDistributionDir, mesosFileName);

        sshCommandService.copySCPFile(ipAddress, mesosDistrib.getAbsolutePath());
    }

    void uninstallService(String service, String ipAddress) throws SystemException {
        String nodeName = ipAddress.replace(".", "-");
        systemOperationService.applySystemOperation("Uninstallation of " + service + " on " + ipAddress,
                builder -> proceedWithServiceUninstallation(builder, ipAddress, service),
                status -> status.removeInstallationFlag(service, nodeName));
        proxyManagerService.removeServerForService(service, ipAddress);
    }

    void uninstallServiceNoOp(String service, String ipAddress) throws SystemException {
        String nodeName = ipAddress.replace(".", "-");
        systemOperationService.applySystemOperation("Uninstallation of " + service + " on " + ipAddress,
                builder -> {},
                status -> status.removeInstallationFlag(service, nodeName));
        proxyManagerService.removeServerForService(service, ipAddress);
    }

    void installService(String service, String ipAddress)
            throws SystemException {
        String nodeName = ipAddress.replace(".", "-");
        systemOperationService.applySystemOperation("installation of " + service + " on " + ipAddress,
                builder -> proceedWithServiceInstallation(builder, ipAddress, service),
                status -> status.setInstallationFlag(service, nodeName, "OK"));
    }

    void restartServiceForSystem(String service, String ipAddress) throws SystemException {
        String nodeName = ipAddress.replace(".", "-");

        if (servicesDefinition.getService(service).isMarathon()) {

            systemOperationService.applySystemOperation("Restart of " + service + " on marathon node ",
                    builder -> {
                        try {
                            builder.append(marathonService.restartServiceMarathonInternal(servicesDefinition.getService(service)));
                        } catch (MarathonException e) {
                            logger.error (e, e);
                            throw new SystemException (e);
                        }
                    },
                    status -> status.setInstallationFlag(service, ServicesInstallStatusWrapper.MARATHON_NODE, "OK") );

        } else {
            systemOperationService.applySystemOperation("Restart of " + service + " on " + ipAddress,
                    builder -> builder.append(sshCommandService.runSSHCommand(ipAddress, "sudo systemctl restart " + service)),
                    status -> status.setInstallationFlag(service, nodeName, "OK"));
        }
    }


    private String proceedWithServiceUninstallation(StringBuilder sb, String ipAddress, String service)
            throws SSHCommandException, SystemException {

        // 1. Calling uninstall.sh script if it exists
        File containerFolder = new File(servicesSetupPath + "/" + service);
        if (!containerFolder.exists()) {
            throw new SystemException("Folder " + servicesSetupPath + "/" + service + " doesn't exist !");
        }

        try {
            File uninstallScriptFile = new File(containerFolder, "uninstall.sh");
            if (uninstallScriptFile.exists()) {
                sb.append(" - Calling uninstall script\n");

                sb.append(sshCommandService.runSSHScriptPath(ipAddress, uninstallScriptFile.getAbsolutePath()));
            }
        } catch (SSHCommandException e) {
            logger.warn (e, e);
            sb.append (e.getMessage());
        }

        // 2. Stop service
        sb.append(" - Stopping Service\n");
        sshCommandService.runSSHCommand(ipAddress, "sudo systemctl stop " + service);

        // 3. Uninstall systemd service file
        sb.append(" - Removing systemd Service File\n");
        // Find systemd unit config files directory
        String foundStandardFlag = sshCommandService.runSSHScript(ipAddress, "if [[ -d /lib/systemd/system/ ]]; then echo found_standard; fi");
        if (foundStandardFlag.contains("found_standard")) {
            sshCommandService.runSSHCommand(ipAddress, "sudo rm -f  /lib/systemd/system/" + service + ".service");
        } else {
            sshCommandService.runSSHCommand(ipAddress, "sudo rm -f  /usr/lib/systemd/system/" + service + ".service");
        }

        // 4. Delete docker container
        sb.append(" - Removing docker container \n");
        sshCommandService.runSSHCommand(ipAddress, "sudo docker rm -f " + service + " || true ");

        // 5. Delete docker image
        sb.append(" - Removing docker image \n");
        sshCommandService.runSSHCommand(ipAddress, "sudo docker image rm -f eskimo:" + servicesDefinition.getService(service).getImageName());

        // 6. Reloading systemd daemon
        sb.append(" - Reloading systemd daemon \n");
        sshCommandService.runSSHCommand(ipAddress, "sudo systemctl daemon-reload");
        sshCommandService.runSSHCommand(ipAddress, "sudo systemctl reset-failed");

        return sb.toString();
    }

    private void proceedWithServiceInstallation(StringBuilder sb, String ipAddress, String service)
            throws IOException, SystemException, SSHCommandException {

        String imageName = servicesDefinition.getService(service).getImageName();

        sb.append(" - Creating archive and copying it over\n");
        File tmpArchiveFile = systemService.createRemotePackageFolder(sb, ipAddress, service, imageName);

        // 4. call setup script
        systemService.installationSetup(sb, ipAddress, service);

        // 5. cleanup
        systemService.installationCleanup(sb, ipAddress, service, imageName, tmpArchiveFile);
    }

    private void sshChmod755 (String ipAddress, String file) throws SSHCommandException {
        sshChmod (ipAddress, file, "755");
    }

    private void sshChmod (String ipAddress, String file, String mode) throws SSHCommandException {
        sshCommandService.runSSHCommand(ipAddress, new String[]{"sudo", "chmod", mode, file});
    }

    String getNodeFlavour(String ipAddress) throws SSHCommandException, SystemException {
        // Find out if debian or RHEL or SUSE
        String flavour = null;
        String rawIsDebian = sshCommandService.runSSHScript(ipAddress, "if [[ -f /etc/debian_version ]]; then echo debian; fi");
        if (rawIsDebian.contains("debian")) {
            flavour = "debian";
        }

        if (flavour == null) {
            String rawIsRedHat = sshCommandService.runSSHScript(ipAddress, "if [[ -f /etc/redhat-release ]]; then echo redhat; fi");
            if (rawIsRedHat.contains("redhat")) {
                flavour = "redhat";
            }
        }

        if (flavour == null) {
            String rawIsSuse = sshCommandService.runSSHScript(ipAddress, "if [[ -f /etc/SUSE-brand ]]; then echo suse; fi");
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
