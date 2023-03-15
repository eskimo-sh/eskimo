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

package ch.niceideas.eskimo.services.satellite;

import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.services.*;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MemoryComputer {

    private static final Logger logger = Logger.getLogger(MemoryComputer.class);

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private SystemService systemService;

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.operationWaitTimoutSeconds}")
    private int operationWaitTimout = 400;

    @Value("${system.reservedMemoryOnNodes}")
    private long reservedMemoryMb = 1000;

    public MemoryModel buildMemoryModel (NodesConfigWrapper nodesConfig, KubernetesServicesConfigWrapper kubeServicesConfig, Set<Node> deadNodes) throws SystemException {
        return new MemoryModel(computeMemory(nodesConfig, kubeServicesConfig, deadNodes));
    }

    Map<Node, Map<Service, Long>> computeMemory(NodesConfigWrapper nodesConfig, KubernetesServicesConfigWrapper kubeServicesConfig, Set<Node> deadNodes) throws SystemException {

        // returns ipAdress -> service -> memory in MB
        Map<Node, Map<Service, Long>> retMap = new HashMap<>();

        // 1. Find out about available RAM on nodes
        Map<Node, Long> memoryMap = getMemoryMap(nodesConfig, deadNodes);

        // 2. for every node
        for (Map.Entry<Node, Long> entry: memoryMap.entrySet()) {

            Node node = entry.getKey();
            Map<Service, Long> nodeMemoryModel = new HashMap<>();

            long totalMemory = entry.getValue();

            // 2..1  Compute total amount of memory shards (high = 3, medium = 2, small = 1, negligible = 0)
            //       assume filesystem cache has to keep a high share (3) or medium share (2) => dynamical
            Set<Service> services = new HashSet<>(nodesConfig.getServicesForNode(node));

            long sumOfParts = services.stream()
                    .map (service -> servicesDefinition.getServiceDefinition(service))
                    .map (serviceDef -> (long) serviceDef.getMemoryConsumptionParts(servicesDefinition))
                    .reduce( (long)(totalMemory > 10000 ? 3 : 2), Long::sum); // the filesystem cache is considered always 3 or 2

            // 3. Now get full memory required for Kubernetes services, divide it by number of nodes and add it to each node
            if (kubeServicesConfig != null) {

                // 3.1 non unique services are considered on the node
                sumOfParts += (kubeServicesConfig.getEnabledServices().stream()
                        .map(serviceName -> servicesDefinition.getServiceDefinition(serviceName))
                        .filter(service -> !service.isUnique())
                        .map(service -> (long) service.getMemoryConsumptionParts(servicesDefinition))
                        .reduce(0L, Long::sum));

                // 3.2, Unique services sum is divided by number of nodes
                long sumOfUniqueParts = (kubeServicesConfig.getEnabledServices().stream()
                        .map(serviceName -> servicesDefinition.getServiceDefinition(serviceName))
                        .filter(ServiceDefinition::isUnique)
                        .map(service -> (long) service.getMemoryConsumptionParts(servicesDefinition))
                        .reduce(0L, Long::sum));

                sumOfParts += (sumOfUniqueParts / memoryMap.size());

            }

            // 4.1. Dive the total memory by the total shards => gives us the value of a shard
            long valueOfShard = (totalMemory - reservedMemoryMb) / sumOfParts;

            // 4.1 Now assign the memory to every node service for the node
            services.stream()
                    .map (service -> servicesDefinition.getServiceDefinition(service))
                    .filter (service -> service.getMemoryConsumptionSize().getNbrParts() > 0)
                    .forEach(service -> nodeMemoryModel.put (service.toService(), service.getMemoryConsumptionParts(servicesDefinition) * valueOfShard));

            // 4.2 Do the same for multiple kubernetes services
            if (kubeServicesConfig != null) {
                kubeServicesConfig.getEnabledServices().stream()
                        .map(service -> servicesDefinition.getServiceDefinition(service))
                        .filter(service -> service.getMemoryConsumptionSize().getNbrParts() > 0)
                        .forEach(service -> nodeMemoryModel.put(service.toService(), service.getMemoryConsumptionParts(servicesDefinition) * valueOfShard));
            }

            retMap.put(node, nodeMemoryModel);
        }

        return retMap;
    }

    Map<Node, Long> getMemoryMap(NodesConfigWrapper nodesConfig, Set<Node> deadNodes) throws SystemException {
        // concurrently build map of ipAddress -> full RAM in MB
        Map<Node, Long> memoryMap = new ConcurrentHashMap<>();

        systemService.performPooledOperation(
                nodesConfig.getAllNodes(), parallelismInstallThreadCount, operationWaitTimout,
                (node, error) -> {
                    if (!deadNodes.contains(node)) {
                        try {
                            String nodeMemory = sshCommandService.runSSHScript(node, "cat /proc/meminfo | grep MemTotal", false);
                            nodeMemory = nodeMemory.trim();
                            if (!nodeMemory.startsWith("MemTotal")) {
                                throw new SSHCommandException("Impossible to understand the format of the meminof result. Missing 'MemTotal' in " + nodeMemory);
                            }
                            BigDecimal divider;
                            if (nodeMemory.endsWith("B")) {
                                divider = BigDecimal.valueOf(1024^2);
                            } else if (nodeMemory.endsWith("kB")) {
                                divider = BigDecimal.valueOf(1024);
                            } else if (nodeMemory.endsWith("mB")) {
                                divider = BigDecimal.valueOf(1);
                            } else if (nodeMemory.endsWith("gB")) {
                                divider = BigDecimal.valueOf(1).divide(BigDecimal.valueOf(1024), RoundingMode.CEILING);
                            } else {
                                throw new SSHCommandException("Impossible to understand the format of " + nodeMemory);
                            }
                            BigDecimal memory = BigDecimal.valueOf(Long.parseLong(nodeMemory.substring(9, nodeMemory.length() - 2).trim()));
                            memoryMap.put (node, memory.divide(divider, RoundingMode.FLOOR).longValue());
                        } catch (SSHCommandException e) {
                            logger.error (e, e);
                            error.set(e);
                        }
                    }
                });

        return memoryMap;
    }

    public static class MemoryComputerException extends RuntimeException {

        static final long serialVersionUID = -3311512123124229248L;

        MemoryComputerException(Throwable cause) {
            super(cause);
        }
    }

}
