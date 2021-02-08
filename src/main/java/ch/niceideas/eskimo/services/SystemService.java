/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.utils.SystemStatusParser;
import com.trilead.ssh2.Connection;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class SystemService {

    private static final Logger logger = Logger.getLogger(SystemService.class);

    public static final String TMP_PATH_PREFIX = "/tmp/";

    public static final String SERVICE_PREFIX = "Service ";
    public static final String SHOULD_NOT_HAPPEN_FROM_HERE = " should not happen from here.";
    public static final String MARATHON_SERVICE_NAME = "marathon";

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private SetupService setupService;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private MarathonService marathonService;

    @Autowired
    private NodesConfigurationService nodesConfigurationService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Value("${system.failedServicesTriggerCount}")
    private int failedServicesTriggerCount = 5;

    @Value("${system.operationWaitTimoutSeconds}")
    private int operationWaitTimoutSeconds = 800; // ~ 13 minutes (for an individual step)

    @Value("${system.statusFetchThreadCount}")
    private int parallelismStatusThreadCount = 10;

    @Value("${system.packageDistributionPath}")
    private String packageDistributionPath = "./packages_distrib";

    @Value("${system.servicesSetupPath}")
    private String servicesSetupPath = "./services_setup";

    @Value("${system.statusUpdatePeriodSeconds}")
    private int statusUpdatePeriodSeconds = 5;

    private final ReentrantLock statusUpdateLock = new ReentrantLock();
    private final ScheduledExecutorService statusRefreshScheduler;
    private final AtomicReference<SystemStatusWrapper> lastStatus = new AtomicReference<>();
    private final AtomicReference<Exception> lastStatusException = new AtomicReference<>();

    private final Map<String, Integer> serviceMissingCounter = new ConcurrentHashMap<>();

    /**
     * for tests
     */
    void setSshCommandService(SSHCommandService sshCommandService) {
        this.sshCommandService = sshCommandService;
    }
    void setMessagingService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
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
    void setNodeRangeResolver (NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setMarathonService (MarathonService marathonService) {
        this.marathonService = marathonService;
    }
    void setNodesConfigurationService (NodesConfigurationService nodesConfigurationService) {
        this.nodesConfigurationService = nodesConfigurationService;
    }
    void setOperationsMonitoringService (OperationsMonitoringService operationsMonitoringService) {
        this.operationsMonitoringService = operationsMonitoringService;
    }

    // constructor for spring
    public SystemService() {
        this (true);
    }
    public SystemService(boolean createUpdateScheduler) {
        if (createUpdateScheduler) {

            // I shouldn't use a timer here since scheduling at fixed inteval may lead to flooding the system and ending
            // up in doing only this on large clusters

            statusRefreshScheduler = Executors.newSingleThreadScheduledExecutor();

            logger.info("Initializing Status updater scheduler ...");
            statusRefreshScheduler.schedule(this::updateStatus, statusUpdatePeriodSeconds, TimeUnit.SECONDS);
        } else {
            statusRefreshScheduler = null;
        }
    }

    @PreDestroy
    public void destroy() {
        logger.info ("Cancelling status updater scheduler");
        if (statusRefreshScheduler != null) {
            statusRefreshScheduler.shutdown();
        }
    }


    public void delegateApplyNodesConfig(OperationsCommand command)
            throws SystemException, ServiceDefinitionException, NodesConfigurationException {
        nodesConfigurationService.applyNodesConfig(command);
    }

    public void showJournal(String serviceName, String node) throws SSHCommandException, MarathonException {
        applyServiceOperation(serviceName, node, "Showing journal of", () -> {
            Service service = servicesDefinition.getService(serviceName);
            if (service.isMarathon()) {
                throw new UnsupportedOperationException("Showing marathon service journal for " + serviceName + SHOULD_NOT_HAPPEN_FROM_HERE);
            } else {
                return sshCommandService.runSSHCommand(node, "sudo journalctl -u " + serviceName);
            }
        });
    }

    public void startService(String serviceName, String node) throws SSHCommandException, MarathonException {
        applyServiceOperation(serviceName, node, "Starting", () -> {
            Service service = servicesDefinition.getService(serviceName);
            if (service.isMarathon()) {
                throw new UnsupportedOperationException("Starting marathon service " + serviceName + SHOULD_NOT_HAPPEN_FROM_HERE);
            } else {
                return sshCommandService.runSSHCommand(node, "sudo systemctl start " + serviceName);
            }
        });
    }

    public void stopService(String serviceName, String node) throws SSHCommandException, MarathonException {
        applyServiceOperation(serviceName, node, "Stopping", () -> {
            Service service = servicesDefinition.getService(serviceName);
            if (service.isMarathon()) {
                throw new UnsupportedOperationException("Stopping marathon service " + serviceName + SHOULD_NOT_HAPPEN_FROM_HERE);
            } else {
                return sshCommandService.runSSHCommand(node, "sudo systemctl stop " + serviceName);
            }
        });
    }

    public void restartService(String serviceName, String node) throws SSHCommandException, MarathonException {
        applyServiceOperation(serviceName, node, "Restarting", () -> {
            Service service = servicesDefinition.getService(serviceName);
            if (service.isMarathon()) {
                throw new UnsupportedOperationException("Restarting marathon service " + serviceName + SHOULD_NOT_HAPPEN_FROM_HERE);
            } else {
                return sshCommandService.runSSHCommand(node, "sudo systemctl restart " + serviceName);
            }
        });
    }

    public void callCommand(String commandId, String serviceName, String node) throws SSHCommandException, MarathonException {
        applyServiceOperation(serviceName, node, "Calling command " + commandId , () -> {
            Service service = servicesDefinition.getService(serviceName);

            Command command = service.getCommand (commandId);
            if (command == null) {
                throw new SSHCommandException("Command " + commandId + " is unknown for service " + serviceName);
            }

            return command.call (node, sshCommandService);
        });
    }

    private void logOperationMessage(String operation) {
        messagingService.addLines(new String[]{
                "\n" + operation
        });
    }

    void applyServiceOperation(String service, String node, String opLabel, ServiceOperation<String> operation) throws SSHCommandException, MarathonException {
        String message = opLabel + " " + service + " on " + node;
        boolean success = false;
        operationsMonitoringService.operationsStarted(new SimpleOperation (message));
        try {

            notificationService.addDoing(opLabel + " " + service + " on " + node);
            logOperationMessage (message);
            messagingService.addLines("Done "
                    + message
                    + "\n-------------------------------------------------------------------------------\n"
                    + operation.call());
            notificationService.addInfo(opLabel + " " + service + " succeeded on " + node);

            success = true;
        } finally {
            operationsMonitoringService.operationsFinished(success);
        }
    }

    public SystemStatusWrapper getStatus() throws StatusExceptionWrapperException {

        // special case at application startup : if the UI request comes before the first status update
        if (lastStatusException.get() == null && lastStatus.get() == null) {
            return new SystemStatusWrapper("{ \"clear\" : \"initializing\"}");
        }

        if (lastStatusException.get() != null) {
            throw new StatusExceptionWrapperException (lastStatusException.get());
        }
        return lastStatus.get();
    }

    void setLastStatusForTest(SystemStatusWrapper lastStatusForTest) {
        this.lastStatus.set (lastStatusForTest);
    }

    public void updateStatus() {

        try {
            statusUpdateLock.lock();

            // 0. Build returned status
            SystemStatusWrapper systemStatus = SystemStatusWrapper.empty();

            // 1. Load Node Config
            NodesConfigWrapper rawNodesConfig = configurationService.loadNodesConfig();

            // 1.1. Load Node status
            ServicesInstallStatusWrapper servicesInstallationStatus = configurationService.loadServicesInstallationStatus();

            // 1.2 flag services needing restart
            if (rawNodesConfig != null && !rawNodesConfig.isEmpty()) {

                NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

                // 2. Build merged status
                final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();
                final ExecutorService threadPool = Executors.newFixedThreadPool(parallelismStatusThreadCount);

                for (Pair<String, String> nbrAndPair : nodesConfig.getNodeAdresses()) {

                    int nodeNbr = Integer.parseInt(nbrAndPair.getKey());
                    String node = nbrAndPair.getValue();
                    String nodeName = node.replace(".", "-");

                    statusMap.put(("node_nbr_" + nodeName), "" + nodeNbr);
                    statusMap.put(("node_address_" + nodeName), node);

                    threadPool.execute(() -> {
                        try {
                            fetchNodeStatus(nodesConfig, statusMap, nbrAndPair, servicesInstallationStatus);
                        } catch (SystemException e) {
                            logger.error(e, e);
                            throw new PooledOperationException(e);
                        }
                    });
                }

                threadPool.shutdown();
                try {
                    threadPool.awaitTermination(operationWaitTimoutSeconds, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.error(e, e);
                }

                // fetch marathon services status
                try {
                    marathonService.fetchMarathonServicesStatus(statusMap, servicesInstallationStatus);
                } catch (MarathonException e) {
                    logger.debug(e, e);
                    // workaround : flag all marathon services as KO on marathon node
                    String marathonNode = servicesInstallationStatus.getFirstNode(MARATHON_SERVICE_NAME);
                    if (StringUtils.isNotBlank(marathonNode)) {
                        String marathonNodeName = marathonNode.replace(".", "-");
                        MarathonServicesConfigWrapper marathonConfig = configurationService.loadMarathonServicesConfig();
                        for (String service : servicesDefinition.listMarathonServices()) {
                            if (marathonService.shouldInstall(marathonConfig, service)) {
                                statusMap.put(SystemStatusWrapper.SERVICE_PREFIX + service + "_" + marathonNodeName, "KO");
                            }
                        }
                    }
                }

                // fill in systemStatus
                for (String key : statusMap.keySet()) {
                    String value = statusMap.get(key);
                    systemStatus.setValueForPath(key, value);
                }
            }

            // 4. If a service disappeared, post notification
            try {
                checkServiceDisappearance(systemStatus);
            } catch (JSONException e) {
                logger.warn(e, e);
            }

            // 5. Handle status update if a service seem to have disappeared

            // 5.1 Test if any additional node should be check for being live
            Set<String> systemStatusNodes = systemStatus.getNodes();
            Set<String> additionalIpToTests = servicesInstallationStatus.getNodes().stream()
                    .filter(ip -> !systemStatusNodes.contains(ip))
                    .collect(Collectors.toSet());

            Set<String> configuredNodesAndOtherLiveNodes = new HashSet<>(systemStatusNodes);
            for (String node : additionalIpToTests) {

                // find out if SSH connection to host can succeed
                try {
                    String ping = sendPing(node);

                    if (ping.startsWith("OK")) {
                        configuredNodesAndOtherLiveNodes.add(node);
                    }
                } catch (SSHCommandException e) {
                    logger.debug(e, e);
                }
            }

            handleStatusChanges(servicesInstallationStatus, systemStatus, configuredNodesAndOtherLiveNodes);

            lastStatus.set (systemStatus);
            lastStatusException.set (null);

        } catch (Exception e) {

            logger.error (e, e);

            lastStatusException.set (e);
            lastStatus.set (null);

        } finally {
            statusUpdateLock.unlock();
            // reschedule
            if (statusRefreshScheduler != null) {
                statusRefreshScheduler.schedule(this::updateStatus, statusUpdatePeriodSeconds, TimeUnit.SECONDS);
            }
        }
    }

    String sendPing(String node) throws SSHCommandException {
        return sshCommandService.runSSHScript(node, "echo OK", false);
    }

    <T> void performPooledOperation(
            List<T> operations, int parallelism, long operationWaitTimout, PooledOperation<T> operation)
            throws SystemException {

        final ExecutorService threadPool = Executors.newFixedThreadPool(parallelism);
        final AtomicReference<Exception> error = new AtomicReference<>();

        for (T opToPerform : operations) {

            if (!operationsMonitoringService.isInterrupted()) {
                threadPool.execute(() -> {

                    if (!operationsMonitoringService.isInterrupted() && (error.get() == null)) {

                        try {
                            operation.call(opToPerform, error);
                        } catch (Exception e) {
                            logger.error(e, e);
                            logger.warn ("Storing error - " + e.getClass()+":"+e.getMessage());
                            error.set(e);
                            // actually killing the thread is perhaps not a good idea
                            //throw new PooledOperationException(e.getMessage());
                        }
                    }
                });
            }
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(operationWaitTimout, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.debug (e, e);
        }

        if (error.get() != null) {
            logger.warn ("Throwing " + error.get().getClass() + ":" + error.get().getMessage() + " as SystemException") ;
            throw new SystemException(error.get().getMessage(), error.get());
        }
    }

    void fetchNodeStatus
            (NodesConfigWrapper nodesConfig, Map<String, String> statusMap, Pair<String, String> nbrAndPair,
             ServicesInstallStatusWrapper servicesInstallationStatus)
                throws SystemException {

        int nodeNbr = Integer.parseInt(nbrAndPair.getKey());
        String node = nbrAndPair.getValue();
        String nodeName = node.replace(".", "-");

        // 3.1 Node answers
        try {

            // find out if SSH connection to host can succeeed
            String ping = null;
            try {
                ping = sendPing(node);
            } catch (SSHCommandException e) {
                logger.warn(e.getMessage());
                logger.debug(e, e);
            }

            if (StringUtils.isBlank(ping) || !ping.startsWith("OK")) {

                statusMap.put(("node_alive_" + nodeName), "KO");

            } else {

                statusMap.put(("node_alive_" + nodeName), "OK");

                String allServicesStatus = sshCommandService.runSSHScript(node,
                        "sudo systemctl status --no-pager --no-block -al " + servicesDefinition.getAllServicesString() + " 2>/dev/null ", false);

                SystemStatusParser parser = new SystemStatusParser(allServicesStatus);

                for (String service : servicesDefinition.listAllNodesServices()) {

                    // should service be installed on node ?
                    boolean shall = nodesConfig.shouldInstall (service, nodeNbr);

                    // check if service is installed ?
                    // check if service installed using SSH
                    String serviceStatus = parser.getServiceStatus(service);
                    boolean installed = !serviceStatus.equals("NA");
                    boolean running = serviceStatus.equals("running");

                    feedInServiceStatus (
                            statusMap, servicesInstallationStatus, node, nodeName, nodeName,
                            service, shall, installed, running);
                }
            }
        } catch (SSHCommandException | JSONException | ConnectionManagerException e) {
            logger.error(e, e);
            throw new SystemException(e.getMessage(), e);
        }
    }

    void feedInServiceStatus (
            Map<String, String> statusMap,
            ServicesInstallStatusWrapper servicesInstallationStatus,
            String node,
            String nodeName,
            String referenceNodeName,
            String service,
            boolean shall,
            boolean installed,
            boolean running) throws ConnectionManagerException {

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

                    // configure proxy if required
                    proxyManagerService.updateServerForService(service, node);
                }
            }
        } else {
            if (installed) {
                statusMap.put(SystemStatusWrapper.buildStatusFlag (service, nodeName), "TD"); // To Be Deleted
            }
        }
    }

    public void handleStatusChanges(
            ServicesInstallStatusWrapper servicesInstallationStatus, SystemStatusWrapper systemStatus,
            Set<String> configuredNodesAndOtherLiveNodes)
                throws FileException, SetupException {

        // If there is some processing pending, then nothing is reliable, just move on
        if (!operationsMonitoringService.isProcessingPending()) {

            try {

                boolean changes = false;

                for (Pair<String, String> installationPairs : servicesInstallationStatus.getAllServiceAndNodeNameInstallationPairs()) {

                    String savedService = installationPairs.getKey();
                    String nodeName = installationPairs.getValue();
                    String originalNodeName = nodeName;

                    // if service is a marathon service
                    if (nodeName.equals(ServicesInstallStatusWrapper.MARATHON_NODE)) {

                        // if marathon is not available, don't do anything
                        String marathonNodeName = systemStatus.getFirstNodeName(MARATHON_SERVICE_NAME);
                        if (StringUtils.isBlank(marathonNodeName)) { // if marathon is not found, don't touch anything. Let's wait for it to come back.
                            //notificationService.addError("Marathon inconsistency.");
                            //logger.warn("Marathon could not be found - not potentially flagging marathon services as disappeared as long as marathon is not back.");
                            continue;
                        }

                        if (!systemStatus.isServiceOKOnNode(MARATHON_SERVICE_NAME, marathonNodeName)) {
                            //logger.warn("Marathon is not OK - not potentially flagging marathon services as disappeared as long as marathon is not back.");
                            continue;
                        }

                        // get first node actually running service
                        nodeName = systemStatus.getFirstNodeName(savedService);
                        if (StringUtils.isBlank(nodeName)) {
                            // if none, consider marathon node as DEFAULT node running service
                            nodeName = marathonNodeName;
                        }
                    }

                    String nodeIp = nodeName.replace("-", ".");

                    Boolean nodeAlive = StringUtils.isNotBlank(nodeName) ? systemStatus.isNodeAlive (nodeName) : Boolean.FALSE;

                    // A. In case target node both configured and up, check services actual statuses before doing anything
                    if (    // nodes is configured and responding (up and running

                            nodeAlive != null && nodeAlive
                            ) {

                        if (handleRemoveServiceIfDown(servicesInstallationStatus, systemStatus, savedService, nodeName, originalNodeName)) {
                            changes = true;
                        }
                    }

                    // B. node is not configured anymore (has been removed, but it is still up and responding and it runs services)
                    //    in this case we want to attempt uninstallation, thus not removing services if they are up
                    // => so nothing to do, don't touch anything in installed services registry

                    // c. if node is both down and not configured anymore, we just remove all services whatever their statuses
                    if (!configuredNodesAndOtherLiveNodes.contains(nodeIp)
                            && countErrorAndRemoveServices(servicesInstallationStatus, savedService, nodeName, originalNodeName)) {
                        changes = true;
                    }

                    // D. In other cases, node is configured but down. We don't make any assumption on node down.
                    //    Admin is left with uninstalling it if he wants.
                    // => so nothing to do, don't touch anything in installed services registry
                }

                if (changes) {
                    configurationService.saveServicesInstallationStatus(servicesInstallationStatus);
                }
            } catch (JSONException e) {
                logger.error(e, e);
                // this is no mission critical method, let's silent errors there
            }
        }
    }

    boolean handleRemoveServiceIfDown(
            ServicesInstallStatusWrapper servicesInstallStatus, SystemStatusWrapper systemStatusWrapper,
            String savedService, String nodeName, String originalNodeName) {

        boolean changes = false;

        if (systemStatusWrapper.isServiceAvailableOnNode(savedService, nodeName)) {
            serviceMissingCounter.remove(savedService + "-" + nodeName);

        } else {
            if (countErrorAndRemoveServices(servicesInstallStatus, savedService, nodeName, originalNodeName)) {
                changes = true;
            }
        }
        return changes;
    }

    boolean countErrorAndRemoveServices(
            ServicesInstallStatusWrapper servicesInstallationStatus,
            String savedService, String nodeName, String originalNodeName) {
        boolean changes = false;
        // otherwise count error
        Integer counter = serviceMissingCounter.get(savedService + "-" + nodeName);
        if (counter == null) {
            counter = 0;
            serviceMissingCounter.put(savedService + "-" + nodeName, counter);

        } else {

            counter = counter + 1;

            // if error count > 2 (i.e. 3), consider service uninstalled, remove it from saved status
            if (counter > failedServicesTriggerCount) {

                servicesInstallationStatus.removeInstallationFlag(savedService, originalNodeName);
                serviceMissingCounter.remove(savedService + "-" + nodeName);
                notificationService.addError(SERVICE_PREFIX + savedService + " on " + nodeName + " vanished!");
                logger.warn (SERVICE_PREFIX + savedService + " on " + nodeName + " has been removed from ServiceInstallationStatus!");

                // unconfigure proxy if required
                proxyManagerService.removeServerForService(savedService, nodeName.replace("-", "."));

                changes = true;

            } else {
                serviceMissingCounter.put(savedService + "-" + nodeName, counter);
            }
        }
        return changes;
    }

    void checkServiceDisappearance(SystemStatusWrapper systemStatus) {

        if (lastStatus.get() != null) {

            for (String serviceStatusFlag : systemStatus.getRootKeys()) {

                // if service is currently not OK but was previously OK
                if (!systemStatus.isServiceStatusFlagOK(serviceStatusFlag) && lastStatus.get().isServiceStatusFlagOK(serviceStatusFlag)) {

                    logger.warn("For service " + serviceStatusFlag + " - previous status was OK and status is " + systemStatus.getValueForPath(serviceStatusFlag));
                    notificationService.addError(SERVICE_PREFIX + SystemStatusWrapper.getServiceName(serviceStatusFlag)
                            + " on " +  Objects.requireNonNull(SystemStatusWrapper.getNodeName(serviceStatusFlag)).replace("-", ".")
                            + " got into problem");
                }
            }
        }
    }

    void callUninstallScript(StringBuilder sb, Connection connection, String service) throws SystemException {
        File containerFolder = new File(servicesSetupPath + "/" + service);
        if (!containerFolder.exists()) {
            throw new SystemException("Folder " + servicesSetupPath + "/" + service + " doesn't exist !");
        }

        try {
            File uninstallScriptFile = new File(containerFolder, "uninstall.sh");
            if (uninstallScriptFile.exists()) {
                sb.append(" - Calling uninstall script\n");

                sb.append(sshCommandService.runSSHScriptPath(connection, uninstallScriptFile.getAbsolutePath()));
            }
        } catch (SSHCommandException e) {
            logger.warn (e, e);
            sb.append (e.getMessage());
        }
    }

    void installationSetup(StringBuilder sb, Connection connection, String node, String service) throws SystemException {
        try {
            exec(connection, sb, new String[]{"bash", TMP_PATH_PREFIX + service + "/setup.sh", node});
        } catch (SSHCommandException e) {
            logger.debug (e, e);
            sb.append(e.getMessage());
            throw new SystemException ("Setup.sh script execution for " + service + " on node " + node + " failed.", e);
        }
    }

    void installationCleanup(StringBuilder sb, Connection connection, String service, String imageName, File tmpArchiveFile) throws SSHCommandException, SystemException {
        exec(connection, sb, "rm -Rf " + TMP_PATH_PREFIX + service);
        exec(connection, sb, "rm -f " + TMP_PATH_PREFIX + service + ".tgz");

        if (StringUtils.isNotBlank(imageName)) {
            try {
                sb.append(" - Deleting docker template image");
                exec(connection, new StringBuilder(), "docker image rm eskimo:" + imageName + "_template");
            } catch (SSHCommandException e) {
                logger.error(e, e);
                sb.append(e.getMessage());
                // ignroed any further
            }
        }

        try {
            FileUtils.delete (new File (TMP_PATH_PREFIX + tmpArchiveFile.getName() + ".tgz"));
        } catch (FileUtils.FileDeleteFailedException e) {
            logger.error (e, e);
            throw new SystemException(e);
        }
    }

    void exec(Connection connection, StringBuilder sb, String[] setupScript) throws SSHCommandException {
        sb.append(sshCommandService.runSSHCommand(connection, setupScript));
    }

    void exec(Connection connection, StringBuilder sb, String command) throws SSHCommandException {
        sb.append(sshCommandService.runSSHCommand(connection, command));
    }

    List<Pair<String, String>> buildDeadIps(Set<String> allNodes, NodesConfigWrapper nodesConfig, Set<String> liveIps, Set<String> deadIps) {
        List<Pair<String, String>> nodesSetup = new ArrayList<>();

        // Find out about dead IPs
        Set<String> nodesToTest = new HashSet<>(allNodes);
        nodesToTest.addAll(nodesConfig.getNodeAddresses());
        for (String node : nodesToTest) {

            if (!node.equals(OperationsCommand.MARATHON_FLAG)) {

                // handle potential interruption request
                if (operationsMonitoringService.isInterrupted()) {
                    return null;
                }

                nodesSetup.add(new Pair<>("node_setup", node));

                // Ping IP to make sure it is available, report problem with IP if it is not ad move to next one

                // find out if SSH connection to host can succeed
                try {
                    String ping = sendPing(node);

                    if (!ping.startsWith("OK")) {

                        handleNodeDead(deadIps, node);
                    } else {
                        liveIps.add(node);
                    }

                    // Ensure sudo is possible
                    checkSudoPossible (deadIps, node);

                } catch (SSHCommandException e) {
                    logger.debug(e, e);
                    handleSSHFails(deadIps, node);
                }
            }
        }

        if (!deadIps.isEmpty()) {
            messagingService.addLines("\n");
        }
        return nodesSetup;
    }

    private void checkSudoPossible(Set<String> deadIps, String node) throws SSHCommandException {

        String result = sshCommandService.runSSHScript(node, "sudo ls", false);
        if (   result.contains("a terminal is required to read the password")
            || result.contains("a password is required")) {
            messagingService.addLines("\nNode " + node + " doesn't enable the configured user to use sudo without password. Installing cannot continue.");
            notificationService.addError("Node " + node + " sudo problem");
            deadIps.add(node);
        }

    }

    void handleNodeDead(Set<String> deadIps, String node) {
        messagingService.addLines("\nNode seems dead " + node);
        notificationService.addError("Node " + node + " is dead.");
        deadIps.add(node);
    }

    void handleSSHFails(Set<String> deadIps, String node) {
        messagingService.addLines("\nNode " + node + " couldn't be joined through SSH\nIs the user to be used by eskimo properly created and the public key properly added to SSH authorized keys ? (See User Guide)");
        notificationService.addError("Node " + node + " not reachable.");
        deadIps.add(node);
    }

    File createRemotePackageFolder(StringBuilder sb, Connection connection, String node, String service, String imageName) throws SystemException, IOException, SSHCommandException {
        // 1. Find container folder, archive and copy there

        // 1.1 Make sure folder exist
        File containerFolder = new File(servicesSetupPath + "/" + service);
        if (!containerFolder.exists()) {
            throw new SystemException("Folder " + servicesSetupPath + "/" + service + " doesn't exist !");
        }

        // 1.2 Create archive

        // Get the temporary directory and print it.
        String tempDir = System.getProperty("java.io.tmpdir");
        if (StringUtils.isBlank(tempDir)) {
            throw new SystemException("Unable to get system temporary directory.");
        }
        File tmpArchiveFile = createTempFile(service, node, ".tgz");
        Files.delete(tmpArchiveFile.toPath());

        File archive = new File(tempDir + "/" + tmpArchiveFile.getName());
        FileUtils.createTarFile(servicesSetupPath + "/" + service, archive);
        if (!archive.exists()) {
            throw new SystemException("Could not create archive for service " + service + " : " + SystemService.TMP_PATH_PREFIX +  tmpArchiveFile.getName());
        }

        // 2. copy it over to target node and extract it

        // 2.1
        sshCommandService.copySCPFile(connection, archive.getAbsolutePath());

        exec(connection, sb, "rm -Rf " +SystemService. TMP_PATH_PREFIX + service);
        exec(connection, sb, "rm -f " + SystemService.TMP_PATH_PREFIX + service + ".tgz");
        exec(connection, sb, "mv " +  tmpArchiveFile.getName() + " " + SystemService.TMP_PATH_PREFIX + service + ".tgz");
        exec(connection, sb, "tar xfz " + SystemService.TMP_PATH_PREFIX + service + ".tgz --directory=" + SystemService.TMP_PATH_PREFIX);
        exec(connection, sb, "chmod 755 " + SystemService.TMP_PATH_PREFIX + service + "/setup.sh");

        // 2.2 delete local archive
        try {
            FileUtils.delete(archive);
        } catch (FileUtils.FileDeleteFailedException e) {
            logger.error(e, e);
            throw new SystemException("Could not delete archive /tmp/" + service + ".tgz");
        }

        // 3. Copy container image there if any
        if (StringUtils.isNotBlank(imageName)) {
            String imageFileName = setupService.findLastPackageFile(SetupService.DOCKER_TEMPLATE_PREFIX, imageName);

            File containerFile = new File(packageDistributionPath + "/" + imageFileName);
            if (containerFile.exists()) {

                sb.append(" - Copying over docker image ").append(imageFileName).append("\n");
                sshCommandService.copySCPFile(connection, packageDistributionPath + "/" + imageFileName);

                exec(connection, sb, new String[]{"mv", imageFileName, SystemService.TMP_PATH_PREFIX + service + "/"});

                exec(connection, sb, new String[]{"mv",
                        SystemService.TMP_PATH_PREFIX + service + "/" + imageFileName,
                        SystemService.TMP_PATH_PREFIX + service + "/" + SetupService.DOCKER_TEMPLATE_PREFIX + imageName + ".tar.gz"});

            } else {
                sb.append(" - (no container found for ").append(service).append(" - will just invoke setup)");
            }
        }
        return tmpArchiveFile;
    }

    File createTempFile(String service, String node, String extension) throws IOException {
        return File.createTempFile(service, extension);
    }


    interface PooledOperation<T> {
        void call(T operation, AtomicReference<Exception> error)
                throws SystemException;
    }

    interface ServiceOperation<V> {
        V call() throws SSHCommandException, MarathonException;
    }

    interface StatusUpdater {
        void updateStatus (ServicesInstallStatusWrapper servicesInstallationStatus);
    }

    public static class PooledOperationException extends RuntimeException {

        static final long serialVersionUID = -3317632123352229248L;

        PooledOperationException(String message) {
            super(message);
        }

        PooledOperationException(Throwable cause) {
            super(cause);
        }
    }

    public static class StatusExceptionWrapperException extends Exception {

        static final long serialVersionUID = -3317632123352221248L;

        StatusExceptionWrapperException(Exception cause) {
            super(cause);
        }

    }
}
