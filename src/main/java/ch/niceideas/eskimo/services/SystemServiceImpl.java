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
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.utils.SystemStatusParser;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile({"!test-system & !system-under-test"})
public class SystemServiceImpl implements SystemService {

    private static final Logger logger = Logger.getLogger(SystemServiceImpl.class);

    public static final String TMP_PATH_PREFIX = "/tmp/";

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private SetupService setupService;

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
    private KubernetesService kubernetesService;

    @Autowired
    private NodesConfigurationService nodesConfigurationService;

    @Autowired
    private OperationsMonitoringService operationsMonitoringService;

    @Value("${system.failedServicesTriggerCount}")
    private int failedServicesTriggerCount = 5;

    @Value("${connectionManager.statusOperationTimeout}")
    private int statusOperationTimeout = 60000; // ~ 13 minutes (for an individual step)

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
    protected final AtomicReference<SystemStatusWrapper> lastStatus = new AtomicReference<>();
    protected final AtomicReference<Exception> lastStatusException = new AtomicReference<>();

    private final Map<String, Integer> serviceMissingCounter = new ConcurrentHashMap<>();

    // constructor for spring
    public SystemServiceImpl() {
        this (true);
    }
    public SystemServiceImpl(boolean createUpdateScheduler) {
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

    @Override
    public void delegateApplyNodesConfig(ServiceOperationsCommand command)
            throws NodesConfigurationException {
        nodesConfigurationService.applyNodesConfig(command);
    }

    @Override
    public void showJournal(Service service, String node) throws SystemException {
        applyServiceOperation(service.getName(), node, "Showing journal of", () -> {
            if (service.isKubernetes()) {
                throw new UnsupportedOperationException("Showing kubernetes service journal for " + service.getName() + SHOULD_NOT_HAPPEN_FROM_HERE);
            } else {
                return sshCommandService.runSSHCommand(node, "sudo journalctl -u " + service.getName());
            }
        });
    }

    @Override
    public void startService(Service service, String node) throws SystemException {
        applyServiceOperation(service.getName(), node, "Starting", () -> {
            if (service.isKubernetes()) {
                throw new UnsupportedOperationException("Starting kubernetes service " + service.getName() + SHOULD_NOT_HAPPEN_FROM_HERE);
            } else {
                return sshCommandService.runSSHCommand(node, "sudo systemctl start " + service.getName());
            }
        });
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void stopService(Service service, String node) throws SystemException{
        applyServiceOperation(service.getName(), node, "Stopping", () -> {
            if (service.isKubernetes()) {
                throw new UnsupportedOperationException("Stopping kubernetes service " + service.getName() + SHOULD_NOT_HAPPEN_FROM_HERE);
            } else {
                return sshCommandService.runSSHCommand(node, "sudo systemctl stop " + service.getName());
            }
        });
    }

    @Override
    @PreAuthorize("hasAuthority('ADMIN')")
    public void restartService(Service service, String node) throws SystemException {
        applyServiceOperation(service.getName(), node, "Restarting", () -> {
            if (service.isKubernetes()) {
                throw new UnsupportedOperationException("Restarting kubernetes service " + service.getName() + SHOULD_NOT_HAPPEN_FROM_HERE);
            } else {
                return sshCommandService.runSSHCommand(node, "sudo systemctl restart " + service.getName());
            }
        });
    }

    @Override
    public void callCommand(String commandId, String serviceName, String node) throws SystemException {
        applyServiceOperation(serviceName, node, "Calling command " + commandId , () -> {
            Service service = servicesDefinition.getService(serviceName);

            Command command = service.getCommand (commandId);
            if (command == null) {
                throw new SSHCommandException("Command " + commandId + " is unknown for service " + serviceName);
            }

            return command.call (node, sshCommandService);
        });
    }

    private void logOperationMessage(OperationId operationId, String operation) {
        operationsMonitoringService.addInfo(operationId, new String[]{
                "\n" + operation
        });
    }

    @Override
    public void applyServiceOperation(String service, String node, String opLabel, ServiceOperation<String> operation) throws SystemException {
        String message = opLabel + " " + service + " on " + node;
        boolean success = false;

        SimpleOperationCommand.SimpleOperationId operationId = new SimpleOperationCommand.SimpleOperationId(opLabel, service, node);

        try {

            operationsMonitoringService.startCommand(new SimpleOperationCommand(opLabel, service, node));

            operationsMonitoringService.startOperation(operationId);

            notificationService.addDoing(opLabel + " " + service + " on " + node);
            logOperationMessage(operationId, message);

            operationsMonitoringService.addInfo(operationId, "Done "
                    + message
                    + "\n-------------------------------------------------------------------------------\n"
                    + operation.call());
            notificationService.addInfo(opLabel + " " + service + " succeeded on " + node);

            success = true;
        } catch (SSHCommandException | KubernetesException | ServiceDefinitionException | NodesConfigurationException e) {

            operationsMonitoringService.addInfo(operationId, "\nDone : "
                    + message
                    + "\n-------------------------------------------------------------------------------\n"
                    + "--> Completed in error : "
                    + e.getMessage());

            notificationService.addError(message + " failed !");
            operationsMonitoringService.endOperationError(operationId);
            throw new SystemException(e.getMessage(), e);

        } finally {

            operationsMonitoringService.endOperation(operationId);

            operationsMonitoringService.endCommand(success);
        }
    }

    @Override
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

    @Override
    public void updateStatus() {

        if (statusUpdateLock.isLocked()) {
            logger.warn ("!!! NOT UPDATING STATUS SINCE STATUS UPDATED IS RUNNING ALREADY !!!");
            return;
        }

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
                    if (!threadPool.awaitTermination(statusOperationTimeout, TimeUnit.MILLISECONDS)) {
                        logger.warn ("Status operation fetching ended up in timeout.");
                    }
                } catch (InterruptedException e) {
                    logger.error(e, e);
                }

                // fetch kubernetes services status
                try {
                    kubernetesService.fetchKubernetesServicesStatus(statusMap, servicesInstallationStatus);
                } catch (KubernetesException e) {
                    logger.debug(e, e);
                    // workaround : flag all Kubernetes services as KO on kube node
                    String kubeNode = servicesInstallationStatus.getFirstNode(servicesDefinition.getKubeMasterService().getName());
                    if (StringUtils.isNotBlank(kubeNode)) {
                        String kubeNodeName = kubeNode.replace(".", "-");
                        KubernetesServicesConfigWrapper kubeServicesConfig = configurationService.loadKubernetesServicesConfig();
                        for (String service : servicesDefinition.listKubernetesServices()) {
                            if (kubernetesService.shouldInstall(kubeServicesConfig, service)) {
                                statusMap.put(SystemStatusWrapper.SERVICE_PREFIX + service + "_" + kubeNodeName, "KO");
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

    @Override
    public String sendPing(String node) throws SSHCommandException {
        return sshCommandService.runSSHScript(node, "echo OK", false);
    }

    @Override
    public <T extends Serializable> void performPooledOperation(
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
                            logger.warn("Storing error - " + e.getClass() + ":" + e.getMessage());
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
            if (!threadPool.awaitTermination(operationWaitTimout, TimeUnit.SECONDS)) {
                logger.warn ("Could not complete operation within " + operationWaitTimout + " seconds");
            }
        } catch (InterruptedException e) {
            logger.debug (e, e);
        }

        if (error.get() != null) {
            logger.warn ("Throwing " + error.get().getClass() + ":" + error.get().getMessage() + " as SystemException") ;
            throw new SystemException(error.get().getMessage(), error.get());
        }
    }

    protected void fetchNodeStatus
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
                    boolean running = serviceStatus.equalsIgnoreCase("running");

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

    @Override
    public void feedInServiceStatus (
            Map<String, String> statusMap,
            ServicesInstallStatusWrapper servicesInstallationStatus,
            String node,
            String nodeName,
            String referenceNodeName,
            String service,
            boolean shall,
            boolean installed,
            boolean running) throws ConnectionManagerException {

        if (StringUtils.isBlank(nodeName)) {
            throw new IllegalArgumentException("nodeName can't be null");
        }
        if (StringUtils.isBlank(service)) {
            throw new IllegalArgumentException("service can't be null");
        }

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

    @Override
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

                    // if service is a kubernetes service
                    if (nodeName.equals(ServicesInstallStatusWrapper.KUBERNETES_NODE)) {

                        // if kubernetes is not available, don't do anything
                        String kubeNodeName = systemStatus.getFirstNodeName(servicesDefinition.getKubeMasterService().getName());
                        if (StringUtils.isBlank(kubeNodeName)) { // if Kubernetes is not found, don't touch anything. Let's wait for it to come back.
                            //notificationService.addError("Kubernetes inconsistency.");
                            //logger.warn("Kubernetes could not be found - not potentially flagging kubernetes services as disappeared as long as kubernetes is not back.");
                            continue;
                        }

                        if (!systemStatus.isServiceOKOnNode(servicesDefinition.getKubeMasterService().getName(), kubeNodeName)) {
                            //logger.warn("Kubernetes is not OK - not potentially flagging kubernetes services as disappeared as long as kubernetes is not back.");

                            // reset missing counter on kubernetes services when kube is down
                            configuredNodesAndOtherLiveNodes.forEach(
                                    nodeIp -> {
                                        String effNodeName = nodeIp.replace(".", "-");
                                        new ArrayList<>(serviceMissingCounter.keySet()).stream()
                                                .filter(flag -> flag.equals(savedService + "-" + effNodeName))
                                                .forEach(serviceMissingCounter::remove);
                                    }
                            );
                            continue;
                        }

                        // get first node actually running service
                        nodeName = systemStatus.getFirstNodeName(savedService);
                        if (StringUtils.isBlank(nodeName)) {
                            // if none, consider kubernetes node as DEFAULT node running service
                            nodeName = kubeNodeName;
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

    protected void checkServiceDisappearance(SystemStatusWrapper systemStatus) {

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

    @Override
    public void callUninstallScript(MessageLogger ml, SSHConnection connection, String service) throws SystemException {
        File containerFolder = new File(servicesSetupPath + "/" + service);
        if (!containerFolder.exists()) {
            throw new SystemException("Folder " + servicesSetupPath + "/" + service + " doesn't exist !");
        }

        try {
            File uninstallScriptFile = new File(containerFolder, "uninstall.sh");
            if (uninstallScriptFile.exists()) {
                ml.addInfo(" - Calling uninstall script");

                ml.addInfo(sshCommandService.runSSHScriptPath(connection, uninstallScriptFile.getAbsolutePath()));
            }
        } catch (SSHCommandException e) {
            logger.warn (e, e);
            ml.addInfo (e.getMessage());
        }
    }

    @Override
    public void installationSetup(MessageLogger ml, SSHConnection connection, String node, String service) throws SystemException {
        try {
            exec(connection, ml, new String[]{"bash", TMP_PATH_PREFIX + service + "/setup.sh", node});
        } catch (SSHCommandException e) {
            logger.debug (e, e);
            ml.addInfo(e.getMessage());
            throw new SystemException ("Setup.sh script execution for " + service + " on node " + node + " failed.", e);
        }
    }

    @Override
    public void installationCleanup(MessageLogger ml, SSHConnection connection, String service, String imageName, File tmpArchiveFile) throws SSHCommandException, SystemException {
        exec(connection, ml, "rm -Rf " + TMP_PATH_PREFIX + service);
        exec(connection, ml, "rm -f " + TMP_PATH_PREFIX + service + ".tgz");

        if (StringUtils.isNotBlank(imageName)) {
            try {
                ml.addInfo(" - Deleting docker template image");
                exec(connection, new MessageLogger() {
                    @Override
                    public void addInfo(String message) {
                        // ignored
                    }

                    @Override
                    public void addInfo(String[] messages) {
                        // ignored
                    }
                    // errors are ignored
                }, "docker image rm eskimo:" + imageName + "_template || true");
            } catch (SSHCommandException e) {
                logger.error(e, e);
                ml.addInfo(e.getMessage());
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

    void exec(SSHConnection connection, MessageLogger ml, String[] setupScript) throws SSHCommandException {
        ml.addInfo(sshCommandService.runSSHCommand(connection, setupScript));
    }

    void exec(SSHConnection connection, MessageLogger ml, String command) throws SSHCommandException {
        ml.addInfo(sshCommandService.runSSHCommand(connection, command));
    }

    @Override
    public List<Pair<String, String>> buildDeadIps(Set<String> allNodes, NodesConfigWrapper nodesConfig, Set<String> liveIps, Set<String> deadIps) {
        List<Pair<String, String>> nodesSetup = new ArrayList<>();

        // Find out about dead IPs
        Set<String> nodesToTest = new HashSet<>(allNodes);
        nodesToTest.addAll(nodesConfig.getNodeAddresses());
        for (String node : nodesToTest) {

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

        /*
        if (!deadIps.isEmpty()) {
            messagingService.addLines("\n");
        }
        */

        return nodesSetup;
    }

    private void checkSudoPossible(Set<String> deadIps, String node) throws SSHCommandException {

        String result = sshCommandService.runSSHScript(node, "sudo ls", false);
        if (   result.contains("a terminal is required to read the password")
            || result.contains("a password is required")) {
            //messagingService.addLines("\nNode " + node + " doesn't enable the configured user to use sudo without password. Installing cannot continue.");
            notificationService.addError("Node " + node + " sudo problem");
            deadIps.add(node);
        }

    }

    void handleNodeDead(Set<String> deadIps, String node) {
        //messagingService.addLines("\nNode seems dead " + node);
        notificationService.addError("Node " + node + " is dead.");
        deadIps.add(node);
    }

    void handleSSHFails(Set<String> deadIps, String node) {
        //messagingService.addLines("\nNode " + node + " couldn't be joined through SSH\nIs the user to be used by eskimo properly created and the public key properly added to SSH authorized keys ? (See User Guide)");
        notificationService.addError("Node " + node + " not reachable.");
        deadIps.add(node);
    }

    @Override
    public File createRemotePackageFolder(MessageLogger ml, SSHConnection connection, String node, String service, String imageName) throws SystemException, IOException, SSHCommandException {
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
            throw new SystemException("Could not create archive for service " + service + " : " + SystemServiceImpl.TMP_PATH_PREFIX +  tmpArchiveFile.getName());
        }

        // 2. copy it over to target node and extract it

        // 2.1
        sshCommandService.copySCPFile(connection, archive.getAbsolutePath());

        exec(connection, ml, "rm -Rf " + SystemServiceImpl. TMP_PATH_PREFIX + service);
        exec(connection, ml, "rm -f " + SystemServiceImpl.TMP_PATH_PREFIX + service + ".tgz");
        exec(connection, ml, "mv " +  tmpArchiveFile.getName() + " " + SystemServiceImpl.TMP_PATH_PREFIX + service + ".tgz");
        exec(connection, ml, "tar xfz " + SystemServiceImpl.TMP_PATH_PREFIX + service + ".tgz --directory=" + SystemServiceImpl.TMP_PATH_PREFIX);
        exec(connection, ml, "chmod 755 " + SystemServiceImpl.TMP_PATH_PREFIX + service + "/setup.sh");

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

                ml.addInfo(" - Copying over docker image " + imageFileName);
                sshCommandService.copySCPFile(connection, packageDistributionPath + "/" + imageFileName);

                exec(connection, ml, new String[]{"mv", imageFileName, SystemServiceImpl.TMP_PATH_PREFIX + service + "/"});

                exec(connection, ml, new String[]{"mv",
                        SystemServiceImpl.TMP_PATH_PREFIX + service + "/" + imageFileName,
                        SystemServiceImpl.TMP_PATH_PREFIX + service + "/" + SetupService.DOCKER_TEMPLATE_PREFIX + imageName + ".tar.gz"});

            } else {
                ml.addInfo(" - (no container found for " + service + "OperationsMonitoringServiceTest - will just invoke setup)");
            }
        }
        return tmpArchiveFile;
    }

    @Override
    public File createTempFile(String service, String node, String extension) throws IOException {
        return File.createTempFile(service, extension);
    }
}
