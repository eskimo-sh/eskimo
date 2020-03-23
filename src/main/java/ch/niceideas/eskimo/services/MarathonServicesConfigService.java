package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MarathonServicesConfigService {

    private static final Logger logger = Logger.getLogger(ServicesConfigService.class);

    public static final String SERVICES_CONFIG_JSON_FILE = "/services-config.json";
    public static final String TMP_PATH_PREFIX = "/tmp/";

    @Autowired
    private SetupService setupService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SystemService systemService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private NotificationService notificationService;

    @Value("${system.packageDistributionPath}")
    private String packageDistributionPath = "./packages_distrib";

    @Value("${system.servicesSetupPath}")
    private String servicesSetupPath = "./services_setup";

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.operationWaitTimoutSeconds}")
    private int operationWaitTimout = 400;

    @Value("${system.baseInstallWaitTimoutSeconds}")
    private int baseInstallWaitTimout = 1000;

    void setMessagingService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /* For tests */
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }


    public void applyMarathonServicesConfig(MarathonOperationsCommand command) throws SystemException {


        boolean success = false;
        systemService.setProcessingPending();
        try {

            // Find out node running marathon

            // TODO

            String marathonIpAddress = findUniqueServiceIP("marathon");
            if (StringUtils.isBlank(marathonIpAddress)) {
                throw new SystemException("Marathon doesn't seem to be installed");
            }

            Set<String> liveIps = new HashSet<>();
            Set<String> deadIps = new HashSet<>();

            List<Pair<String, String>> nodesSetup = new ArrayList<>();


            // handle potential interruption request
            if (systemService.isInterrupted()) {
                return;
            }

            nodesSetup.add(new Pair<>("node_setup", marathonIpAddress));

            // Ping IP to make sure it is available, report problem with IP if it is not ad move to next one

            // find out if SSH connection to host can succeed
            try {
                String ping = systemService.sendPing(marathonIpAddress);

                if (!ping.startsWith("OK")) {
                    systemService.handleNodeDead(deadIps, marathonIpAddress);
                }
            } catch (SSHCommandException e) {
                logger.debug(e, e);
                systemService.handleNodeDead(deadIps, marathonIpAddress);
            }

            if (!deadIps.isEmpty()) {
                throw new SystemException("At least one configured node was found dead");
            }

            if (systemService.isInterrupted()) {
                return;
            }

            // Installation in batches (groups following dependencies)

            // TODO deploying on marathon 1 service at a time for now
            systemService.performPooledOperation(command.getInstallations(), 1, operationWaitTimout,
                    (operation, error) -> {
                        String service = operation;
                        installMarathonService(service, marathonIpAddress);
                    });

            // uninstallations
            // TODO deploying on marathon 1 service at a time for now
            systemService.performPooledOperation(command.getUninstallations(), 1, operationWaitTimout,
                    (operation, error) -> {
                        String service = operation;
                        uninstallMarathonService(service, marathonIpAddress);
                    });

            /*
            // restarts
            for (List<Pair<String, String>> restarts : servicesInstallationSorter.orderOperations (command.getRestarts(), nodesConfig, deadIps)) {
                performPooledOperation(restarts, parallelismInstallThreadCount, operationWaitTimout,
                        (operation, error) -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            if (liveIps.contains(ipAddress)) {
                                restartServiceForSystem(service, ipAddress);
                            }
                        });
            }
            */

            success = true;
        } catch (FileException | SetupException e) {
            logger.error (e, e);
            throw new SystemException(e);
        } finally {
            systemService.setLastOperationSuccess (success);
            systemService.releaseProcessingPending();
        }
    }

    private String findUniqueServiceIP(String service) throws FileException, SetupException {

        String uniqueServiceNodeName = findUniqueServiceNodeName(service);
        if (StringUtils.isBlank(uniqueServiceNodeName)) {
            return null;
        }

        return uniqueServiceNodeName.replace("-", ".");
    }

    private String findUniqueServiceNodeName(String service) throws FileException, SetupException {

        ServicesInstallStatusWrapper installStatus = configurationService.loadServicesInstallationStatus();

        for (String installFlag : installStatus.getRootKeys()) {

            if (installFlag.startsWith(service)
                    && installStatus.getValueForPathAsString(installFlag) != null
                    && installStatus.getValueForPathAsString(installFlag).equals("OK")) {

                String ipAddress = installFlag.substring(installFlag.indexOf(OperationsCommand.INSTALLED_ON_IP_FLAG) + OperationsCommand.INSTALLED_ON_IP_FLAG.length());
                return ipAddress.replace(".", "_");
            }
        }

        return null;
    }

    void uninstallMarathonService(String service, String marathonIpAddress) throws SystemException {
        String nodeName = marathonIpAddress.replace(".", "-");
        systemOperationService.applySystemOperation("Uninstallation of " + service + " on marathon node " + marathonIpAddress,
                builder -> proceedWithMarathonServiceUninstallation(builder, marathonIpAddress, service),
                status -> status.removeRootKey(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName));
        try {
            proxyManagerService.removeServerForService(service, findMarathonServiceNode(service));
        } catch (FileException | SetupException e) {
            logger.error(e, e);
            throw new SystemException(e);
        }
    }

    private String findMarathonServiceNode(String service) throws FileException, SetupException {
        return findUniqueServiceNodeName (service);
    }

    void installMarathonService(String service, String marathonIpAddress)
            throws SystemException {
        String nodeName = marathonIpAddress.replace(".", "-");
        systemOperationService.applySystemOperation("installation of " + service + " on marathon node " + marathonIpAddress,
                builder -> proceedWithMarathonServiceInstallation(builder, marathonIpAddress, service),
                status -> status.setValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName, "OK"));
    }

    private String proceedWithMarathonServiceUninstallation(StringBuilder sb, String marathonIpAddress, String service)
            throws SSHCommandException, SystemException {

        throw new UnsupportedOperationException("To Be Implemented !");

        /*
        // 1. Calling uninstall.sh script if it exists
        File containerFolder = new File(servicesSetupPath + "/" + service);
        if (!containerFolder.exists()) {
            throw new SystemException("Folder " + servicesSetupPath + "/" + service + " doesn't exist !");
        }

        try {
            File uninstallScriptFile = new File(containerFolder, "uninstall.sh");
            if (uninstallScriptFile.exists()) {
                sb.append(" - Calling uninstall script\n");

                sb.append(sshCommandService.runSSHScriptPath(marathonIpAddress, uninstallScriptFile.getAbsolutePath()));
            }
        } catch (SSHCommandException e) {
            logger.warn (e, e);
            sb.append (e.getMessage());
        }

        // 2. Stop service
        sb.append(" - Stopping Service\n");
        sshCommandService.runSSHCommand(marathonIpAddress, "sudo systemctl stop " + service);

        // 3. Uninstall systemd service file
        sb.append(" - Removing systemd Service File\n");
        // Find systemd unit config files directory
        String foundStandardFlag = sshCommandService.runSSHScript(marathonIpAddress, "if [[ -d /lib/systemd/system/ ]]; then echo found_standard; fi");
        if (foundStandardFlag.contains("found_standard")) {
            sshCommandService.runSSHCommand(marathonIpAddress, "sudo rm -f  /lib/systemd/system/" + service + ".service");
        } else {
            sshCommandService.runSSHCommand(marathonIpAddress, "sudo rm -f  /usr/lib/systemd/system/" + service + ".service");
        }

        // 4. Delete docker container
        sb.append(" - Removing docker container \n");
        sshCommandService.runSSHCommand(marathonIpAddress, "sudo docker rm -f " + service + " || true ");

        // 5. Delete docker image
        sb.append(" - Removing docker image \n");
        sshCommandService.runSSHCommand(marathonIpAddress, "sudo docker image rm -f eskimo:" + servicesDefinition.getService(service).getImageName());

        // 6. Reloading systemd daemon
        sb.append(" - Reloading systemd daemon \n");
        sshCommandService.runSSHCommand(marathonIpAddress, "sudo systemctl daemon-reload");
        sshCommandService.runSSHCommand(marathonIpAddress, "sudo systemctl reset-failed");

        return sb.toString();
        */
    }

    private void proceedWithMarathonServiceInstallation(StringBuilder sb, String marathonIpAddress, String service)
            throws IOException, SystemException, SSHCommandException {

        String imageName = servicesDefinition.getService(service).getImageName();

        sb.append(" - Creating archive and copying it over to marathon node \n");
        File tmpArchiveFile = systemService.createRemotePackageFolder(sb, marathonIpAddress, service, imageName);

        // 4. call setup script
        systemService.installationSetup(sb, marathonIpAddress, service);

        /*

        // 5. cleanup
        systemService.installationCleanup(sb, ipAddress, service, imageName, tmpArchiveFile);throw new SystemException(e);
        }
        */
    }
}
