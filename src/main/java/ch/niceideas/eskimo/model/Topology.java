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

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.NodesConfigWrapper.ParsedNodesConfigProperty;
import ch.niceideas.eskimo.services.*;
import org.apache.log4j.Logger;
import org.json.JSONException;

import java.util.*;
import java.util.stream.Collectors;

public class Topology {

    private static final Logger logger = Logger.getLogger(Topology.class);

    public static final String MASTER_PREFIX = "MASTER_";
    public static final String SELF_MASTER_PREFIX = "SELF_MASTER_";
    public static final String NODE_NBR_PREFIX = "NODE_NBR_";

    private final Map<String, String> definedMasters = new HashMap<>();
    private final Map<String, Map<String, String>> additionalEnvironment = new HashMap<>();

    public static ParsedNodesConfigProperty parseKeyToServiceConfig (String key, NodesConfigWrapper nodesConfig)
            throws NodesConfigurationException {

        ParsedNodesConfigProperty property = NodesConfigWrapper.parseProperty(key);
        if (property == null) {
            throw new NodesConfigurationException(("Could not parse service config key " + key));
        }

        int nodeNbr = getNodeNbr(key, nodesConfig, property);

        return new ParsedNodesConfigProperty(property.getServiceName(), nodeNbr > -1 ? nodeNbr : null);
    }

    public static int getNodeNbr(String key, NodesConfigWrapper nodesConfig, ParsedNodesConfigProperty property) throws NodesConfigurationException {

        try {
            if (property != null && property.getNodeNumber() != null) {
                return property.getNodeNumber().intValue();
            }

            return Integer.parseInt((String) nodesConfig.getValueForPath(key));
        } catch (JSONException e) {
            logger.error (e, e);
            throw new NodesConfigurationException (e);
        }
    }

    public static Topology create(
            NodesConfigWrapper nodesConfig, MarathonServicesConfigWrapper marathonConfig,
            Set<String> deadIps, ServicesDefinition servicesDefinition, String contextPath, String currentNodeIpAddress)
            throws ServiceDefinitionException, NodesConfigurationException {

        Topology topology = new Topology();

        try {
            // Define master for standard services
            for (String key : nodesConfig.getServiceKeys())  {

                ParsedNodesConfigProperty result = parseKeyToServiceConfig (key, nodesConfig);

                Service service = servicesDefinition.getService(result.getServiceName());
                if (service == null) {
                    throw new NodesConfigurationException("Could not find any service definition matching " + result.getServiceName());
                }

                topology.defineMasters(service, deadIps, result.getNodeNumber(), nodesConfig);

                topology.defineAdditionalEnvionment(service, servicesDefinition, contextPath, result.getNodeNumber(), nodesConfig);
            }

            // Define master for marathon services
            if (currentNodeIpAddress != null && marathonConfig != null) {
                for (String key : marathonConfig.getEnabledServices()) {

                    Service service = servicesDefinition.getService(key);
                    if (service == null) {
                        throw new NodesConfigurationException("Could not find any service definition matching " + key);
                    }

                    int currentNodeNumber = nodesConfig.getNodeNumber(currentNodeIpAddress);

                    topology.defineMasters(service, deadIps, currentNodeNumber, nodesConfig);
                }
            }

        } catch (JSONException | FileException | SetupException | SystemException e) {
            logger.error (e, e);
            throw new NodesConfigurationException(e);
        }

        return topology;
    }

