package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StreamUtils;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MarathonService {

    private static final Logger logger = Logger.getLogger(MarathonService.class);

    public static final int MARATHON_UNINSTALL_SHUTDOWN_ATTEMPTS = 200;

    public static final String MARATHON_NA_FLAG = "MARATHON_NA";
    public static final String MARATHON_CONTEXT = "apps/";
    public static final String MARATHON = "marathon";
    public static final String DEPLOYMENT_ID_FIELD = "deploymentId";
    public static final String RUNNING_STATUS = "running";
    public static final String MARATHON_NODE = "marathon node";
    public static final String SEPARATOR = "--------------------------------------------------------------------------------\n";

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SystemService systemService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private MemoryComputer memoryComputer;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NodesConfigurationService nodesConfigurationService;

    @Value("${system.packageDistributionPath}")
    private String packageDistributionPath = "./packages_distrib";

    @Value("${system.servicesSetupPath}")
    private String servicesSetupPath = "./services_setup";

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.marathonOperationWaitTimoutSeconds}")
    private int marathonOperationWaitTimoutSeconds = 100 * 60; // 100 minutes

    @Value("${system.baseInstallWaitTimoutSeconds}")
    private int baseInstallWaitTimout = 1000;

    private final HttpClient httpClient;

    /* For tests */
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }
    void setSshCommandService(SSHCommandService sshCommandService) {
        this.sshCommandService = sshCommandService;
    }
    void setSystemOperationService(SystemOperationService systemOperationService) {
        this.systemOperationService = systemOperationService;
    }
    void setProxyManagerService(ProxyManagerService proxyManagerService) {
        this.proxyManagerService = proxyManagerService;
    }
    void setMemoryComputer(MemoryComputer memoryComputer) {
        this.memoryComputer = memoryComputer;
    }
    void setMessagingService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    void setNodesConfigurationService(NodesConfigurationService nodesConfigurationService) {
        this.nodesConfigurationService = nodesConfigurationService;
    }

    public MarathonService() {
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

    protected String queryMarathon (String endpoint) throws MarathonException {
        return queryMarathon(endpoint, "GET");
    }

    protected String queryMesosAgent (String host, String endpoint) throws MarathonException {
        return queryMesosAgent(host, endpoint, "GET");
    }

    protected String queryMesosAgent (String host, String endpoint, String method) throws MarathonException {

        try {
            ProxyTunnelConfig mesosAgentTunnelConfig = proxyManagerService.getTunnelConfig(servicesDefinition.getService("mesos-agent").getServiceId(host));
            if (mesosAgentTunnelConfig == null) {
                return null;
            }

            // apps/cerebro
            BasicHttpRequest request = new BasicHttpRequest(method, "http://localhost:" + mesosAgentTunnelConfig.getLocalPort() + "/" + endpoint);

            return sendHttpRequestAndGetResult(mesosAgentTunnelConfig, request);

        } catch (IOException e) {
            logger.debug (e, e);
            throw new MarathonException(e);
        }
    }

    protected String queryMarathon (String endpoint, String method) throws MarathonException {

        try {
            ProxyTunnelConfig marathonTunnelConfig = proxyManagerService.getTunnelConfig(MARATHON);
            if (marathonTunnelConfig == null) {
                return null;
            }

            // apps/cerebro
            BasicHttpRequest request = new BasicHttpRequest(method, "http://localhost:" + marathonTunnelConfig.getLocalPort() + "/v2/" + endpoint);

            return sendHttpRequestAndGetResult(marathonTunnelConfig, request);

        } catch (IOException e) {
            //logger.debug (e, e);
            throw new MarathonException(e);
        }
    }

    protected String updateMarathon (String endpoint, String method, String content) throws MarathonException {

        try {
            ProxyTunnelConfig marathonTunnelConfig = proxyManagerService.getTunnelConfig(MARATHON);
            if (marathonTunnelConfig == null) {
                throw new MarathonException("Marathon is not detected as present (in proxy)");
            }

            // apps/cerebro
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(method, "http://localhost:" + marathonTunnelConfig.getLocalPort() + "/v2/" + endpoint);

            BasicHttpEntity requestContent = new BasicHttpEntity();
            requestContent.setContentType("application/json");
            requestContent.setContent(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
            request.setEntity(requestContent);

            return sendHttpRequestAndGetResult(marathonTunnelConfig, request);

        } catch (IOException e) {
            logger.error (e, e);
            throw new MarathonException(e);
        }
    }

    protected String sendHttpRequestAndGetResult(ProxyTunnelConfig tunnelConfig, BasicHttpRequest request) throws IOException {
        HttpResponse response = httpClient.execute(
                new HttpHost("localhost", tunnelConfig.getLocalPort(), "http"),
                request);

        InputStream result = response.getEntity().getContent();

        Header contentencodingHeader = response.getEntity().getContentEncoding();

        return StreamUtils.getAsString(result, contentencodingHeader != null ? contentencodingHeader.getValue() : "UTF-8");
    }

    private Pair<String,String> getServiceRuntimeNode(String service) throws MarathonException {
        return getAndWaitServiceRuntimeNode(service, 1);
    }

    protected Pair<String, String> getAndWaitServiceRuntimeNode (String service, int numberOfAttempts) throws
            MarathonException  {

        ServicesInstallStatusWrapper servicesInstallStatus;
        try {
            servicesInstallStatus = configurationService.loadServicesInstallationStatus();
        } catch (FileException | SetupException e) {
            logger.error (e, e);
            throw new MarathonException(e);
        }

        for (int i = 0; i < numberOfAttempts; i++) {
            String serviceJson = null;
            try {
                serviceJson = queryMarathon(MARATHON_CONTEXT + service);
            } catch (MarathonException e) {
                if (e.getCause() != null) {
                    logger.warn("getAndWaitServiceRuntimeNode - Got " + e.getCause().getClass() + ":" + e.getCause().getMessage());
                } else {
                    logger.warn("getAndWaitServiceRuntimeNode - Got " + e.getClass() + ":" + e.getMessage());
                }
            }
            if (StringUtils.isBlank(serviceJson) || !serviceJson.startsWith("{")) {
                if (i < numberOfAttempts - 1) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        logger.debug (e, e);
                        // Restore interrupted state...
                        Thread.currentThread().interrupt();
                    }
                    continue;
                } else {
                    return new Pair<>(MARATHON_NA_FLAG, "NA");
                }
            }

            JsonWrapper serviceResult = new JsonWrapper(serviceJson);

            if (StringUtils.isNotBlank(serviceResult.getValueForPathAsString("message"))
                    && serviceResult.getValueForPathAsString("message").contains("does not exist")) {

                // This is now the sitatiuon where marathon knows nothing about the service
                // in this only case we return null as nodeIp to identify this
                return new Pair<>(null, "NA");
            }

            String nodeIp = serviceResult.getValueForPathAsString("app.tasks.0.host");
            if (StringUtils.isBlank(nodeIp) && i < numberOfAttempts - 1) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    logger.debug (e, e);
                    // Restore interrupted state...
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            if (StringUtils.isBlank(nodeIp)) {

                // service is not started by marathon, assuming it on marathon node
                nodeIp = servicesInstallStatus.getFirstIpAddress(MARATHON);
            }

            String status = "notOK";

            //Integer tasksUnhealthy = (Integer) serviceResult.getValueForPath("app.tasksUnhealthy");

            Integer tasksRunning = (Integer) serviceResult.getValueForPath("app.tasksRunning");
            if (tasksRunning != null && tasksRunning == 1) {
                status = RUNNING_STATUS;
            }

            return new Pair<>(nodeIp, status);
        }

        return new Pair<>(null, "notOK");
    }

    public void applyMarathonServicesConfig(MarathonOperationsCommand command) throws MarathonException {

        logger.info ("Starting Marathon Deployment Operations");
        boolean success = false;
        systemService.setProcessingPending();
        try {

            // Find out node running marathon
            ServicesInstallStatusWrapper servicesInstallStatus = configurationService.loadServicesInstallationStatus();

            String marathonIpAddress = servicesInstallStatus.getFirstIpAddress(MARATHON);
            if (StringUtils.isBlank(marathonIpAddress)) {
                String message = "Marathon doesn't seem to be installed";
                notificationService.addError(message);
                messagingService.addLines(message);
                messagingService.addLines ("Marathon services configuration is saved but will need to be re-applied when marathon is available.");

                // special case : if some marathon services are getting uninstalled, and marathon is nowhere installed or anything, let's force flag them as uninstalled
                try {
                    SystemStatusWrapper lastStatus = systemService.getStatus();
                    String marathonNodeName = lastStatus.getFirstNodeName(MARATHON);
                    if (StringUtils.isBlank(marathonNodeName)) {

                        if (command.getUninstallations().size() > 0) {
                            messagingService.addLines("Uninstalled marathon services will be flagged as uninstalled even though no operation can be performed in marathon.");
                            configurationService.updateAndSaveServicesInstallationStatus(servicesInstallationStatus -> {
                                for (String uninstalledMarathonService : command.getUninstallations()) {
                                    servicesInstallationStatus.removeInstallationFlag(uninstalledMarathonService, ServicesInstallStatusWrapper.MARATHON_NODE);
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
            if (systemService.isInterrupted()) {
                return;
            }

            NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();

            List<Pair<String, String>> nodesSetup = systemService.buildDeadIps(new HashSet<String>(){{add(marathonIpAddress);}}, nodesConfig, liveIps, deadIps);

            if (deadIps.contains(marathonIpAddress)) {
                String message = "The marathon node is dead. cannot proceed any further with installation.";
                notificationService.addError(message);
                messagingService.addLines(message);
                messagingService.addLines ("Marathon services configuration is saved but will need to be re-applied when marathon is available.");
                throw new MarathonException(message);
            }

            if (systemService.isInterrupted()) {
                return;
            }

            ensureMarathonAvailability();

            if (systemService.isInterrupted()) {
                return;
            }

            if (nodesSetup == null) {
                return;
            }

            MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, deadIps);

            if (systemService.isInterrupted()) {
                return;
            }

            // Nodes re-setup (topology)
            systemService.performPooledOperation (new ArrayList<> (liveIps), parallelismInstallThreadCount, baseInstallWaitTimout,
                    (operation, error) -> {
                        // topology
                        if (error.get() == null) {
                            systemOperationService.applySystemOperation("Installation of Topology and settings on " + operation,
                                    builder -> nodesConfigurationService.installTopologyAndSettings(nodesConfig, command.getRawConfig(), memoryModel, operation), null);
                        }

                    });

            // Installation in batches (groups following dependencies) - deploying on marathon 1 service at a time for now
            systemService.performPooledOperation(command.getInstallations(), 1, marathonOperationWaitTimoutSeconds,
                    (operation, error) -> installMarathonService(operation, marathonIpAddress));

            // uninstallations - deploying on marathon 1 service at a time for now
            systemService.performPooledOperation(command.getUninstallations(), 1, marathonOperationWaitTimoutSeconds,
                    (operation, error) -> uninstallMarathonService(operation, marathonIpAddress));

            /*
            // restarts
            for (List<Pair<String, String>> restarts : servicesInstallationSorter.orderOperations (command.getRestarts(), nodesConfig, deadIps)) {
                performPooledOperation(restarts, parallelismInstallThreadCount, marathonOperationWaitTimoutSeconds,
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
        } catch (FileException | SetupException | SystemException e) {
            logger.error (e, e);
            messagingService.addLines (e.getMessage());
            notificationService.addError("Marathon Services installation failed !");
            throw new MarathonException(e);
        } finally {
            systemService.setLastOperationSuccess (success);
            systemService.releaseProcessingPending();
            logger.info ("Marathon Deployment Operations Completed.");
        }
    }

    void ensureMarathonAvailability() throws MarathonException {
        try {
            SystemStatusWrapper lastStatus = systemService.getStatus();

            String marathonNodeName = lastStatus.getFirstNodeName(MARATHON);
            if (StringUtils.isBlank(marathonNodeName)) {
                String message = "Marathon is not available";
                notificationService.addError(message);
                messagingService.addLines("Marathon is not available. The changes in marathon services configuration and " +
                        "deployments are saved but they will need to be applied again another time when " +
                        "marathon is available");
                throw new MarathonException(message);
            } else {

                if (!lastStatus.isServiceOKOnNode(MARATHON, marathonNodeName)) {

                    String message = "Marathon is not properly running";
                    notificationService.addError(message);
                    messagingService.addLines("Marathon is not properly running. The changes in marathon services configuration and " +
                            "deployments are saved but they will need to be applied again another time when " +
                            "marathon is available");
                    throw new MarathonException(message);
                }
            }

        } catch (SystemService.StatusExceptionWrapperException e) {

            String message = "Couldn't get last marathon Service status";
            notificationService.addError(message);
            String warnings = "Couldn't get last marathon Service status to assess feasibility of marathon setup\n";
            warnings += e.getCause().getCause() + ":" + e.getCause().getMessage();
            messagingService.addLines(warnings);
            throw new MarathonException(message);
        }
    }

    void uninstallMarathonService(String service, String marathonIpAddress) throws SystemException {
        String nodeIp = null;
        try {
            Pair<String, String> nodeNameAndStatus = this.getServiceRuntimeNode(service);
            nodeIp = nodeNameAndStatus.getKey();
        } catch (MarathonException e) {
            logger.warn (e.getMessage());
            logger.debug (e, e);
        }
        systemOperationService.applySystemOperation("Uninstallation of " + service + " on marathon node " + marathonIpAddress,
                builder -> {
                    try {
                        proceedWithMarathonServiceUninstallation(builder, marathonIpAddress, service);
                    } catch (MarathonException e) {
                        logger.error (e, e);
                        throw new SystemException(e);
                    }
                },
                status -> status.removeInstallationFlag(service, ServicesInstallStatusWrapper.MARATHON_NODE));
        if (nodeIp != null) {
            proxyManagerService.removeServerForService(service, nodeIp);
        } else {
            logger.warn ("No previous IP could be found for service " + service);
        }
    }


    void installMarathonService(String service, String marathonIpAddress)
            throws SystemException {
        systemOperationService.applySystemOperation("installation of " + service + " on marathon node " + marathonIpAddress,
                builder -> proceedWithMarathonServiceInstallation(builder, marathonIpAddress, service),
                status -> status.setInstallationFlag(service, ServicesInstallStatusWrapper.MARATHON_NODE, "OK") );
    }

    private void proceedWithMarathonServiceUninstallation(StringBuilder sb, String marathonIpAddress, String service)
            throws SSHCommandException, SystemException, MarathonException {

        // 1. Calling uninstall.sh script if it exists
        systemService.callUninstallScript(sb, marathonIpAddress, service);

        // 2. Stop service
        sb.append("Deleting marathon application for ").append(service).append("\n");
        String killResultString = queryMarathon(MARATHON_CONTEXT + service, "DELETE");
        JsonWrapper killResult = new JsonWrapper(killResultString);

        String deploymentId = killResult.getValueForPathAsString(DEPLOYMENT_ID_FIELD);
        if (StringUtils.isBlank(deploymentId)) {
            sb.append("WARNING : Could not find any deployment ID when killing tasks for ").append(service).append("\n");
        } else {
            sb.append("Tasks killing deployment ID for ").append(service).append(" is ").append(deploymentId).append("\n");
        }

        // 3. Wait for service to be stopped
        waitForServiceShutdown(service);

        // 4. Delete docker container
        sb.append(" - TODO Removing docker image from registry \n");

        // 5. remove blobs
        sb.append(" - Removing service from docker repository \n");

        // 5.1 remove repository for service
        sshCommandService.runSSHCommand(marathonIpAddress,
                "docker exec -i --user root marathon bash -c \"rm -Rf /var/lib/marathon/docker_registry/docker/registry/v2/repositories/" + service + "\"");

        // 5.2 run garbage collection to remove blobs
        sb.append(" - Running garbage collection \n");
        sshCommandService.runSSHCommand(marathonIpAddress,
                "docker exec -i --user root marathon bash -c \"docker-registry garbage-collect /etc/docker/registry/config.yml\"");
    }

    private void proceedWithMarathonServiceInstallation(StringBuilder sb, String marathonIpAddress, String service)
            throws IOException, SystemException, SSHCommandException {

        String imageName = servicesDefinition.getService(service).getImageName();

        sb.append(" - Creating archive and copying it over to marathon node \n");
        File tmpArchiveFile = systemService.createRemotePackageFolder(sb, marathonIpAddress, service, imageName);

        // 4. call setup script
        systemService.installationSetup(sb, marathonIpAddress, service);

        // 5. cleanup
        systemService.installationCleanup(sb, marathonIpAddress, service, imageName, tmpArchiveFile);
    }

    public void fetchMarathonServicesStatus
            (Map<String, String> statusMap, ServicesInstallStatusWrapper servicesInstallationStatus)
            throws MarathonException {

        // 3.1 Node answers
        try {

            String marathonIpAddress = servicesInstallationStatus.getFirstIpAddress(MARATHON);

            String ping = null;
            if (!StringUtils.isBlank(marathonIpAddress)) {

                // find out if SSH connection to host can succeeed
                try {
                    ping = systemService.sendPing(marathonIpAddress);
                } catch (SSHCommandException e) {
                    logger.warn(e.getMessage());
                    logger.debug(e, e);
                }
            }

            MarathonServicesConfigWrapper marathonConfig = configurationService.loadMarathonServicesConfig();
            for (String service : servicesDefinition.listMarathonServices()) {

                // should service be installed on marathon ?
                boolean shall = this.shouldInstall(marathonConfig, service);

                Pair<String, String> nodeNameAndStatus = new Pair<>(MARATHON_NA_FLAG, "NA");
                if (StringUtils.isNotBlank(ping) && ping.startsWith("OK")) {
                    nodeNameAndStatus = this.getServiceRuntimeNode(service);
                }

                String nodeIp = nodeNameAndStatus.getKey();

                // if marathon is not answering, we assume service is still installed if it has been installed before
                // we identify it on marathon node then.
                if (nodeIp != null && nodeIp.equals(MARATHON_NA_FLAG)) {
                    if (StringUtils.isNotBlank(servicesInstallationStatus.getFirstIpAddress(service))) {
                        nodeIp = marathonIpAddress;
                    } else {
                        nodeIp = null;
                    }
                }

                boolean installed = StringUtils.isNotBlank(nodeIp);
                boolean running = nodeNameAndStatus.getValue().equals(RUNNING_STATUS);

                String nodeName = nodeIp != null ? nodeIp.replace(".", "-") : null;

                // uninstalled services are identified on the marathon node
                if (StringUtils.isBlank(nodeName)) {
                    if (StringUtils.isNotBlank(marathonIpAddress)) {
                        nodeName = marathonIpAddress.replace(".", "-");
                    } else {
                        nodeName = servicesInstallationStatus.getFirstNodeName(MARATHON);
                    }
                    // last attempt, get it from theoretical perspective
                    if (StringUtils.isBlank(nodeName)) {
                        nodeName = configurationService.loadNodesConfig().getFirstNodeName(MARATHON);
                    }
                }

                systemService.feedInServiceStatus (
                        statusMap, servicesInstallationStatus, nodeIp, nodeName,
                        ServicesInstallStatusWrapper.MARATHON_NODE,
                        service, shall, installed, running);
            }
        } catch (JSONException | ConnectionManagerException | SystemException | SetupException  e) {
            logger.error(e, e);
            throw new MarathonException(e.getMessage(), e);
        }
    }

    boolean shouldInstall(MarathonServicesConfigWrapper marathonConfig, String service) {
        if (marathonConfig != null) {
            return marathonConfig.isServiceInstallRequired(service);
        }
        return false;
    }

    public void showJournalMarathon(Service service) throws MarathonException, SSHCommandException {
        ensureMarathonAvailability();
        systemService.applyServiceOperation(service.getName(), MARATHON_NODE, "Showing journal", () -> showJournalMarathonInternal(service));
    }

    String showJournalMarathonInternal(Service service) {

        StringBuilder resultBuilder = new StringBuilder();

        JsonWrapper mesosInfo;
        try {
            String mesosInfoResult = queryMarathon("info");

            if (StringUtils.isBlank (mesosInfoResult)) {
                resultBuilder.append ("Failed to fetch marathon information  ! \n");
                resultBuilder.append ("(Got empty marathon information)\n");
                return resultBuilder.toString();
            }

            mesosInfo = new JsonWrapper(mesosInfoResult);

        } catch (MarathonException e) {
            logger.error (e, e);
            resultBuilder.append ("Failed to fetch marathon information  ! \n");
            resultBuilder.append ("Got exception\n");
            resultBuilder.append (e.getMessage());

            // Stopping here
            return resultBuilder.toString();
        }

        try {
            String serviceJson = queryMarathon(MARATHON_CONTEXT + service.getName());

            JsonWrapper serviceResult = new JsonWrapper(serviceJson);

            resultBuilder.append("Marathon Service Definition for ").append(service.getName()).append(":\n");
            resultBuilder.append (SEPARATOR);
            resultBuilder.append(serviceResult.getFormattedValue());
            resultBuilder.append ("\n\n");

            String mesosNodeIp = serviceResult.getValueForPathAsString("app.tasks.0.host"); // 0
            String mesosSlaveId= serviceResult.getValueForPathAsString("app.tasks.0.slaveId"); // 1

            String frameworkId = mesosInfo.getValueForPathAsString("frameworkId"); // 2

            String mesosTaskId = serviceResult.getValueForPathAsString("app.tasks.0.id"); // 3

            JsonWrapper mesosAgentState;
            try {

                String mesosAgentStateString = queryMesosAgent (mesosNodeIp, "state");

                if (StringUtils.isBlank (mesosAgentStateString)) {
                    resultBuilder.append ("Failed to mesos agent information  !\n");
                    resultBuilder.append ("(Got empty mesos agent information)\n");
                    return resultBuilder.toString();
                }

                mesosAgentState = new JsonWrapper(mesosAgentStateString);

            } catch (MarathonException e) {
                logger.error (e, e);
                resultBuilder.append("Failed to fetch mesos agent information for ").append(mesosNodeIp).append(" !\n");
                resultBuilder.append ("Got exception\n");
                resultBuilder.append (e.getMessage());

                // Stopping here
                return resultBuilder.toString();
            }

            String mesosContainerId = null; // 4
            String mesosContainerDirectory = null; // TOTAL

            JSONArray frameworks = mesosAgentState.getSubJSONArray("frameworks");
            for (int j = 0; j < frameworks.length(); j++) {

                JSONObject framework = frameworks.getJSONObject(j);

                if (framework.getString("id").equals(frameworkId)) {

                    JSONArray executors = framework.getJSONArray("executors");

                    for (int i = 0; i < executors.length(); i++) {

                        JSONObject executor = executors.getJSONObject(i);
                        String executorId = executor.getString("id");
                        if (executorId.equals(mesosTaskId)) {
                            mesosContainerId = executor.getString("container");
                            mesosContainerDirectory  = executor.getString("directory");
                            break;
                        }
                    }

                    break;
                }
            }

            resultBuilder.append("Mesos Information for service ").append(service.getName()).append(" :\n");
            resultBuilder.append (SEPARATOR);
            resultBuilder.append(" - Mesos Node IP              : ").append(mesosNodeIp).append("\n");
            resultBuilder.append(" - Mesos Slave ID             : ").append(mesosSlaveId).append("\n");
            resultBuilder.append(" - Marathon framework ID      : ").append(frameworkId).append("\n");
            resultBuilder.append(" - Mesos Task ID              : ").append(mesosTaskId).append("\n");
            resultBuilder.append(" - Mesos Container ID         : ").append(mesosContainerId).append("\n");
            resultBuilder.append(" - Mesos Container directory  : ").append(mesosContainerDirectory).append("\n\n");

            String stdOutContent = queryMesosAgent(mesosNodeIp, "/files/download?path=" + mesosContainerDirectory + "/stdout");

            resultBuilder.append ("STDOUT :\n");
            resultBuilder.append (SEPARATOR);
            resultBuilder.append (stdOutContent);
            resultBuilder.append ("\n");


            String stdErrContent = queryMesosAgent(mesosNodeIp, "/files/download?path=" + mesosContainerDirectory + "/stderr");

            resultBuilder.append ("STDERR :\n");
            resultBuilder.append (SEPARATOR);
            resultBuilder.append (stdErrContent);
            resultBuilder.append ("\n");

            // http://192.168.10.11:5051/files/download?path=/var/lib/mesos/slave/slaves/
            // de95d2e8-22c5-4a74-8b3a-915f16b12bfb-S0/
            // frameworks/
            // de95d2e8-22c5-4a74-8b3a-915f16b12bfb-0000/
            // executors/
            // zeppelin.instance-70446d82-854d-11ea-92ab-0242bf9b78f7._app.1/
            // runs/
            // 75f7c509-6237-43db-9119-8ecdffb165c7/stderr


        } catch (MarathonException e) {
            logger.error (e, e);
            resultBuilder.append("Failed to fetch journal for ").append(service.getName()).append(" !\n");
            resultBuilder.append ("Got exception\n");
            resultBuilder.append (e.getMessage());
        }

        return resultBuilder.toString();
    }

    public void startServiceMarathon(Service service) throws MarathonException, SSHCommandException {
        ensureMarathonAvailability();
        systemService.applyServiceOperation(service.getName(), MARATHON_NODE, "Starting", () -> startServiceMarathonInternal(service));
    }

    String startServiceMarathonInternal(Service service) throws MarathonException {

        StringBuilder log = new StringBuilder();

        Pair<String, String> nodeNameAndStatus = this.getAndWaitServiceRuntimeNode(service.getName(), 10);

        String nodeIp = nodeNameAndStatus.getKey();

        boolean installed = StringUtils.isNotBlank(nodeIp);
        boolean running = nodeNameAndStatus.getValue().equals(RUNNING_STATUS);

        if (!installed) {
            log.append("ERROR - Service ").append(service.getName()).append(" is not installed.").append("\n");
            throw new MarathonException("Service " + service.getName() + " is not installed.");

        } else if (running) {

            log.append("WARNING - Service ").append(service.getName()).append(" is already started").append("\n");

        } else {

            String startResultString = updateMarathon(MARATHON_CONTEXT + service.getName(), "PATCH", "{ \"id\": \"/" + service.getName() + "\", \"instances\": 1}");
            JsonWrapper startResult = new JsonWrapper(startResultString);

            String deploymentId = startResult.getValueForPathAsString(DEPLOYMENT_ID_FIELD);
            if (StringUtils.isBlank(deploymentId)) {
                log.append("WARNING : Could not find any deployment ID when starting tasks for ").append(service.getName()).append("\n");
            } else {
                log.append("Tasks starting deployment ID for ").append(service.getName()).append(" is ").append(deploymentId).append("\n");
            }
        }

        return log.toString();
    }

    public void  stopServiceMarathon(Service service) throws MarathonException, SSHCommandException {
        ensureMarathonAvailability();
        systemService.applyServiceOperation(service.getName(), MARATHON_NODE, "Stopping", () -> stopServiceMarathonInternal(service));
    }

    String stopServiceMarathonInternal(Service service) throws MarathonException {

        StringBuilder log = new StringBuilder();

        Pair<String, String> nodeNameAndStatus = this.getAndWaitServiceRuntimeNode(service.getName(), 20); // 20 attempts
        String nodeIp = nodeNameAndStatus.getKey();

        boolean installed = StringUtils.isNotBlank(nodeIp);
        if (!installed) {
            log.append("Warning : Service ").append(service.getName()).append(" is not installed");

        } else {

            boolean running = nodeNameAndStatus.getValue().equals(RUNNING_STATUS);
            if (!running) {
                log.append("Info: Service ").append(service.getName()).append(" was not running");
            } else {

                // 1. Kill all tasks for service
                log.append("Killing tasks for ").append(service.getName()).append("\n");
                String killResultString = queryMarathon(MARATHON_CONTEXT + service.getName() + "/tasks?scale=true", "DELETE");
                JsonWrapper killResult = new JsonWrapper(killResultString);

                String deploymentId = killResult.getValueForPathAsString(DEPLOYMENT_ID_FIELD);
                if (StringUtils.isBlank(deploymentId)) {
                    log.append("WARNING : Could not find any deployment ID when killing tasks for ").append(service.getName());
                } else {
                    log.append("Tasks killing deployment ID for ").append(service.getName()).append(" is ").append(deploymentId);
                }
            }
        }

        return log.toString();
    }

    public void restartServiceMarathon(Service service) throws MarathonException, SSHCommandException {
        ensureMarathonAvailability();
        systemService.applyServiceOperation(service.getName(), MARATHON_NODE, "Stopping", () -> restartServiceMarathonInternal(service));
    }

    protected String restartServiceMarathonInternal(Service service) throws MarathonException {
        StringBuilder log = new StringBuilder();

        Pair<String, String> nodeNameAndStatus = this.getAndWaitServiceRuntimeNode(service.getName(), 60); // 60 attempts
        String nodeIp = nodeNameAndStatus.getKey();

        boolean installed = StringUtils.isNotBlank(nodeIp);
        if (!installed) {
            log.append("Warning : Service ").append(service.getName()).append(" is not installed");

        } else {

            log.append(stopServiceMarathonInternal(service));

            waitForServiceShutdown(service.getName());

            log.append("\n");
            log.append(startServiceMarathonInternal(service));
        }

        return log.toString();
    }

    protected void waitForServiceShutdown(String service) throws MarathonException {
        Pair<String, String> nodeNameAndStatus;

        int i;
        for (i = 0; i < MARATHON_UNINSTALL_SHUTDOWN_ATTEMPTS; i++) { // 200 attemots
            nodeNameAndStatus = this.getServiceRuntimeNode(service);

            boolean running = nodeNameAndStatus.getValue().equals(RUNNING_STATUS);
            if (!running) {
                break;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.debug(e.getMessage());
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            }
        }
        if (i == MARATHON_UNINSTALL_SHUTDOWN_ATTEMPTS) {
            throw new MarathonException("Could not stop service " + service + " in " + (MARATHON_UNINSTALL_SHUTDOWN_ATTEMPTS * 100 / 60) +  " seconds.");
        }
    }
}
