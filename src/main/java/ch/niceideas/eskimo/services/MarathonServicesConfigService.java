package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.utils.ErrorStatusHelper;
import ch.niceideas.eskimo.utils.SystemStatusParser;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpRequest;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    private HttpClient httpClient;

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

    public MarathonServicesConfigService () {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setDefaultRequestConfig(buildRequestConfig())
                .setDefaultSocketConfig(buildSocketConfig());

        clientBuilder.setMaxConnTotal(2);

        httpClient = clientBuilder.build();
    }

    protected RequestConfig buildRequestConfig() {
        return RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES) // we handle them in the servlet instead
                .setConnectTimeout(10000)
                .setSocketTimeout(20000)
                .setConnectionRequestTimeout(10000)
                .build();
    }

    protected SocketConfig buildSocketConfig() {
        return SocketConfig.custom()
                .setSoTimeout(20000)
                .build();
    }

    protected String queryMarathon (String endpoint) throws MarathonServicesConfigurationException {

        try {
            ProxyTunnelConfig marathonTunnelConfig = proxyManagerService.getTunnelConfig("marathon");

            // apps/cerebro
            BasicHttpRequest request = new BasicHttpRequest("GET", "http://localhost:" + marathonTunnelConfig.getLocalPort() + "/v2/" + endpoint);

            HttpResponse response = httpClient.execute(
                    new HttpHost("localhost", marathonTunnelConfig.getLocalPort(), "http"),
                    request);


            InputStream result = response.getEntity().getContent();

            Header contentencodingHeader = response.getEntity().getContentEncoding();

            return StreamUtils.getAsString(result, contentencodingHeader != null ? contentencodingHeader.getValue() : "UTF-8");

        } catch (IOException e) {
            logger.error (e, e);
            throw new MarathonServicesConfigurationException(e);
        }
    }

    private Pair<String,String> getServiceRuntimeNode(String service) throws MarathonServicesConfigurationException {
        return getAndWaitServiceRuntimeNode(service, 1);
    }

    private Pair<String, String> getAndWaitServiceRuntimeNode (String service,int numberOfAttempts) throws
        MarathonServicesConfigurationException {

        for (int i = 0; i < numberOfAttempts; i++) {
            String serviceJson = queryMarathon("apps/" + service);

            JsonWrapper serviceResult = new JsonWrapper(serviceJson);

            if (StringUtils.isNotBlank(serviceResult.getValueForPathAsString("message"))
                    && serviceResult.getValueForPathAsString("message").contains("does not exist")) {
                return new Pair<>(null, "NA");
            }

            String nodeIp = serviceResult.getValueForPathAsString("app.tasks.0.host");
            if (StringUtils.isBlank(nodeIp) && i < numberOfAttempts - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.debug (e, e);
                }
                continue;
            }

            String status = "notOK";

            //Integer tasksUnhealthy = (Integer) serviceResult.getValueForPath("app.tasksUnhealthy");

            Integer tasksRunning = (Integer) serviceResult.getValueForPath("app.tasksRunning");
            if (tasksRunning != null && tasksRunning.intValue() == 1) {
                status = "running";
            }

            return new Pair<>(nodeIp, status);
        }

        return new Pair<>(null, "notOK");
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
                        installMarathonService(operation, marathonIpAddress);
                    });

            // uninstallations
            // TODO deploying on marathon 1 service at a time for now
            systemService.performPooledOperation(command.getUninstallations(), 1, operationWaitTimout,
                    (operation, error) -> {
                        uninstallMarathonService(operation, marathonIpAddress);
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

    String findUniqueServiceIP(String service) throws FileException, SetupException {

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
                return ipAddress.replace(".", "-");
            }
        }

        return null;
    }

    void uninstallMarathonService(String service, String marathonIpAddress) throws SystemException {
        StringBuilder installedNodeBuilder = new StringBuilder();
        systemOperationService.applySystemOperation("Uninstallation of " + service + " on marathon node " + marathonIpAddress,
                builder -> {
                    try {
                        Pair<String, String> nodeNameAndStatus = getServiceRuntimeNode(service);
                        if (nodeNameAndStatus.getKey() == null) {
                            throw new SystemException("Service " + service + " isn't found in marathon");
                        }
                        installedNodeBuilder.append (nodeNameAndStatus.getKey().replace(".", "-"));
                        proceedWithMarathonServiceUninstallation(builder, marathonIpAddress, service);
                    } catch (MarathonServicesConfigurationException e) {
                        logger.error (e, e);
                        throw new SystemException(e);
                    }
                },
                status -> {
                    if (installedNodeBuilder.length() == 0) {
                        throw new RuntimeException("Unsupported : couldn't find installation node");
                    }
                    status.removeRootKey(service + OperationsCommand.INSTALLED_ON_IP_FLAG + installedNodeBuilder.toString());
                });
        try {
            proxyManagerService.removeServerForService(service, findUniqueServiceNodeName(service));
        } catch (FileException | SetupException e) {
            logger.error(e, e);
            throw new SystemException(e);
        }
    }


    void installMarathonService(String service, String marathonIpAddress)
            throws SystemException {
        StringBuilder installedNodeBuilder = new StringBuilder();
        systemOperationService.applySystemOperation("installation of " + service + " on marathon node " + marathonIpAddress,
                builder -> {
                    try {
                        proceedWithMarathonServiceInstallation(builder, marathonIpAddress, service);
                        Pair<String, String> nodeNameAndStatus = getAndWaitServiceRuntimeNode(service, 120); // giving it 2 minutes to start
                        if (nodeNameAndStatus.getKey() == null) {
                            throw new SystemException("Service " + service + " isn't found in marathon");
                        }
                        installedNodeBuilder.append (nodeNameAndStatus.getKey().replace(".", "-"));
                    } catch (MarathonServicesConfigurationException e) {
                        logger.error (e, e);
                        throw new SystemException(e);
                    }
                },
                status -> {
                    if (installedNodeBuilder.length() == 0) {
                        throw new RuntimeException("Unsupported : couldn't find installation node");
                    }
                    status.setValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + installedNodeBuilder.toString(), "OK");
                });
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

    public void fetchMarathonServicesStatus
            (Map<String, String> statusMap, ServicesInstallStatusWrapper servicesInstallationStatus)
            throws MarathonServicesConfigurationException {

        // 3.1 Node answers
        try {

            String marathonIpAddress = findUniqueServiceIP("marathon");
            if (!StringUtils.isBlank(marathonIpAddress)) {

                // find out if SSH connection to host can succeeed
                String ping = null;
                try {
                    ping = systemService.sendPing(marathonIpAddress);
                } catch (SSHCommandException e) {
                    logger.warn(e.getMessage());
                    logger.debug(e, e);
                }

                if (StringUtils.isNotBlank(ping) && ping.startsWith("OK")) {

                    for (String service : servicesDefinition.listMarathonServices()) {

                        // should service be installed on marathon ?
                        boolean shall = this.shouldInstall(service);

                        // check if service is installed ?
                        //check if service installed using SSH
                        Pair<String, String> nodeNameAndStatus = this.getServiceRuntimeNode(service);

                        String nodeIp = nodeNameAndStatus.getKey();

                        boolean installed = StringUtils.isNotBlank(nodeIp);
                        boolean running = nodeNameAndStatus.getValue().equals("running");

                        String nodeName = nodeIp != null ? nodeIp.replace(".", "-") : null;

                        // uninstalled services are put to the marathon node for now
                        if (StringUtils.isBlank(nodeName)) {
                            nodeName = marathonIpAddress.replace(".", "-");
                        }

                        systemService.feedInServiceStatus(statusMap, servicesInstallationStatus, nodeIp, nodeName, service, shall, installed, running);
                    }
                }
            }
        } catch (JSONException | ConnectionManagerException | SystemException | SetupException | FileException e) {
            logger.error(e, e);
            throw new MarathonServicesConfigurationException(e.getMessage(), e);
        }
    }

    boolean shouldInstall(String service) throws SetupException, SystemException {
        MarathonServicesConfigWrapper marathonConfig = configurationService.loadMarathonServicesConfig();
        if (marathonConfig != null) {

            // search it in config
            return StringUtils.isNotBlank ((String)marathonConfig.getValueForPath(service + "_install"))
                    && marathonConfig.getValueForPath(service + "_install").equals("on");
        }

        return false;
    }
}