    private void defineAdditionalEnvionment (
            Service service, ServicesDefinition servicesDefinition, String contextPath, int nodeNbr, NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, FileException, SetupException {

        String ipAddress = nodesConfig.getNodeAddress (nodeNbr);

        for (String addEnv : service.getAdditionalEnvironment()) {
            Map<String, String> addEnvForService = additionalEnvironment.computeIfAbsent(service.getName(), k -> new HashMap<>());

            if (addEnv.equals("SERVICE_NUMBER_0_BASED") || addEnv.equals("SERVICE_NUMBER_1_BASED")) {

                servicesDefinition.executeInEnvironmentLock(persistentEnvironment -> {

                    String variableName = NODE_NBR_PREFIX + service.getName().toUpperCase().toUpperCase()+"_"+ipAddress.replace(".", "");

                    String varValue = (String) persistentEnvironment.getValueForPath(variableName);
                    if (StringUtils.isBlank(varValue)) {

                        // we just start from 1  and increment it as long as that number is already taken
                        int counter = addEnv.equals("SERVICE_NUMBER_1_BASED") ? 1 : 0;

                        do {
                            boolean alreadyTaken = false;
                            for (String key : persistentEnvironment.getRootKeys()) {
                                if (key.startsWith(NODE_NBR_PREFIX + service.getName().toUpperCase().toUpperCase() + "_")) {
                                    int value = Integer.parseInt((String)persistentEnvironment.getValueForPath(key));
                                    if (value >= counter) {
                                        alreadyTaken = true;
                                        break;
                                    }
                                }
                            }
                            if (!alreadyTaken) {
                                break;
                            }
                            counter++;
                        } while (true);

                        varValue = ""+counter;
                        persistentEnvironment.setValueForPath(variableName, varValue);
                    }

                    addEnvForService.put(variableName, varValue);
                });

            } else if (addEnv.startsWith("ALL_NODES_LIST_")) {

                String serviceToList = addEnv.substring("ALL_NODES_LIST_".length());

                String varName = addEnv.replace("-", "_");

                String allAddresses = String.join(",", nodesConfig.getAllNodeAddressesWithService(serviceToList).toArray(new String[0]));

                if (StringUtils.isNotBlank(allAddresses)) {
                    addEnvForService.put(varName, allAddresses);
                }

            } else if (addEnv.equals("CONTEXT_PATH")) {

                if (StringUtils.isNotBlank(contextPath)) {

                    // remove leading and trailing slashes if any
                    String contextPathVar = contextPath.startsWith("/") ? contextPath.substring(1) : contextPath;
                    if (contextPathVar.endsWith("/")) {
                        contextPathVar = contextPathVar.substring(0, contextPathVar.length() - 1);
                    }

                    addEnvForService.put (addEnv, contextPathVar);
                }
            }
        }
    }

    private void defineMasters(Service service, Set<String> deadIps, int nodeNbr, NodesConfigWrapper nodesConfig)
            throws NodesConfigurationException, ServiceDefinitionException {
        for (Dependency dep : service.getDependencies()) {
            defineMasters (dep, deadIps, service, nodeNbr, nodesConfig);
        }

    }
    private void defineMasters(Dependency dep, Set<String> deadIps, Service service, int nodeNbr, NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, NodesConfigurationException {

        Set<String> otherMasters = new HashSet<>();
        String ipAddress = nodesConfig.getNodeAddress(nodeNbr);

        switch (dep.getMes()) {

            case FIRST_NODE:
                for (int i = 1; i <= dep.getNumberOfMasters(); i++) {
                    String masterIp = findFirstOtherServiceIP(nodesConfig, deadIps, dep.getMasterService(), otherMasters);
                    masterIp = handleMissingMaster(dep, service, masterIp, i);
                    if (StringUtils.isNotBlank(masterIp)) {
                        definedMasters.put(MASTER_PREFIX + getVariableName(dep) + "_" + i, masterIp);
                        otherMasters.add(masterIp);
                    }
                }
                break;

            case SAME_NODE_OR_RANDOM:
                if ( dep.getNumberOfMasters() > 1) {
                    throw new ServiceDefinitionException ("Service " + service.getName() + " defined several master required. This is unsupported for SAME_NODE_OR_RANDOM");
                }

                String multiServiceValue = (String) nodesConfig.getValueForPath(dep.getMasterService() + nodeNbr);
                if (StringUtils.isNotBlank(multiServiceValue)) {
                    definedMasters.put(SELF_MASTER_PREFIX + getVariableName(dep)+"_"+ipAddress.replace(".", ""), ipAddress);
                } else {
                    String uniqueServiceNbrString = (String) nodesConfig.getValueForPath(dep.getMasterService());
                    if (StringUtils.isNotBlank(uniqueServiceNbrString)) {
                        definedMasters.put(SELF_MASTER_PREFIX + getVariableName(dep)+"_"+ipAddress.replace(".", ""), ipAddress);
                    } else {
                        String masterIp = findFirstServiceIP(nodesConfig, deadIps, dep.getMasterService());
                        masterIp = handleMissingMaster(dep, service, masterIp);
                        if (StringUtils.isNotBlank(masterIp)) {
                            definedMasters.put(SELF_MASTER_PREFIX + getVariableName(dep) + "_" + ipAddress.replace(".", ""), masterIp);
                        }
                    }
                }
                break;

            case RANDOM:
                for (int i = 1; i <= dep.getNumberOfMasters(); i++) {
                    String masterIp = findRandomOtherServiceIP(nodesConfig, deadIps, dep.getMasterService(), otherMasters);
                    masterIp = handleMissingMaster(dep, service, masterIp, i);
                    if (StringUtils.isNotBlank(masterIp)) {
                        definedMasters.put(MASTER_PREFIX + getVariableName(dep) + "_" + i, masterIp);
                        otherMasters.add(masterIp);
                    }
                }
                break;

            case RANDOM_NODE_AFTER:
            case RANDOM_NODE_AFTER_OR_SAME:
                if (service.isMarathon()) {
                    throw new ServiceDefinitionException ("Service " + service.getName() + " defines a RANDOM_NODE_AFTER dependency which is not supported for marathon services");
                }
                if (dep.getNumberOfMasters() > 1) {
                    throw new ServiceDefinitionException ("Service " + service.getName() + " defined several master required. This is unsupported for RANDOM_NODE_AFTER");
                }

                String masterIp = findRandomServiceIPAfter(nodesConfig, deadIps, dep.getMasterService(), nodeNbr);

                if (dep.getMes().equals(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME) && StringUtils.isBlank(masterIp)) {
                    masterIp = findRandomOtherServiceIP(nodesConfig, deadIps, dep.getMasterService(), otherMasters);
                }
                if (StringUtils.isNotBlank(masterIp)) {
                    masterIp = handleMissingMaster(dep, service, masterIp);
                    definedMasters.put(MASTER_PREFIX + getVariableName(dep) + "_" + ipAddress.replace(".", ""), masterIp);
                }
                break;

            case SAME_NODE:
            default:
                if (service.isMarathon()) {
                    throw new ServiceDefinitionException ("Service " + service.getName() + " defines a SAME_NODE dependency which is not supported for marathon services");
                }
                // do nothing here. WIll be enforced by checker.
                break;
        }

    }

    String getVariableName(Dependency dep) {
        return dep.getMasterService().toUpperCase().replace("-", "_");
    }

    private String handleMissingMaster(Dependency dep, Service service, String masterIp, int countOfMaster) throws NodesConfigurationException {
        if (masterIp == null) {
            if (!dep.isMandatory()) {
                // if none could be found, then well ... none could be found
                masterIp = "";
            } else {
                throw new NodesConfigurationException("Dependency " + dep.getMasterService() + " for service " + service.getName() + " could not found occurence " + countOfMaster);
            }
        }
        return masterIp;
    }

    private String handleMissingMaster(Dependency dep, Service service, String masterIp) throws NodesConfigurationException {
        if (masterIp == null) {
            if (!dep.isMandatory()) {
                // if none could be found, then well ... none could be found
                masterIp = "";
            } else {
                throw new NodesConfigurationException("Dependency " + dep.getMasterService() + " for service " + service.getName() + " could not be found");
            }
        }
        return masterIp;
    }


    private String findFirstServiceIP(NodesConfigWrapper nodesConfig, Set<String> deadIps, String serviceName) throws NodesConfigurationException {
        int nodeNbr = Integer.MAX_VALUE;

        for (int candidateNbr : nodesConfig.getNodeNumbers(serviceName)) {
            String ipAddress = findNodeIp (nodesConfig, candidateNbr);
            if (candidateNbr < nodeNbr && !deadIps.contains(ipAddress)) {
                nodeNbr = candidateNbr;
            }
        }

        if (nodeNbr == Integer.MAX_VALUE) {
            return null;
        }

        return findNodeIp(nodesConfig, nodeNbr);
    }

    private String findFirstOtherServiceIP(
            NodesConfigWrapper nodesConfig, Set<String> deadIps, String serviceName, Set<String> existingMasters)
            throws NodesConfigurationException {
        int nodeNbr = Integer.MAX_VALUE;

        for (int candidateNbr : nodesConfig.getNodeNumbers(serviceName)) {
            String otherIp = nodesConfig.getNodeAddress(candidateNbr);
            if (!existingMasters.contains(otherIp)
                    && !deadIps.contains(otherIp)
                    && (candidateNbr < nodeNbr)) {
                nodeNbr = candidateNbr;
            }
        }

        if (nodeNbr == Integer.MAX_VALUE) {
            return null;
        }
        return findNodeIp(nodesConfig, nodeNbr);
    }

    private String findRandomOtherServiceIP(NodesConfigWrapper nodesConfig, Set<String> deadIps, String serviceName, Set<String> existingMasters) {

        // Try to find any other number running ElasticSearch
        for (int otherNbr : nodesConfig.getNodeNumbers(serviceName)) {
            String otherIp = nodesConfig.getNodeAddress(otherNbr);
            if (!existingMasters.contains(otherIp) && !deadIps.contains(otherIp)) {
                return otherIp;
            }
        }

        return null;
    }

    private String findRandomServiceIPAfter(NodesConfigWrapper nodesConfig, Set<String> deadIps, String serviceName, int currentNodeNumber)
            throws NodesConfigurationException {
        int masterNumber = -1;

        for (int otherNbr : nodesConfig.getNodeNumbers(serviceName)) {
            String ipAddress = findNodeIp (nodesConfig, otherNbr);
            if (otherNbr > currentNodeNumber && (masterNumber == -1 || otherNbr < masterNumber) // try to find closest one (next in a chain)
                    && !deadIps.contains(ipAddress)) {
                masterNumber = otherNbr;
            }
        }

        if (masterNumber == -1) {
            Set<String> existingMasters = new HashSet<>();
            existingMasters.add (findNodeIp(nodesConfig, currentNodeNumber));
            return findFirstOtherServiceIP(nodesConfig, deadIps, serviceName, existingMasters);
        } else {
            return findNodeIp(nodesConfig, masterNumber);
        }
    }

    private String findNodeIp(NodesConfigWrapper nodesConfig, int nodeNumber) throws NodesConfigurationException {
        if (nodeNumber > -1) {
            // return IP address correspondoing to master number
            String ipAddress = nodesConfig.getNodeAddress(nodeNumber);
            if (StringUtils.isBlank(ipAddress)) {
                throw new NodesConfigurationException("Inconsistency : could not find IP of " + nodeNumber);
            }

            return ipAddress;
        }
        return null;
    }

    public String[] getMasters(Service service) {

        String variableName = service.getName().toUpperCase().replace("-", "_");

        List<String> retMasters = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String ipAddress = definedMasters.get(MASTER_PREFIX + variableName + "_" + i);
            if (StringUtils.isNotBlank(ipAddress)) {
                retMasters.add(ipAddress);
            }
        }

        return retMasters.toArray(new String[0]);
    }

    private void appendExport (StringBuilder sb, String variable, String value) {
        sb.append("export ");
        sb.append(variable);
        sb.append("=");
        sb.append(value);
        sb.append("\n");
    }

    public String getTopologyScript() {
        StringBuilder sb = new StringBuilder();
        for (String master : definedMasters.keySet().stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList())) {
            appendExport (sb, master, definedMasters.get(master));
        }
        return sb.toString();
    }

