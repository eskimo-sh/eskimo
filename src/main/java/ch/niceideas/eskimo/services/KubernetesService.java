package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.utils.KubeStatusParser;
import com.trilead.ssh2.Connection;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class KubernetesService {

    private static final Logger logger = Logger.getLogger(KubernetesService.class);

    public static final String KUBE_MASTER = "kube-master";
    public static final String KUBE_NA_FLAG = "MARATHON_NA";
    public static final String TOPOLOGY_ALL_NODES = "Topology (All Nodes)";

    public static final String STATUS_RUNNING = "Running";
    public static final String STATUS_TERMINATING = "Terminating";
    public static final String STATUS_CONTAINER_CREATING = "ContainerCreating";

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

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

    // FIXME
    public void showJournal(Service service) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    // FIXME
    public void startService(Service service) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    // FIXME
    @PreAuthorize("hasAuthority('ADMIN')")
    public void stopService(Service service) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    // FIXME
    @PreAuthorize("hasAuthority('ADMIN')")
    public void restartService(Service service) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    // FIXME
    @PreAuthorize("hasAuthority('ADMIN')")
    public void applyKubernetesServicesConfig(MarathonOperationsCommand operationsCommand) {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    // FIXME
    @PreAuthorize("hasAuthority('ADMIN')")
    void installService(MarathonOperationsCommand.MarathonOperationId operation, String kubeMasterNode)
            throws SystemException {
        systemOperationService.applySystemOperation(operation,
                logger -> proceedWithKubernetesServiceInstallation(logger, kubeMasterNode, operation.getService()),
                status -> status.setInstallationFlag(operation.getService(), ServicesInstallStatusWrapper.KUBERNETES_NODE, "OK") );
    }

    @PreAuthorize("hasAuthority('ADMIN')")
    void uninstallService(MarathonOperationsCommand.MarathonOperationId operation, String kubeMasterNode) throws SystemException {
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
            // TODO This shold go through kube proxy, I might not need this nymore
            proxyManagerService.removeServerForService(operation.getService(), nodeIp);
        } else {
            logger.warn ("No previous IP could be found for service " + operation.getService());
        }
    }

    // FIXME
    private void proceedWithKubernetesServiceUninstallation(MessageLogger ml, String kubeMasterNode, String service)
            throws SSHCommandException, SystemException, KubernetesException {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    // FIXME
    protected String restartServiceKubernetesInternal(Service service) throws KubernetesException {
        throw new UnsupportedOperationException("To Be Implemented");
    }

    // FIXME
    void ensureKubernetesAvailability() throws KubernetesException {
        // TODO
        logger.warn("ensureKubernetesAvailability - To Be Implemented");
    }

    boolean shouldInstall(MarathonServicesConfigWrapper marathonConfig, String service) {
        if (marathonConfig != null) {
            return marathonConfig.isServiceInstallRequired(service);
        }
        return false;
    }

    public void fetchKubernetesServicesStatus
            (Map<String, String> statusMap, ServicesInstallStatusWrapper servicesInstallationStatus)
            throws KubernetesException {

        // 3.1 Node answers
        try {

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

            // FIYME change politics, get kubectl status all at once and then below instead of calling for every service,
            // get it from Kubectl result
            KubeStatusParser parser = getKubeStatusParser();

            MarathonServicesConfigWrapper marathonConfig = configurationService.loadMarathonServicesConfig();
            for (String service : servicesDefinition.listMarathonServices()) {

                // should service be installed on kubernetes ?
                boolean shall = this.shouldInstall(marathonConfig, service);

                Pair<String, String> nodeNameAndStatus = new Pair<>(KUBE_NA_FLAG, "NA");
                if (parser != null) {
                    nodeNameAndStatus = parser.getServiceRuntimeNode(servicesDefinition.getService(service), kubeMasterNode);
                }

                String nodeIp = nodeNameAndStatus.getKey();

                // if kubernetes is not answering, we assume service is still installed if it has been installed before
                // we identify it on marathon node then.
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
                if (!installed || !running || servicesDefinition.getService(service).isRegistryOnly()) {

                    // uninstalled services are identified on the marathon node
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

    @PreAuthorize("hasAuthority('ADMIN')")
    public void applyServicesConfig(MarathonOperationsCommand command) throws KubernetesException {

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

                        if (command.getUninstallations().size() > 0) {
                            logger.warn("Uninstalled Kubernetes services will be flagged as uninstalled even though no operation can be performed in kubernetes.");
                            configurationService.updateAndSaveServicesInstallationStatus(servicesInstallationStatus -> {
                                for (MarathonOperationsCommand.MarathonOperationId uninstalledMarathonService : command.getUninstallations()) {
                                    servicesInstallationStatus.removeInstallationFlag(uninstalledMarathonService.getService(), ServicesInstallStatusWrapper.KUBERNETES_NODE);
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

            NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();

            List<Pair<String, String>> nodesSetup = systemService.buildDeadIps(new HashSet<String>(){{add(kubeMasterNode);}}, nodesConfig, liveIps, deadIps);

            if (deadIps.contains(kubeMasterNode)) {
                notificationService.addError("The Kube Master node is dead. cannot proceed any further with installation.");
                String message = "The Kube Master node is dead. cannot proceed any further with installation. Kubernetes services configuration is saved but will need to be re-applied when k8s-master is available.";
                operationsMonitoringService.addGlobalInfo(message);
                throw new KubernetesException(message);
            }

            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            ensureKubernetesAvailability();

            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            if (nodesSetup == null) {
                return;
            }

            MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, deadIps);

            if (operationsMonitoringService.isInterrupted()) {
                return;
            }

            // Nodes re-setup (topology)
            systemOperationService.applySystemOperation(new MarathonOperationsCommand.MarathonOperationId("Installation", TOPOLOGY_ALL_NODES),
                    ml -> {
                        systemService.performPooledOperation (new ArrayList<>(liveIps), parallelismInstallThreadCount, baseInstallWaitTimout,
                                (operation, error) -> {
                                    // topology
                                    if (error.get() == null) {
                                        try {
                                            nodesConfigurationService.installTopologyAndSettings(nodesConfig, command.getRawConfig(), memoryModel, operation);
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

            /*
            // restarts
            for (List<Pair<String, String>> restarts : servicesInstallationSorter.orderOperations (command.getRestarts(), nodesConfig, deadIps)) {
                performPooledOperation(restarts, parallelismInstallThreadCount, kubernetesOperationWaitTimoutSeconds,
                        (operation, error) -> {
                            String service = operation.getKey();
                            String node = operation.getValue();
                            if (liveIps.contains(node)) {
                                restartServiceForSystem(service, node);
                            }
                        });
            }
            */

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

        Connection connection = null;
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

    private KubeStatusParser getKubeStatusParser() throws KubernetesException {

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

            return new KubeStatusParser(allPodStatus, allServicesStatus, registryServices);

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
