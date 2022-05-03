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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.SerializablePair;
import ch.niceideas.eskimo.services.*;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ch.niceideas.eskimo.model.SimpleOperationCommand.standardizeOperationMember;

public class ServiceOperationsCommand extends JSONInstallOpCommand<ServiceOperationsCommand.ServiceOperationId> implements Serializable {

    private static final Logger logger = Logger.getLogger(ServiceOperationsCommand.class);

    @Deprecated
    public static final String MARATHON_FLAG = "(marathon)";

    public static final String KUBERNETES_FLAG = "(kubernetes)";
    public static final String CHECK_INSTALL_OP_TYPE = "Check / Install";
    public static final String BASE_SYSTEM = "Base System";

    private final NodesConfigWrapper rawNodesConfig;

    private List<ServiceOperationId> restarts = new ArrayList<>();

    public static ServiceOperationsCommand create (
            ServicesDefinition servicesDefinition,
            NodeRangeResolver nodeRangeResolver,
            ServicesInstallStatusWrapper servicesInstallStatus,
            NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {

        ServiceOperationsCommand retCommand = new ServiceOperationsCommand(rawNodesConfig);

        NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges (rawNodesConfig);

        // 1. Find out about services that need to be installed
        for (String service : servicesDefinition.listServicesOrderedByDependencies()) {
            for (int nodeNumber : nodesConfig.getNodeNumbers(service)) {

                String node = nodesConfig.getNodeAddress(nodeNumber);
                String nodeName = node.replace(".", "-");

                if (!servicesInstallStatus.isServiceInstalled(service, nodeName)) {

                    retCommand.addInstallation(new ServiceOperationId("installation", service, node));
                }
            }
        }

        // 2. Find out about services that need to be uninstalled

        for (Pair<String, String> installationPairs : servicesInstallStatus.getAllServiceAndNodeNameInstallationPairs()) {

            String installedService = installationPairs.getKey();
            String nodeName = installationPairs.getValue();

            Service service = servicesDefinition.getService(installedService);
            if (service == null) {
                throw new IllegalStateException("Cann find service " + installedService + " in services definition");
            }

            if (!service.isKubernetes()) {

                String node = nodeName.replace("-", ".");

                try {
                    int nodeNbr = nodesConfig.getNodeNumber(node);
                    boolean found = false;

                    // search it in config
                    for (int nodeNumber : nodesConfig.getNodeNumbers(installedService)) {

                        if (nodeNumber == nodeNbr) {
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        retCommand.addUninstallation(new ServiceOperationId("uninstallation", installedService, node));
                    }

                } catch (SystemException e) {
                    logger.debug(e, e);
                    retCommand.addUninstallation(new ServiceOperationId("uninstallation", installedService, node));
                }
            }
        }

        // 3. If a services changed, dependent services need to be restarted
        Set<String> changedServices = new HashSet<>();

        changedServices.addAll (retCommand.getInstallations().stream().map(ServiceOperationId::getService).collect(Collectors.toList()));
        changedServices.addAll (retCommand.getUninstallations().stream().map(ServiceOperationId::getService).collect(Collectors.toList()));

        //Set<String> restartedServices = new HashSet<>();
        changedServices.forEach(service -> {
                servicesDefinition.getDependentServices(service).stream()
                        .filter(dependent -> {
                            Service masterService = servicesDefinition.getService(service);
                            Dependency dep = servicesDefinition.getService(dependent).getDependency(masterService).orElseThrow(IllegalStateException::new);
                            return dep.isRestart();
                        })
                        .forEach(dependent -> {

                            if (servicesDefinition.getService(dependent).isKubernetes()) {
                                if (servicesInstallStatus.isServiceInstalledAnywhere(dependent)) {
                                    retCommand.addRestartIfNotInstalled(dependent, KUBERNETES_FLAG);
                                }

                            } else {

                                for (int nodeNumber : nodesConfig.getNodeNumbers(dependent)) {

                                    String node = nodesConfig.getNodeAddress(nodeNumber);

                                    Service masterService = servicesDefinition.getService(service);
                                    Dependency dep = servicesDefinition.getService(dependent).getDependency(masterService).orElseThrow(IllegalStateException::new);

                                    // if dependency is same node, only restart if service on same node is impacted
                                    if (!dep.getMes().equals(MasterElectionStrategy.SAME_NODE) || isServiceImpactedOnSameNode(retCommand, masterService, node)) {
                                        retCommand.addRestartIfNotInstalled(dependent, node);
                                    }
                                }
                            }

                        });
        });

        // also add services simply flagged as needed restart previously
        servicesInstallStatus.getRootKeys().forEach(installStatusFlag -> {
            Pair<String, String> serviceAndNodePair = ServicesInstallStatusWrapper.parseInstallStatusFlag (installStatusFlag);
            String installedService = Objects.requireNonNull(serviceAndNodePair).getKey();
            String nodeName = Objects.requireNonNull(serviceAndNodePair).getValue();
            String node = nodeName.replace("-", ".");
            String status = (String) servicesInstallStatus.getValueForPath(installStatusFlag);
            if (status.equals("restart")) {
                feedInRestartService(servicesDefinition, servicesInstallStatus, retCommand, node, installedService);
            }
        });

        //feedInRestartService(servicesDefinition, servicesInstallStatus, retCommand, nodesConfig, (Set<String>) restartedServices);

        // XXX need to sort again since I am not relying on feedInRestartService which was taking care of sorting
        List<ServiceOperationId> restarts = retCommand.getRestarts();
        restarts.sort((o1, o2) -> servicesDefinition.compareServices(o1.getService(), o2.getService()));
        retCommand.setRestarts(restarts);

        return retCommand;
    }

    private static boolean isServiceImpactedOnSameNode(ServiceOperationsCommand retCommand, Service masterService, String node) {
        return retCommand.getInstallations().stream()
                .anyMatch (operationId -> operationId.getNode().equals(node) && operationId.getService().equals(masterService.getName()));
    }

    static void feedInRestartService(ServicesDefinition servicesDefinition, ServicesInstallStatusWrapper servicesInstallStatus, ServiceOperationsCommand retCommand, NodesConfigWrapper nodesConfig, Set<String> restartedServices) {
        restartedServices.stream()
                .sorted(servicesDefinition::compareServices)
                .forEach(serviceName -> feedInRestartService(servicesDefinition, servicesInstallStatus, retCommand, nodesConfig, serviceName));
    }

    private static void feedInRestartService(ServicesDefinition servicesDefinition, ServicesInstallStatusWrapper servicesInstallStatus, ServiceOperationsCommand retCommand, String node, String restartedService) {
        if (servicesDefinition.getService(restartedService).isKubernetes()) {
            if (servicesInstallStatus.isServiceInstalledAnywhere(restartedService)) {
                retCommand.addRestartIfNotInstalled(restartedService, KUBERNETES_FLAG);
            }
        } else {
            retCommand.addRestartIfNotInstalled(restartedService, node);
        }
    }

    private static void feedInRestartService(ServicesDefinition servicesDefinition, ServicesInstallStatusWrapper servicesInstallStatus, ServiceOperationsCommand retCommand, NodesConfigWrapper nodesConfig, String restartedService) {
        if (servicesDefinition.getService(restartedService).isKubernetes()) {
            if (servicesInstallStatus.isServiceInstalledAnywhere(restartedService)) {
                retCommand.addRestartIfNotInstalled(restartedService, KUBERNETES_FLAG);
            }
        } else {
            for (int nodeNumber : nodesConfig.getNodeNumbers(restartedService)) {

                String node = nodesConfig.getNodeAddress(nodeNumber);

                retCommand.addRestartIfNotInstalled(restartedService, node);
            }
        }
    }

    public static ServiceOperationsCommand createForRestartsOnly (
            ServicesDefinition servicesDefinition,
            NodeRangeResolver nodeRangeResolver,
            String[] servicesToRestart,
            ServicesInstallStatusWrapper servicesInstallStatus,
            NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {

        ServiceOperationsCommand retCommand = new ServiceOperationsCommand(rawNodesConfig);

        NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges (rawNodesConfig);

        Set<String> restartedServices = new HashSet<>(Arrays.asList(servicesToRestart));

        feedInRestartService(servicesDefinition, servicesInstallStatus, retCommand, nodesConfig, restartedServices);

        return retCommand;
    }

    ServiceOperationsCommand(NodesConfigWrapper rawNodesConfig) {
        this.rawNodesConfig = rawNodesConfig;
    }

    public NodesConfigWrapper getRawConfig() {
        return rawNodesConfig;
    }

    void addRestartIfNotInstalled(String service, String node) {
        if (   !getInstallations().contains(new ServiceOperationId ("installation", service, node))
            && !getRestarts().contains(new ServiceOperationId ("restart", service, node))) {
            restarts.add(new ServiceOperationId ("restart", service, node));
        }
    }

    public List<ServiceOperationId> getRestarts() {
        return restarts;
    }

    private void setRestarts (List<ServiceOperationId> restarts) {
        this.restarts = restarts;
    }

    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("installations", new JSONArray(toJsonList(getInstallations())));
            put("uninstallations", new JSONArray(toJsonList(getUninstallations())));
            put("restarts", new JSONArray(toJsonList(restarts)));
        }});
    }

    private Collection<Object> toJsonList(List<ServiceOperationId> listOfPairs) {
        return listOfPairs.stream()
                .map((Function<ServiceOperationId, Object>) id -> {
                    JSONObject ret = new JSONObject();
                    try {
                        ret.put(id.getService(), id.getNode());
                    } catch (JSONException e) {
                        // cannot happen
                    }
                    return ret;
                })
                .collect(Collectors.toList());
    }

    public Set<String> getAllNodes() {
        Set<String> retSet = new HashSet<>();
        getInstallations().stream()
                .map(ServiceOperationId::getNode)
                .forEach(retSet::add);
        getUninstallations().stream()
                .map(ServiceOperationId::getNode)
                .forEach(retSet::add);
        restarts.stream()
                .map(ServiceOperationId::getNode)
                .forEach(retSet::add);

        // this one can come from restartes flags
        retSet.remove(ServiceOperationsCommand.MARATHON_FLAG);

        return retSet;
    }

    public List<List<ServiceOperationId>> getRestartsInOrder
            (ServicesInstallationSorter servicesInstallationSorter, NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, NodesConfigurationException {
        return servicesInstallationSorter.orderOperations (getRestarts(), nodesConfig);
    }

    public List<List<ServiceOperationId>> getInstallationsInOrder
            (ServicesInstallationSorter servicesInstallationSorter, NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, NodesConfigurationException {
        return servicesInstallationSorter.orderOperations (getInstallations(), nodesConfig);
    }

    public List<List<ServiceOperationId>> getUninstallationsInOrder
            (ServicesInstallationSorter servicesInstallationSorter, NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, NodesConfigurationException {
        List<List<ServiceOperationId>> orderedUninstallations = servicesInstallationSorter.orderOperations (getUninstallations(), nodesConfig);
        Collections.reverse(orderedUninstallations);
        return orderedUninstallations;
    }

    @Override
    public List<ServiceOperationId> getAllOperationsInOrder
            (OperationsContext context)
            throws ServiceDefinitionException, NodesConfigurationException {

        List<ServiceOperationId> allOpList = new ArrayList<>();

        context.getNodesConfig().getNodeAddresses().forEach(node -> allOpList.add(new ServiceOperationId(CHECK_INSTALL_OP_TYPE, BASE_SYSTEM, node)));

        getInstallationsInOrder(context.getServicesInstallationSorter(), context.getNodesConfig()).forEach(allOpList::addAll);
        getUninstallationsInOrder(context.getServicesInstallationSorter(), context.getNodesConfig()).forEach(allOpList::addAll);
        getRestartsInOrder(context.getServicesInstallationSorter(), context.getNodesConfig()).forEach(allOpList::addAll);

        return allOpList;
    }

    @Data
    @RequiredArgsConstructor
    public static class ServiceOperationId implements OperationId {

        private final String type;
        private final String service;
        private final String node;

        public boolean isOnNode(String node) {
            return this.node.equals(node);
        }

        public boolean isSameNode(OperationId other) {
            return other.isOnNode(this.getNode());
        }

        public String getMessage() {
            return type + " of " + getService() + " on " + getNode();
        }

        @Override
        public String toString() {
            return standardizeOperationMember (type)
                    + "_"
                    + standardizeOperationMember (service)
                    + "_"
                    + standardizeOperationMember (node);
        }
    }
}