    public String getTopologyScriptForNode(NodesConfigWrapper nodesConfig, MemoryModel memoryModel, int nodeNbr) throws NodesConfigurationException {
        StringBuilder sb = new StringBuilder();
        sb.append("#Topology\n");
        sb.append(getTopologyScript());

        // find all services on node
        Set<String> serviceNames = new HashSet<>();
        for (String key : nodesConfig.getRootKeys()) {

            ParsedNodesConfigProperty property = NodesConfigWrapper.parseProperty(key);

            if (property == null) {
                throw new NodesConfigurationException("Could not parse service config key " + key);
            }

            int otherNodeNbr = getNodeNbr(key, nodesConfig, property);

            if (otherNodeNbr == nodeNbr) {
                serviceNames.add (property.getServiceName());
            }
        }

        sb.append("\n#Additional Environment\n");
        for (String serviceName : serviceNames.stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList())) {
            if (additionalEnvironment.get(serviceName) != null) {
                for (String additionalProp : additionalEnvironment.get(serviceName).keySet()) {
                    appendExport (sb, additionalProp, additionalEnvironment.get(serviceName).get(additionalProp));
                }
            }
        }

        // Add self variable
        sb.append("\n#Self identification\n");
        String ipAddress = nodesConfig.getNodeAddress(nodeNbr);
        if (StringUtils.isBlank(ipAddress)) {
            throw new NodesConfigurationException("No IP address found for node number " + nodeNbr);
        }
        appendExport(sb, "SELF_IP_ADDRESS", ipAddress);

        appendExport(sb, "SELF_NODE_NUMBER", ""+nodeNbr);

        // memory management
        Map<String, Long> memorySettings = memoryModel.getModelForNode(nodesConfig, nodeNbr);
        if (memorySettings != null && !memorySettings.isEmpty()) {
            sb.append("\n#Memory Management\n");

            List<String> memoryList = memorySettings.keySet().stream().sorted().collect(Collectors.toList());
            for (String service : memoryList) {

                appendExport(sb, "MEMORY_"+service.toUpperCase().replace("-", "_"), ""+memorySettings.get(service));
            }
        }

        return sb.toString();
    }

}
