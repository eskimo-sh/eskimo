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

import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.service.MasterDetection;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.mdStrategy.MdStrategy;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MasterService {

    private static final Logger logger = Logger.getLogger(MasterService.class);

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Autowired
    private SystemService systemService;

    @Value("${system.statusUpdatePeriodSeconds}")
    private int statusUpdatePeriodSeconds = 5;

    private final ReentrantLock statusUpdateLock = new ReentrantLock();
    private final ScheduledExecutorService statusRefreshScheduler;

    private final Map<String, String> serviceMasterNodes = new ConcurrentHashMap<>();
    private final Map<String, Date> serviceMasterTimestamps = new ConcurrentHashMap<>();

    /**
     * for tests
     */
    void setSshCommandService(SSHCommandService sshCommandService) {
        this.sshCommandService = sshCommandService;
    }
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setNodeRangeResolver (NodeRangeResolver nodeRangeResolver) {
        this.nodeRangeResolver = nodeRangeResolver;
    }
    void setSystemService (SystemService systemService) {
        this.systemService = systemService;
    }
    Map<String, String>  getServiceMasterNodes() {
        return Collections.unmodifiableMap(serviceMasterNodes);
    }
    Map<String, Date> getServiceMasterTimestamps() {
        return Collections.unmodifiableMap(serviceMasterTimestamps);
    }

    // constructor for spring
    public MasterService() {
        this (true);
    }
    public MasterService(boolean createUpdateScheduler) {
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

    public MasterStatusWrapper getMasterStatus() throws MasterExceptionWrapperException {

        try {

            ensureDefaultMastersSet();

            return buildMasterStatus();

            // feed in a default master for all servces without master (as long as proper master it not found)

        } catch (SystemException | NodesConfigurationException | SetupException e) {

            logger.error (e, e);
            throw new MasterExceptionWrapperException(e);
        }
    }

    private void ensureDefaultMastersSet() throws SetupException, SystemException, NodesConfigurationException {

        List<Service> missingMasterServices = Arrays.stream(servicesDefinition.listMultipleServices())
                .map(serviceName -> servicesDefinition.getService(serviceName))
                .filter(service -> service.getMasterDetection() != null)
                .filter(service -> !serviceMasterNodes.containsKey(service.getName()))
                .collect(Collectors.toList());

        if (!missingMasterServices.isEmpty()) {

            NodesConfigWrapper rawNodesConfig = configurationService.loadNodesConfig();

            if (rawNodesConfig != null && !rawNodesConfig.isEmpty()) {

                NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

                for (Service service : missingMasterServices) {

                    List<String> serviceNodes;
                    if (service.isKubernetes()) {
                        serviceNodes = nodesConfig.getAllNodeAddressesWithService(KubernetesService.KUBE_MASTER);
                    } else {
                        // get any node where it's supposed to be install (always the same one)
                        serviceNodes = nodesConfig.getAllNodeAddressesWithService(service.getName());
                    }

                    if (!serviceNodes.isEmpty()) {

                        Collections.sort(serviceNodes);
                        serviceMasterNodes.put(service.getName(), serviceNodes.get(0));
                        serviceMasterTimestamps.put(service.getName(), new Date(Long.MIN_VALUE));
                    }
                }
            }
        }
    }

    public void updateStatus() {

        try {
            statusUpdateLock.lock();

            // 1. Load Node Config
            NodesConfigWrapper rawNodesConfig = configurationService.loadNodesConfig();

            if (rawNodesConfig != null && !rawNodesConfig.isEmpty()) {

                NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

                // 2. Browse all services that are multiple
                List<Service> masterServices = Arrays.stream(servicesDefinition.listMultipleServices())
                        .map(serviceName -> servicesDefinition.getService(serviceName))
                        .filter(service -> service.getMasterDetection() != null)
                        .collect(Collectors.toList());

                SystemStatusWrapper lastStatus = systemService.getStatus();

                for (Service service : masterServices) {

                    // If service is available on a single node in anyway, don't bother going to master election
                    List<String> serviceNodes = lastStatus.getAllNodesForServiceRegardlessStatus(service.getName());
                    if (serviceNodes.size() == 1) {

                        serviceMasterTimestamps.put(service.getName(),
                                new Date(System.currentTimeMillis() - (1000L * 60L * 60L * 24L * 365L * 10L))); // 10 years ago
                        serviceMasterNodes.put(service.getName(), serviceNodes.get(0));

                    } else {

                        MasterDetection masterDetection = service.getMasterDetection();
                        MdStrategy strategy = masterDetection.getDetectionStrategy().getStrategy();

                        // 3. If service is installed on multiple node, attempt to detect master
                        List < String > nodes;
                        if (service.isKubernetes()) {
                            nodes = nodesConfig.getNodeAddresses();
                        } else {
                            nodes = nodesConfig.getAllNodeAddressesWithService(service.getName());
                        }

                        for (String node : nodes) {

                            if (lastStatus.isServiceOKOnNode(service.getName(), node)) {

                                try {
                                    Date masterElectedDate = strategy.detectMaster(
                                            service, node, masterDetection, this, sshCommandService, notificationService);

                                    if (masterElectedDate != null) {
                                        handleMasterDetectedDate(service, node, masterElectedDate);
                                    }

                                } catch (MasterDetectionException e) {
                                    logger.warn(e.getMessage());
                                    logger.debug(e, e);
                                }
                            } else {
                                if (!service.isKubernetes()) {
                                    logger.warn("Not checking if service " + service.getName() + " is master on " + node + " since service reports issues");
                                }
                            }
                        }
                    }
                }
            }

        } catch (SystemException | NodesConfigurationException | SetupException | SystemService.StatusExceptionWrapperException e) {
            logger.error (e, e);

        } finally {
            statusUpdateLock.unlock();
            // reschedule
            if (statusRefreshScheduler != null) {
                statusRefreshScheduler.schedule(this::updateStatus, statusUpdatePeriodSeconds * 10L, TimeUnit.SECONDS);
            }
        }
    }

    private MasterStatusWrapper buildMasterStatus() {

        MasterStatusWrapper masterStatus = MasterStatusWrapper.empty();

        serviceMasterNodes.keySet().forEach(
              service -> masterStatus.setValueForPath(
                      "masters." + service,
                      serviceMasterNodes.get(service).replace(".", "-")));

        return masterStatus;
    }

    private void handleMasterDetectedDate(Service service, String node, Date masterElectedDate) {

        Date previous = serviceMasterTimestamps.get(service.getName());
        if (previous == null || previous.before(masterElectedDate)) {
            serviceMasterTimestamps.put(service.getName(), masterElectedDate);
            serviceMasterNodes.put(service.getName(), node);
        }
    }


    public static class MasterExceptionWrapperException extends Exception {

        static final long serialVersionUID = -3317632123352221248L;

        MasterExceptionWrapperException(Exception cause) {
            super(cause);
        }

    }
}
