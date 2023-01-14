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

package ch.niceideas.eskimo.model;

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.NodesConfigWrapper.ParsedNodesConfigProperty;
import ch.niceideas.eskimo.model.service.Dependency;
import ch.niceideas.eskimo.model.service.MasterElectionStrategy;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.model.service.Service;
import ch.niceideas.eskimo.services.ServiceDefinitionException;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SystemException;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import org.apache.log4j.Logger;
import org.json.JSONException;

import java.util.*;

public class Topology {

    private static final Logger logger = Logger.getLogger(Topology.class);

    public static final String MASTER_PREFIX = "MASTER_";
    public static final String SELF_MASTER_PREFIX = "SELF_MASTER_";
    public static final String NODE_NBR_PREFIX = "NODE_NBR_";
    public static final String ALL_NODES_LIST_PREFIX = "ALL_NODES_LIST_";
    public static final String ALL_NODES_LIST = "ALL_NODES_LIST";
    public static final String SERVICE_NUMBER_0_BASED = "SERVICE_NUMBER_0_BASED";
    public static final String SERVICE_NUMBER_1_BASED = "SERVICE_NUMBER_1_BASED";
    public static final String CONTEXT_PATH = "CONTEXT_PATH";
    public static final String SERVICE = "Service";

    private final Map<String, String> definedMasters = new HashMap<>();
    private final Map<String, Map<String, String>> additionalEnvironment = new HashMap<>();
    private final String contextPath;

    Topology() {
        this.contextPath = null;
    }

