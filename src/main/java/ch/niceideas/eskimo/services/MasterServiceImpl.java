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

import ch.niceideas.eskimo.model.MasterStatusWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.SystemStatusWrapper;
import ch.niceideas.eskimo.model.service.MasterDetection;
import ch.niceideas.eskimo.model.service.ServiceDef;
import ch.niceideas.eskimo.services.mdstrategy.MdStrategy;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
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
@Profile("!test-master")
public class MasterServiceImpl implements MasterService {

    private static final Logger logger = Logger.getLogger(MasterServiceImpl.class);

    private static final long TEN_YEARS_AGO_IN_MILLIS = (1000L * 60L * 60L * 24L * 365L * 10L);

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

    private final Map<Service, Node> serviceMasterNodes = new ConcurrentHashMap<>();
    private final Map<Service, Date> serviceMasterTimestamps = new ConcurrentHashMap<>();

    /**
     * for tests
     */
    Map<Service, Node>  getServiceMasterNodes() {
        return Collections.unmodifiableMap(serviceMasterNodes);
    }
    Map<Service, Date> getServiceMasterTimestamps() {
        return Collections.unmodifiableMap(serviceMasterTimestamps);
    }

    // constructor for spring
    public MasterServiceImpl() {
        this (true);
    }
    public MasterServiceImpl(boolean createUpdateScheduler) {
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
        serviceMasterNodes.clear();
        serviceMasterTimestamps.clear();
    }

    @Override
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

        List<ServiceDef> missingMasterServices = Arrays.stream(servicesDefinition.listMultipleServices())
                .map(service -> servicesDefinition.getServiceDefinition(service))
                .filter(serviceDef -> serviceDef.getMasterDetection() != null)
                .filter(serviceDef -> !serviceMasterNodes.containsKey(serviceDef.toService()))
                .collect(Collectors.toList());

        if (!missingMasterServices.isEmpty()) {

            NodesConfigWrapper rawNodesConfig = configurationService.loadNodesConfig();

            if (rawNodesConfig != null && !rawNodesConfig.isEmpty()) {

                NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

                for (ServiceDef serviceDef : missingMasterServices) {

                    List<Node> serviceNodes;
                    if (serviceDef.isKubernetes()) {
                        serviceNodes = nodesConfig.getAllNodesWithService(servicesDefinition.getKubeMasterService().toService());
                    } else {
                        // get any node where it's supposed to be install (always the same one)
                        serviceNodes = nodesConfig.getAllNodesWithService(serviceDef.toService());
                    }

                    if (!serviceNodes.isEmpty()) {

                        Collections.sort(serviceNodes);
                        serviceMasterNodes.put(serviceDef.toService(), serviceNodes.get(0));
                        serviceMasterTimestamps.put(serviceDef.toService(), new Date(Long.MIN_VALUE));
                    }
                }
            }
        }
    }

    @Override
    public void updateStatus() {

        try {
            statusUpdateLock.lock();

            // 1. Load Node Config
            NodesConfigWrapper rawNodesConfig = configurationService.loadNodesConfig();

            if (rawNodesConfig != null && !rawNodesConfig.isEmpty()) {

                NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);

                // 2. Browse all services that are multiple
                List<ServiceDef> masterServices = Arrays.stream(servicesDefinition.listMultipleServices())
                        .map(service -> servicesDefinition.getServiceDefinition(service))
                        .filter(serviceDef -> serviceDef.getMasterDetection() != null)
                        .collect(Collectors.toList());

                SystemStatusWrapper lastStatus = systemService.getStatus();

                for (ServiceDef serviceDef : masterServices) {

                    // If service is available on a single node in anyway, don't bother going to master election
                    List<Node> serviceNodes = lastStatus.getAllNodesForServiceRegardlessStatus(serviceDef.toService());
                    if (serviceNodes.size() == 1) {

                        serviceMasterTimestamps.put(serviceDef.toService(),
                                new Date(System.currentTimeMillis() - TEN_YEARS_AGO_IN_MILLIS)); // 10 years ago
                        serviceMasterNodes.put(serviceDef.toService(), serviceNodes.get(0));

                    } else {

                        MasterDetection masterDetection = serviceDef.getMasterDetection();
                        MdStrategy strategy = masterDetection.getDetectionStrategy().getStrategy();

                        // 3. If service is installed on multiple node, attempt to detect master
                        List<Node> nodes = serviceDef.isKubernetes() ?
                                nodesConfig.getAllNodes() :
                                nodesConfig.getAllNodesWithService(serviceDef.toService());

                        for (Node node : nodes) {

                            if (lastStatus.isServiceOKOnNode(serviceDef.toService(), node)) {

                                try {
                                    Date masterElectedDate = strategy.detectMaster(
                                            serviceDef, node, masterDetection, this, sshCommandService, notificationService);

                                    if (masterElectedDate != null) {
                                        handleMasterDetectedDate(serviceDef.toService(), node, masterElectedDate);
                                    }

                                } catch (MasterDetectionException e) {
                                    logger.warn(e.getMessage());
                                    logger.debug(e, e);
                                }
                            } else {
                                if (!serviceDef.isKubernetes()) {
                                    logger.warn("Not checking if service " + serviceDef.getName() + " is master on " + node + " since service reports issues");
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
                      serviceMasterNodes.get(service).getName()));

        return masterStatus;
    }

    private void handleMasterDetectedDate(Service service, Node node, Date masterElectedDate) {

        Date previous = serviceMasterTimestamps.get(service);
        if (previous == null || previous.before(masterElectedDate)) {
            serviceMasterTimestamps.put(service, masterElectedDate);
            serviceMasterNodes.put(service, node);
        }
    }

}
