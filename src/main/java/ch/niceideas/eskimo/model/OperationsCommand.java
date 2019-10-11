/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.eskimo.services.*;
import ch.niceideas.common.utils.Pair;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OperationsCommand {

    private static final Logger logger = Logger.getLogger(OperationsCommand.class);

    private final NodesConfigWrapper rawNodesConfig;

    private List<Pair<String, String>> installations = new ArrayList<>();
    private List<Pair<String, String>> uninstallations = new ArrayList<>();
    private List<Pair<String, String>> restarts = new ArrayList<>();

    public static OperationsCommand create (
            ServicesDefinition servicesDefinition,
            NodeRangeResolver nodeRangeResolver,
            ServicesInstallStatusWrapper servicesInstallStatus,
            NodesConfigWrapper rawNodesConfig)
            throws JSONException, NodesConfigurationException {

        OperationsCommand retCommand = new OperationsCommand(rawNodesConfig);

        NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges (rawNodesConfig);

        // 1. Find out about services that need to be installed
        for (String service : servicesDefinition.listServicesOrderedByDependencies()) {
            for (int nodeNumber : nodesConfig.getNodeNumbers(service)) {
                if (nodeNumber > -1) {
                    String ipAddress = nodesConfig.getNodeAddress(nodeNumber);
                    String nodeName = ipAddress.replaceAll("\\.", "-");

                    if (!servicesInstallStatus.isServiceOK(service, nodeName)) {

                        retCommand.addInstallation(service, ipAddress);
                    }
                }
            }
        }

        // 2. Find out about services that need to be uninstalled
        for (String installation : servicesInstallStatus.getRootKeys()) {

            String installedService = installation.substring(0, installation.indexOf("_installed_on_IP_"));

            String nodeName =  installation.substring(installation.indexOf("_installed_on_IP_") + "_installed_on_IP_".length());
            String ipAddress = nodeName.replaceAll("-", "\\.");

            try {
                int nodeNbr = nodesConfig.getNodeNumber(ipAddress);
                boolean found = false;

                // search it in config
                for (int nodeNumber : nodesConfig.getNodeNumbers(installedService)) {

                    if (nodeNumber == nodeNbr) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    retCommand.addUninstallation(installedService, ipAddress);
                }

            } catch (SystemException e) {
                logger.debug (e, e);
                retCommand.addUninstallation(installedService, ipAddress);
            }
        }

        // 3. If a services changed, dependent services need to be restarted
        Set<String> changedServices = new HashSet<>();

        changedServices.addAll (retCommand.getInstallations().stream().map(Pair::getKey).collect(Collectors.toList()));
        changedServices.addAll (retCommand.getUninstallations().stream().map(Pair::getKey).collect(Collectors.toList()));

        Set<String> restartedServices = new HashSet<>();
        changedServices.forEach(service -> restartedServices.addAll (servicesDefinition.getDependentServices(service)));

        for (String restartedService : restartedServices.stream().sorted(servicesDefinition::compareServices).collect(Collectors.toList())) {
            for (int nodeNumber : nodesConfig.getNodeNumbers(restartedService)) {

                if (nodeNumber > -1) {
                    String ipAddress = nodesConfig.getNodeAddress(nodeNumber);

                    retCommand.addRestart(restartedService, ipAddress);
                }
            }
        }

        return retCommand;
    }

    public static OperationsCommand createForRestartsOnly (
            ServicesDefinition servicesDefinition,
            NodeRangeResolver nodeRangeResolver,
            String[] servicesToRestart,
            NodesConfigWrapper rawNodesConfig)
            throws JSONException, NodesConfigurationException {

        OperationsCommand retCommand = new OperationsCommand(rawNodesConfig);

        NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges (rawNodesConfig);

        for (String restartedService : Arrays.stream(servicesToRestart).sorted(servicesDefinition::compareServices).collect(Collectors.toList())) {
            for (int nodeNumber : nodesConfig.getNodeNumbers(restartedService)) {

                if (nodeNumber > -1) {
                    String ipAddress = nodesConfig.getNodeAddress(nodeNumber);

                    retCommand.addRestart(restartedService, ipAddress);
                }
            }
        }

        return retCommand;

    }

    OperationsCommand(NodesConfigWrapper rawNodesConfig) {
        this.rawNodesConfig = rawNodesConfig;
    }

    public NodesConfigWrapper getRawConfig() {
        return rawNodesConfig;
    }

    void addInstallation(String service, String ipAddress) {
        installations.add(new Pair<>(service, ipAddress));
    }

    void addUninstallation(String service, String ipAddress) {
        uninstallations.add(new Pair<>(service, ipAddress));
    }

    void addRestart(String service, String ipAddress) {
        Pair<String, String> addedPair = new Pair<>(service, ipAddress);
        if (!installations.contains(addedPair)) {
            restarts.add(addedPair);
        }
    }

    public List<Pair<String, String>> getInstallations() {
        return installations;
    }

    public List<Pair<String, String>> getUninstallations() {
        return uninstallations;
    }

    public List<Pair<String, String>> getRestarts() {
        return restarts;
    }

    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("installations", new JSONArray(toJsonList(installations)));
            put("uninstallations", new JSONArray(toJsonList(uninstallations)));
            put("restarts", new JSONArray(toJsonList(restarts)));
        }});
    }

    private Collection<Object> toJsonList(List<Pair<String, String>> listOfPairs) {
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

    public Set<String> getAllIpAddresses() {
        Set<String> retSet = new HashSet<>();
        installations.stream()
                .map(pair -> pair.getValue())
                .forEach(retSet::add);
        uninstallations.stream()
                .map(pair -> pair.getValue())
                .forEach(retSet::add);
        restarts.stream()
                .map(pair -> pair.getValue())
                .forEach(retSet::add);
        return retSet;
    }
}