    public Topology(String contextPath) {
        this.contextPath = contextPath;
    }

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
                return property.getNodeNumber();
            }

            return Integer.parseInt((String) nodesConfig.getValueForPath(key));
        } catch (JSONException e) {
            logger.error (e, e);
            throw new NodesConfigurationException (e);
        }
    }

    public static Topology create(
            NodesConfigWrapper nodesConfig, KubernetesServicesConfigWrapper kubeServicesConfig,
            ServicesDefinition servicesDefinition, String contextPath, String currentNode)
            throws ServiceDefinitionException, NodesConfigurationException {

        Topology topology = new Topology(contextPath);

        try {
            // Define master for standard services
            for (String key : nodesConfig.getServiceKeys())  {

                ParsedNodesConfigProperty result = parseKeyToServiceConfig (key, nodesConfig);

                Service service = servicesDefinition.getService(result.getServiceName());
                if (service == null) {
                    throw new NodesConfigurationException("Could not find any service definition matching " + result.getServiceName());
                }

                topology.defineMasters(servicesDefinition, service, result.getNodeNumber(), nodesConfig, kubeServicesConfig);

                topology.defineAdditionalEnvionment(service, servicesDefinition, contextPath, result.getNodeNumber(), nodesConfig);
            }

            // Define master for kubernetes services
            if (currentNode != null && kubeServicesConfig != null) {
                for (String key : kubeServicesConfig.getEnabledServices()) {

                    Service service = servicesDefinition.getService(key);
                    if (service == null) {
                        throw new NodesConfigurationException("Could not find any service definition matching " + key);
                    }

                    int currentNodeNumber = nodesConfig.getNodeNumber(currentNode);

                    topology.defineMasters(servicesDefinition, service, currentNodeNumber, nodesConfig, kubeServicesConfig);
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

        String node = nodesConfig.getNodeAddress (nodeNbr);

        for (String addEnv : service.getAdditionalEnvironment()) {
            Map<String, String> addEnvForService = additionalEnvironment.computeIfAbsent(service.getName(), k -> new HashMap<>());

            if (addEnv.equals(SERVICE_NUMBER_0_BASED) || addEnv.equals(SERVICE_NUMBER_1_BASED)) {

                servicesDefinition.executeInEnvironmentLock(persistentEnvironment -> {

                    String variableName = NODE_NBR_PREFIX
                            + service.getName().toUpperCase().toUpperCase().replace("-", "_")
                            + "_"
                            + node.replace(".", "");

                    String varValue = (String) persistentEnvironment.getValueForPath(variableName);
                    if (StringUtils.isBlank(varValue)) {

                        // we just start from 1  and increment it as long as that number is already taken
                        int counter = addEnv.equals(SERVICE_NUMBER_1_BASED) ? 1 : 0;

                        do {
                            boolean alreadyTaken = false;
                            for (String key : persistentEnvironment.getRootKeys()) {
                                if (key.startsWith(NODE_NBR_PREFIX + service.getName().toUpperCase().toUpperCase().replace("-", "_") + "_")) {
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

            } else if (addEnv.startsWith(ALL_NODES_LIST_PREFIX)) {

                String serviceToList = addEnv.substring(ALL_NODES_LIST_PREFIX.length());

                String varName = addEnv.replace("-", "_");

                List<String> allNodeList = new ArrayList<>(nodesConfig.getAllNodeAddressesWithService(serviceToList));
                Collections.sort(allNodeList); // THis is absolutely key, the order needs to be predictable
                String allNodes = String.join(",", allNodeList.toArray(new String[0]));

                if (StringUtils.isNotBlank(allNodes)) {
                    addEnvForService.put(varName, allNodes);
                } else {
                    logger.warn ("No nodes found for " + serviceToList);
                }

            } else if (addEnv.equals(CONTEXT_PATH)) {

                if (StringUtils.isNotBlank(contextPath)) {

                    // remove leading and trailing slashes if any
                    String contextPathVar = FileUtils.noSlashEnd(FileUtils.noSlashStart(contextPath));
                    addEnvForService.put (addEnv, contextPathVar);
                }
            }
        }
    }

    private void defineMasters(ServicesDefinition servicesDefinition, Service service, int nodeNbr, NodesConfigWrapper nodesConfig,
                               KubernetesServicesConfigWrapper kubeServicesConfig)
            throws NodesConfigurationException, ServiceDefinitionException {
        for (Dependency dep : service.getDependencies()) {

            Service masterService = servicesDefinition.getService(dep.getMasterService());
            if (masterService.isKubernetes()) {

                if (!service.isKubernetes()) {
                    throw new ServiceDefinitionException (SERVICE + " " + service.getName()
                            + " defines a dependency on a kube service " + masterService.getName() + " which is not supported.");
                }

                if (kubeServicesConfig == null || !kubeServicesConfig.isServiceInstallRequired(masterService.getName())) {
                    throw new ServiceDefinitionException (SERVICE + " " + service.getName()
                            + " defines a dependency on another kube service " + masterService.getName() + " but that service is not going to be installed.");
                }
                // XXX Dependeny on Kube-master is enforced already, we're left with checking that the dependency definition is not crazy
                switch (dep.getMes()) {
                    case SAME_NODE_OR_RANDOM:
                    case RANDOM_NODE_AFTER:
                    case RANDOM_NODE_AFTER_OR_SAME:
                    case SAME_NODE:
                        throw new ServiceDefinitionException (SERVICE + " " + service.getName()
                                + " defines a dependency on another kube service " + masterService.getName() + " if type " + dep.getMes() + " which is not suppored");
                    default:
                        break;
                }
            } else {
                defineMasters (dep, service, nodeNbr, nodesConfig);
            }
        }

    }
    private void defineMasters(Dependency dep, Service service, int nodeNbr,
                    NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, NodesConfigurationException {

        Set<String> otherMasters = new HashSet<>();
        String node = nodesConfig.getNodeAddress(nodeNbr);

        switch (dep.getMes()) {

            case FIRST_NODE:
                for (int i = 1; i <= dep.getNumberOfMasters(); i++) {
                    String masterIp = findFirstOtherServiceNode(nodesConfig, dep.getMasterService(), otherMasters);
                    masterIp = handleMissingMaster(nodesConfig, dep, service, masterIp, i);
                    if (StringUtils.isNotBlank(masterIp)) {
                        definedMasters.put(MASTER_PREFIX + getVariableName(dep) + "_" + i, masterIp);
                        otherMasters.add(masterIp);
                    }
                }
                break;

            case SAME_NODE_OR_RANDOM:
                if ( dep.getNumberOfMasters() > 1) {
                    throw new ServiceDefinitionException (SERVICE + " " + service.getName() + " defined several master required. This is unsupported for SAME_NODE_OR_RANDOM");
                }

                String multiServiceValue = (String) nodesConfig.getValueForPath(dep.getMasterService() + nodeNbr);
                if (StringUtils.isNotBlank(multiServiceValue)) {
                    definedMasters.put(SELF_MASTER_PREFIX + getVariableName(dep)+"_"+node.replace(".", ""), node);
                } else {
                    String uniqueServiceNbrString = (String) nodesConfig.getValueForPath(dep.getMasterService());
                    if (StringUtils.isNotBlank(uniqueServiceNbrString)) {
                        definedMasters.put(SELF_MASTER_PREFIX + getVariableName(dep)+"_"+node.replace(".", ""), node);
                    } else {
                        String masterIp = findFirstServiceNode(nodesConfig, dep.getMasterService());
                        masterIp = handleMissingMaster(nodesConfig, dep, service, masterIp);
                        if (StringUtils.isNotBlank(masterIp)) {
                            definedMasters.put(SELF_MASTER_PREFIX + getVariableName(dep) + "_" + node.replace(".", ""), masterIp);
                        }
                    }
                }
                break;

            case RANDOM:
                for (int i = 1; i <= dep.getNumberOfMasters(); i++) {
                    String masterIp = findRandomOtherServiceNode(nodesConfig, dep.getMasterService(), otherMasters);
                    masterIp = handleMissingMaster(nodesConfig, dep, service, masterIp, i);
                    if (StringUtils.isNotBlank(masterIp)) {
                        definedMasters.put(MASTER_PREFIX + getVariableName(dep) + "_" + i, masterIp);
                        otherMasters.add(masterIp);
                    }
                }
                break;

            case RANDOM_NODE_AFTER:
            case RANDOM_NODE_AFTER_OR_SAME:
                if (service.isKubernetes()) {
                    throw new ServiceDefinitionException (SERVICE + " " + service.getName()
                            + " defines a " + dep.getMes() + " dependency on " + dep.getMasterService()+ ", which is not supported for kubernetes services");
                }
                if (dep.getNumberOfMasters() > 1) {
                    throw new ServiceDefinitionException (SERVICE + " " + service.getName() + " defined several master required. This is unsupported for RANDOM_NODE_AFTER");
                }

                String masterIp = findRandomServiceNodeAfter(nodesConfig, dep.getMasterService(), nodeNbr);

                if (dep.getMes().equals(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME) && StringUtils.isBlank(masterIp)) {
                    masterIp = findRandomOtherServiceNode(nodesConfig, dep.getMasterService(), otherMasters);
                }
                if (StringUtils.isNotBlank(masterIp)) {
                    masterIp = handleMissingMaster(nodesConfig, dep, service, masterIp);
                    definedMasters.put(MASTER_PREFIX + getVariableName(dep) + "_" + node.replace(".", ""), masterIp);
                }
                break;

            case SAME_NODE:
                if (service.isKubernetes()) {
                    throw new ServiceDefinitionException (SERVICE + " " + service.getName() + " defines a SAME_NODE dependency on "
                            + dep.getMasterService() + ", which is not supported for kubernetes services");
                }
                break;

            default:
                // do nothing here. WIll be enforced by checker.
                break;
        }

    }

    String getVariableName(Dependency dep) {
        return dep.getMasterService().toUpperCase().replace("-", "_");
    }

    private String handleMissingMaster(ConfigurationOwner nodesConfig, Dependency dep, Service service, String masterIp, int countOfMaster) throws NodesConfigurationException {
        return handleMissingMasterInternal (nodesConfig, dep, masterIp,
                "Dependency " + dep.getMasterService() + " for service " + service.getName() + " could not found occurence " + countOfMaster);
    }

    private String handleMissingMaster(ConfigurationOwner nodesConfig, Dependency dep, Service service, String masterIp) throws NodesConfigurationException {
        return handleMissingMasterInternal (nodesConfig, dep, masterIp,
                "Dependency " + dep.getMasterService() + " for service" + service.getName() + " could not be found");
    }

    private String handleMissingMasterInternal(ConfigurationOwner nodesConfig, Dependency dep, String masterIp, String message) throws NodesConfigurationException {
        if (masterIp == null) {
            if (!dep.isMandatory(nodesConfig)) {
                // if none could be found, then well ... none could be found
                masterIp = "";
            } else {
                throw new NodesConfigurationException(message);
            }
        }
        return masterIp;
    }


    private String findFirstServiceNode(NodesConfigWrapper nodesConfig, String serviceName) throws NodesConfigurationException {
        int nodeNbr = Integer.MAX_VALUE;

        for (int candidateNbr : nodesConfig.getNodeNumbers(serviceName)) {
            if (candidateNbr < nodeNbr) {
                nodeNbr = candidateNbr;
            }
        }

        if (nodeNbr == Integer.MAX_VALUE) {
            return null;
        }

        return findNodeNumberNode(nodesConfig, nodeNbr);
    }

    private String findFirstOtherServiceNode(
            NodesConfigWrapper nodesConfig, String serviceName, Set<String> existingMasters)
            throws NodesConfigurationException {
        int nodeNbr = Integer.MAX_VALUE;

        for (int candidateNbr : nodesConfig.getNodeNumbers(serviceName)) {
            String otherNode = nodesConfig.getNodeAddress(candidateNbr);
            if (!existingMasters.contains(otherNode)
                    && (candidateNbr < nodeNbr)) {
                nodeNbr = candidateNbr;
            }
        }

        if (nodeNbr == Integer.MAX_VALUE) {
            return null;
        }
        return findNodeNumberNode(nodesConfig, nodeNbr);
    }

    private String findRandomOtherServiceNode(NodesConfigWrapper nodesConfig, String serviceName, Set<String> existingMasters) {

        // Try to find any other node running service
        for (int otherNbr : nodesConfig.getNodeNumbers(serviceName)) {
            String otherNode = nodesConfig.getNodeAddress(otherNbr);
            if (!existingMasters.contains(otherNode)) {
                return otherNode;
            }
        }

        return null;
    }

    private String findRandomServiceNodeAfter(NodesConfigWrapper nodesConfig, String serviceName, int currentNodeNumber)
            throws NodesConfigurationException {
        int masterNumber = -1;

        for (int otherNbr : nodesConfig.getNodeNumbers(serviceName)) {
            if (otherNbr > currentNodeNumber && (masterNumber == -1 || otherNbr < masterNumber) // try to find closest one (next in a chain)
                    ) {
                masterNumber = otherNbr;
            }
        }

        if (masterNumber == -1) {
            Set<String> existingMasters = new HashSet<>();
            existingMasters.add (findNodeNumberNode(nodesConfig, currentNodeNumber));
            return findFirstOtherServiceNode(nodesConfig, serviceName, existingMasters);
        } else {
            return findNodeNumberNode(nodesConfig, masterNumber);
        }
    }

    private String findNodeNumberNode(NodesConfigWrapper nodesConfig, int nodeNumber) throws NodesConfigurationException {
        if (nodeNumber > -1) {
            // return IP address corresponding to master number
            String node = nodesConfig.getNodeAddress(nodeNumber);
            if (StringUtils.isBlank(node)) {
                throw new NodesConfigurationException("Inconsistency : could not find IP of " + nodeNumber);
            }

            return node;
        }
        return null;
    }

    public String[] getMasters(Service service) {

        String variableName = service.getName().toUpperCase().replace("-", "_");

        List<String> retMasters = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String node = definedMasters.get(MASTER_PREFIX + variableName + "_" + i);
            if (StringUtils.isNotBlank(node)) {
                retMasters.add(node);
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

    public String getTopologyScript(ServicesInstallStatusWrapper serviceInstallStatus) {
        StringBuilder sb = new StringBuilder();

        sb.append("#Topology\n");
        definedMasters.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .forEach(master -> appendExport (sb, master, definedMasters.get(master)));

        if (!serviceInstallStatus.isEmpty()) {

            sb.append("\n#Eskimo installation status\n");

            serviceInstallStatus.getInstalledServicesFlags()
                    .forEach(serviceInstallStatusGlag -> {
                        Pair<String, String> serviceInstall = ServicesInstallStatusWrapper.parseInstallStatusFlag(serviceInstallStatusGlag);
                        if (serviceInstall != null) {
                            String installedService = serviceInstall.getKey();
                            String nodeName = serviceInstall.getValue();
                            appendExport(sb,
                                    "ESKIMO_INSTALLED_" + installedService.replace("-", "_") + "_" + nodeName.replace("-", ""),
                                    serviceInstallStatus.getValueForPathAsString(serviceInstallStatusGlag));
                        }
                    });
        }

        return sb.toString();
    }

    public String getTopologyScriptForNode(
            NodesConfigWrapper nodesConfig,
            KubernetesServicesConfigWrapper kubeConfig,
            ServicesInstallStatusWrapper serviceInstallStatus,
            ServicesDefinition servicesDefinition,
            MemoryModel memoryModel, int nodeNbr)
                    throws NodesConfigurationException, SetupException, FileException {
        StringBuilder sb = new StringBuilder();
        sb.append(getTopologyScript(serviceInstallStatus));

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
        serviceNames.stream()
                .sorted(Comparator.naturalOrder())
                .filter(serviceName -> additionalEnvironment.get(serviceName) != null)
                .forEach(serviceName -> {
                    for (String additionalProp : additionalEnvironment.get(serviceName).keySet()) {
                        appendExport (sb, additionalProp, additionalEnvironment.get(serviceName).get(additionalProp));
                    }
                });

        // Add self variable
        sb.append("\n#Self identification\n");
        String node = nodesConfig.getNodeAddress(nodeNbr);
        if (StringUtils.isBlank(node)) {
            throw new NodesConfigurationException("No Node Address found for node number " + nodeNbr);
        }
        appendExport(sb, "SELF_IP_ADDRESS", node);

        appendExport(sb, "SELF_NODE_NUMBER", ""+nodeNbr);

        appendExport(sb, "ESKIMO_NODE_COUNT", ""+nodesConfig.getNodeAddresses().size());

        if (StringUtils.isNotBlank(contextPath)) {
            appendExport(sb, CONTEXT_PATH, ""+contextPath);
        }

        List<String> allNodeList = new ArrayList<>(nodesConfig.getNodeAddresses());
        Collections.sort(allNodeList); // THis is absolutely key, the order needs to be predictable
        String allNodes = String.join(",", allNodeList.toArray(new String[0]));

        appendExport(sb, ALL_NODES_LIST, allNodes);

        // Kubernetes Topology
        if (servicesDefinition.getKubeMasterService() != null
                && nodesConfig.hasServiceConfigured(servicesDefinition.getKubeMasterService().getName()) &&
                (   nodesConfig.isServiceOnNode(servicesDefinition.getKubeMasterService().getName(), nodeNbr)
                 || nodesConfig.isServiceOnNode(servicesDefinition.getKubeSlaveService().getName(), nodeNbr))) {
            // No. In the end we need kubernetes topology on every node where a kube service might be potentially running
            /*
                && kubeConfig.hasEnabledServices()
                && nodesConfig.isServiceOnNode(KubernetesService.KUBE_MASTER, nodeNbr)) {
             */
            sb.append("\n#Kubernetes Topology\n");

            if (kubeConfig != null) {
                for (String service : kubeConfig.getEnabledServices()) {

                    String cpuSetting = kubeConfig.getCpuSetting(service);
                    if (StringUtils.isNotBlank(cpuSetting)) {
                        appendExport(sb, "ESKIMO_KUBE_REQUEST_" + service.toUpperCase().replace("-", "_") + "_CPU", cpuSetting);
                    }

                    String ramSetting = kubeConfig.getRamSetting(service);
                    if (StringUtils.isNotBlank(ramSetting)) {
                        appendExport(sb, "ESKIMO_KUBE_REQUEST_" + service.toUpperCase().replace("-", "_") + "_RAM", ramSetting);
                    }

                }
            }
        }

        // memory management
        Map<String, Long> memorySettings = memoryModel.getModelForNode(nodesConfig, nodeNbr);
        if (memorySettings != null && !memorySettings.isEmpty()) {
            sb.append("\n#Memory Management\n");

            memorySettings.keySet().stream()
                    .sorted()
                    .forEach(service -> appendExport(sb,
                            "MEMORY_" + service.toUpperCase().replace("-", "_"), "" + memorySettings.get(service)));
        }

        return sb.toString();
    }

}
