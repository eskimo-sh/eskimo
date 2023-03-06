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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.eskimo.model.service.Dependency;
import ch.niceideas.eskimo.model.service.MasterElectionStrategy;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Operation;
import ch.niceideas.eskimo.types.Service;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class NodeServiceOperationsCommand
        extends AbstractServiceOperationsCommand<NodeServiceOperationsCommand.ServiceOperationId, NodesConfigWrapper>
        implements Serializable {

    private final Set<Node> allNodes = new HashSet<>();

    public static NodeServiceOperationsCommand create (
            ServicesDefinition servicesDefinition,
            NodeRangeResolver nodeRangeResolver,
            ServicesInstallStatusWrapper servicesInstallStatus,
            NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {

        NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges (rawNodesConfig);

        NodeServiceOperationsCommand retCommand = new NodeServiceOperationsCommand(rawNodesConfig, nodesConfig.getAllNodes());

        // 1. Find out about services that need to be installed
        for (Service service : servicesDefinition.listServicesOrderedByDependencies()) {
            for (int nodeNumber : nodesConfig.getAllNodeNumbersWithService(service)) {

                Node node = nodesConfig.getNode(nodeNumber);

                if (!servicesInstallStatus.isServiceInstalled(service, node)) {

                    retCommand.addInstallation(new ServiceOperationId(ServiceOperation.INSTALLATION, service, node));
                }
            }
        }

        // 2. Find out about services that need to be uninstalled

        for (Pair<Service, Node> installationPairs : servicesInstallStatus.getAllServiceNodeInstallationPairs()) {

            Service installedService = installationPairs.getKey();
            Node node = installationPairs.getValue();

            ServiceDefinition serviceDef = Optional.ofNullable(servicesDefinition.getServiceDefinition(installedService))
                    .orElseThrow(() -> new IllegalStateException("Cann find service " + installedService + " in services definition"));
            if (!serviceDef.isKubernetes()) {

                nodesConfig.getAllNodesWithService(installedService).stream()
                        .filter(candidate -> candidate.equals(node))
                        .findAny()
                        .ifPresentOrElse(
                                (candidate) -> {},
                                () -> retCommand.addUninstallation(new ServiceOperationId(ServiceOperation.UNINSTALLATION, installedService, node))
                        );
            }
        }

        // 3. If a services changed, dependent services need to be restarted
        Set<Service> changedServices = new HashSet<>();

        changedServices.addAll (retCommand.getInstallations().stream().map(ServiceOperationId::getService).collect(Collectors.toList()));
        changedServices.addAll (retCommand.getUninstallations().stream().map(ServiceOperationId::getService).collect(Collectors.toList()));

        changedServices.forEach(service -> servicesDefinition.getDependentServices(service).stream()
                .filter(dependent -> {
                    ServiceDefinition masterServiceDef = servicesDefinition.getServiceDefinition(service);
                    Dependency dep = servicesDefinition.getServiceDefinition(dependent).getDependency(masterServiceDef).orElseThrow(
                            () -> new IllegalStateException("Couldn't find dependency "  + masterServiceDef + " on " + dependent));
                    return dep.isRestart();
                })
                .forEach(dependent -> {

                    if (servicesDefinition.getServiceDefinition(dependent).isKubernetes()) {
                        if (servicesInstallStatus.isServiceInstalledAnywhere(dependent)) {
                            retCommand.addRestartIfNotInstalled(dependent, Node.KUBERNETES_FLAG);
                        }

                    } else {

                        for (int nodeNumber : nodesConfig.getAllNodeNumbersWithService(dependent)) {

                            Node node = nodesConfig.getNode(nodeNumber);

                            ServiceDefinition masterServiceDef = servicesDefinition.getServiceDefinition(service);
                            Dependency dep = servicesDefinition.getServiceDefinition(dependent).getDependency(masterServiceDef).orElseThrow(IllegalStateException::new);

                            // if dependency is same node, only restart if service on same node is impacted
                            if (!dep.getMes().equals(MasterElectionStrategy.SAME_NODE) || isServiceImpactedOnSameNode(retCommand, masterServiceDef, node)) {
                                retCommand.addRestartIfNotInstalled(dependent, node);
                            }
                        }
                    }
                }));

        // also add services simply flagged as needed restart previously
        servicesInstallStatus.getRootKeys().forEach(installStatusFlag -> {
            Pair<Service, Node> serviceAndNodePair = ServicesInstallStatusWrapper.parseInstallStatusFlag (installStatusFlag);
            Service installedService = Objects.requireNonNull(serviceAndNodePair).getKey();
            Node node = Objects.requireNonNull(serviceAndNodePair).getValue();
            String status = (String) servicesInstallStatus.getValueForPath(installStatusFlag);
            if (status.equals("restart")) {
                feedInRestartService(servicesDefinition, servicesInstallStatus, retCommand, node, installedService);
            }
        });

        // XXX need to sort again since I am not relying on feedInRestartService which was taking care of sorting
        retCommand.getRestarts().sort((o1, o2) -> servicesDefinition.compareServices(o1.getService(), o2.getService()));

        return retCommand;
    }

    private static boolean isServiceImpactedOnSameNode(NodeServiceOperationsCommand retCommand, ServiceDefinition masterServiceDef, Node node) {
        return retCommand.getInstallations().stream()
                .anyMatch (operationId -> operationId.getNode().equals(node) && operationId.getService().equals(masterServiceDef.toService()));
    }

    static void feedInRestartService(ServicesDefinition servicesDefinition, ServicesInstallStatusWrapper servicesInstallStatus, NodeServiceOperationsCommand retCommand, NodesConfigWrapper nodesConfig, Set<Service> restartedServices) {
        restartedServices.stream()
                .sorted(servicesDefinition::compareServices)
                .forEach(service -> feedInRestartService(servicesDefinition, servicesInstallStatus, retCommand, nodesConfig, service));
    }

    private static void feedInRestartService(ServicesDefinition servicesDefinition, ServicesInstallStatusWrapper servicesInstallStatus, NodeServiceOperationsCommand retCommand, Node node, Service restartedService) {
        if (servicesDefinition.getServiceDefinition(restartedService).isKubernetes()) {
            if (servicesInstallStatus.isServiceInstalledAnywhere(restartedService)) {
                retCommand.addRestartIfNotInstalled(restartedService, Node.KUBERNETES_FLAG);
            }
        } else {
            retCommand.addRestartIfNotInstalled(restartedService, node);
        }
    }

    private static void feedInRestartService(ServicesDefinition servicesDefinition, ServicesInstallStatusWrapper servicesInstallStatus, NodeServiceOperationsCommand retCommand, NodesConfigWrapper nodesConfig, Service restartedService) {
        if (servicesDefinition.getServiceDefinition(restartedService).isKubernetes()) {
            if (servicesInstallStatus.isServiceInstalledAnywhere(restartedService)) {
                retCommand.addRestartIfNotInstalled(restartedService, Node.KUBERNETES_FLAG);
            }
        } else {
            for (int nodeNumber : nodesConfig.getAllNodeNumbersWithService(restartedService)) {

                Node node = nodesConfig.getNode(nodeNumber);

                retCommand.addRestartIfNotInstalled(restartedService, node);
            }
        }
    }

    public static NodeServiceOperationsCommand createForRestartsOnly (
            ServicesDefinition servicesDefinition,
            NodeRangeResolver nodeRangeResolver,
            Service[] servicesToRestart,
            ServicesInstallStatusWrapper servicesInstallStatus,
            NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {

        NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges (rawNodesConfig);

        NodeServiceOperationsCommand retCommand = new NodeServiceOperationsCommand(rawNodesConfig, nodesConfig.getAllNodes());

        Set<Service> restartedServices = new HashSet<>(Arrays.asList(servicesToRestart));

        feedInRestartService(servicesDefinition, servicesInstallStatus, retCommand, nodesConfig, restartedServices);

        return retCommand;
    }

    NodeServiceOperationsCommand(NodesConfigWrapper rawNodesConfig, List<Node> allNodes) {
        super (rawNodesConfig);
        this.allNodes.addAll(allNodes);
    }

    void addRestartIfNotInstalled(Service service, Node node) {
        if (   !getInstallations().contains(new ServiceOperationId (ServiceOperation.INSTALLATION, service, node))
            && !getRestarts().contains(new ServiceOperationId (ServiceOperation.RESTART, service, node))) {
            addRestart(new ServiceOperationId (ServiceOperation.RESTART, service, node));
        }
    }

    @Override
    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("installations", new JSONArray(toJsonList(getInstallations())));
            put("uninstallations", new JSONArray(toJsonList(getUninstallations())));
            put("restarts", new JSONArray(toJsonList(getRestarts())));
        }});
    }

    public Set<Node> getAllNodes() {
        Set<Node> retSet = new HashSet<>();
        getInstallations().stream()
                .map(ServiceOperationId::getNode)
                .forEach(retSet::add);
        getUninstallations().stream()
                .map(ServiceOperationId::getNode)
                .forEach(retSet::add);
        getRestarts().stream()
                .map(ServiceOperationId::getNode)
                .forEach(retSet::add);

        // this one can come from restartes flags
        retSet.remove(Node.KUBERNETES_FLAG);

        return retSet;
    }

    @Override
    public List<ServiceOperationId> getNodesCheckOperation() {
        Set<Node> allNodes = new HashSet<>(getAllNodes());
        allNodes.addAll(this.allNodes);
        return allNodes.stream()
                .map(node -> new ServiceOperationId(ServiceOperation.CHECK_INSTALL, Service.BASE_SYSTEM, node))
                .sorted()
                .collect(Collectors.toList());
    }

    @EqualsAndHashCode(callSuper = true)
    public static class ServiceOperationId extends AbstractStandardOperationId<ServiceOperation> implements OperationId<ServiceOperation> {

        public ServiceOperationId (ServiceOperation operation, Service service, Node node) {
            super (operation, service, node);
        }
    }

    @RequiredArgsConstructor
    public enum ServiceOperation implements Operation {
        CHECK_INSTALL("Check / Install", 10),
        INSTALLATION("installation", 11),
        UNINSTALLATION ("uninstallation", 12),
        RESTART ("restart", 13);

        @Getter
        private final String type;

        @Getter
        private final int ordinal;

        @Override
        public String toString () {
            return type;
        }
    }
}
