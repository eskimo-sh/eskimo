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
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OperationsCommand extends JSONOpCommand<SerializablePair<String, String>> implements Serializable {

    private static final Logger logger = Logger.getLogger(OperationsCommand.class);

    public static final String MARATHON_FLAG = "(marathon)";

    private final NodesConfigWrapper rawNodesConfig;

    private final ArrayList<SerializablePair<String, String>> restarts = new ArrayList<>();

    public static OperationsCommand create (
            ServicesDefinition servicesDefinition,
            NodeRangeResolver nodeRangeResolver,
            ServicesInstallStatusWrapper servicesInstallStatus,
            NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {

        OperationsCommand retCommand = new OperationsCommand(rawNodesConfig);

        NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges (rawNodesConfig);

        // 1. Find out about services that need to be installed
        for (String service : servicesDefinition.listServicesOrderedByDependencies()) {
            for (int nodeNumber : nodesConfig.getNodeNumbers(service)) {

                String node = nodesConfig.getNodeAddress(nodeNumber);
                String nodeName = node.replace(".", "-");

                if (!servicesInstallStatus.isServiceInstalled(service, nodeName)) {

                    retCommand.addInstallation(new SerializablePair<>(service, node));
                }
            }
        }

        // 2. Find out about services that need to be uninstalled

        for (Pair<String, String> installationPairs : servicesInstallStatus.getAllServiceAndNodeNameInstallationPairs()) {

            String installedService = installationPairs.getKey();
            String nodeName = installationPairs.getValue();

            if (!servicesDefinition.getService(installedService).isMarathon()) {

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
                        retCommand.addUninstallation(new SerializablePair<>(installedService, node));
                    }

                } catch (SystemException e) {
                    logger.debug(e, e);
                    retCommand.addUninstallation(new SerializablePair<>(installedService, node));
                }
            }
        }

        // 3. If a services changed, dependent services need to be restarted
        Set<String> changedServices = new HashSet<>();

        changedServices.addAll (retCommand.getInstallations().stream().map(Pair::getKey).collect(Collectors.toList()));
        changedServices.addAll (retCommand.getUninstallations().stream().map(Pair::getKey).collect(Collectors.toList()));

        Set<String> restartedServices = new HashSet<>();
        changedServices.forEach(service -> restartedServices.addAll (servicesDefinition.getDependentServices(service)));

        // also add services simply flagged as needed restart previously
        servicesInstallStatus.getRootKeys().forEach(installStatusFlag -> {
            Pair<String, String> serviceAndNodePair = ServicesInstallStatusWrapper.parseInstallStatusFlag (installStatusFlag);
            String installedService = Objects.requireNonNull(serviceAndNodePair).getKey();
            String status = (String) servicesInstallStatus.getValueForPath(installStatusFlag);
            if (status.equals("restart")) {
                restartedServices.add (installedService);
            }
        });

        feedInRestartService(servicesDefinition, servicesInstallStatus, retCommand, nodesConfig, restartedServices);

        return retCommand;
    }

    static void feedInRestartService(ServicesDefinition servicesDefinition, ServicesInstallStatusWrapper servicesInstallStatus, OperationsCommand retCommand, NodesConfigWrapper nodesConfig, Set<String> restartedServices) {
        for (String restartedService : restartedServices.stream().sorted(servicesDefinition::compareServices).collect(Collectors.toList())) {

            if (!servicesDefinition.getService(restartedService).isMarathon()) {
                for (int nodeNumber : nodesConfig.getNodeNumbers(restartedService)) {

                    String node = nodesConfig.getNodeAddress(nodeNumber);

                    retCommand.addRestartIfNotInstalled(restartedService, node);
                }

            } else {

                if (servicesInstallStatus.isServiceInstalledAnywhere(restartedService)) {
                    retCommand.addRestartIfNotInstalled(restartedService, MARATHON_FLAG);
                }
            }
        }
    }

    public static OperationsCommand createForRestartsOnly (
            ServicesDefinition servicesDefinition,
            NodeRangeResolver nodeRangeResolver,
            String[] servicesToRestart,
            ServicesInstallStatusWrapper servicesInstallStatus,
            NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {

        OperationsCommand retCommand = new OperationsCommand(rawNodesConfig);

        NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges (rawNodesConfig);

        Set<String> restartedServices = new HashSet<>(Arrays.asList(servicesToRestart));

        feedInRestartService(servicesDefinition, servicesInstallStatus, retCommand, nodesConfig, restartedServices);

        return retCommand;
    }

    OperationsCommand(NodesConfigWrapper rawNodesConfig) {
        this.rawNodesConfig = rawNodesConfig;
    }

    public NodesConfigWrapper getRawConfig() {
        return rawNodesConfig;
    }

    void addRestartIfNotInstalled(String service, String node) {
        SerializablePair<String, String> addedPair = new SerializablePair<>(service, node);
        if (!getInstallations().contains(addedPair)) {
            restarts.add(addedPair);
        }
    }

    public List<SerializablePair<String, String>> getRestarts() {
        return restarts;
    }

    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("installations", new JSONArray(toJsonList(getInstallations())));
            put("uninstallations", new JSONArray(toJsonList(getUninstallations())));
            put("restarts", new JSONArray(toJsonList(restarts)));
        }});
    }

    private Collection<Object> toJsonList(List<? extends Pair<String, String>> listOfPairs) {
        return listOfPairs.stream()
                .map((Function<Pair<String, String>, Object>) pair -> {
                    JSONObject ret = new JSONObject();
                    try {
                        ret.put(pair.getKey(), pair.getValue());
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
                .map(Pair::getValue)
                .forEach(retSet::add);
        getUninstallations().stream()
                .map(Pair::getValue)
                .forEach(retSet::add);
        restarts.stream()
                .map(Pair::getValue)
                .forEach(retSet::add);
        return retSet;
    }

    public List<List<Pair<String, String>>> getRestartsInOrder
            (ServicesInstallationSorter servicesInstallationSorter, NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, NodesConfigurationException {
        return servicesInstallationSorter.orderOperations (getRestarts(), nodesConfig);
    }

    public List<List<Pair<String, String>>> getInstallationsInOrder
            (ServicesInstallationSorter servicesInstallationSorter, NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, NodesConfigurationException {
        return servicesInstallationSorter.orderOperations (getInstallations(), nodesConfig);
    }

    public List<List<Pair<String, String>>> getUninstallationsInOrder
            (ServicesInstallationSorter servicesInstallationSorter, NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, NodesConfigurationException {
        List<List<Pair<String, String>>> orderedUninstallations = servicesInstallationSorter.orderOperations (getUninstallations(), nodesConfig);
        Collections.reverse(orderedUninstallations);
        return orderedUninstallations;
    }

    public List<Pair<String, String>> getAllOperationsInOrder
            (OperationsContext context)
            throws ServiceDefinitionException, NodesConfigurationException {

        List<Pair<String, String>> allOpList = new ArrayList<>();

        getInstallationsInOrder(context.getServicesInstallationSorter(), context.getNodesConfig()).forEach(allOpList::addAll);
        getUninstallationsInOrder(context.getServicesInstallationSorter(), context.getNodesConfig()).forEach(allOpList::addAll);
        getRestartsInOrder(context.getServicesInstallationSorter(), context.getNodesConfig()).forEach(allOpList::addAll);

        return allOpList;
    }
}
