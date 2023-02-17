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

import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.NodesConfigWrapper.ParsedNodesConfigProperty;
import ch.niceideas.eskimo.model.service.Dependency;
import ch.niceideas.eskimo.model.service.MasterElectionStrategy;
import ch.niceideas.eskimo.model.service.MemoryModel;
import ch.niceideas.eskimo.model.service.ServiceDefinition;
import ch.niceideas.eskimo.services.ServiceDefinitionException;
import ch.niceideas.eskimo.services.ServicesDefinition;
import ch.niceideas.eskimo.services.SetupException;
import ch.niceideas.eskimo.services.SystemException;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;
import org.json.JSONException;

import java.util.*;
import java.util.stream.Collectors;

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

    private final Map<String, Node> definedMasters = new HashMap<>();
    private final Map<Service, Map<String, String>> additionalEnvironment = new HashMap<>();
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

        return new ParsedNodesConfigProperty(property.getService(), nodeNbr > -1 ? nodeNbr : null);
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
            ServicesDefinition servicesDefinition, String contextPath, Node currentNode)
            throws ServiceDefinitionException, NodesConfigurationException {

        Topology topology = new Topology(contextPath);

        try {
            // Define master for standard services
            for (String key : nodesConfig.getServiceKeys())  {

                ParsedNodesConfigProperty result = parseKeyToServiceConfig (key, nodesConfig);

                ServiceDefinition serviceDef = servicesDefinition.getServiceDefinition(result.getService());
                if (serviceDef == null) {
                    throw new NodesConfigurationException("Could not find any service definition matching " + result.getService());
                }

                topology.defineMasters(servicesDefinition, serviceDef, result.getNodeNumber(), nodesConfig, kubeServicesConfig);

                topology.defineAdditionalEnvionment(serviceDef, servicesDefinition, contextPath, result.getNodeNumber(), nodesConfig);
            }

            // Define master for kubernetes services
            if (currentNode != null && kubeServicesConfig != null) {
                for (Service key : kubeServicesConfig.getEnabledServices()) {

                    ServiceDefinition serviceDef = servicesDefinition.getServiceDefinition(key);
                    if (serviceDef == null) {
                        throw new NodesConfigurationException("Could not find any service definition matching " + key);
                    }

                    int currentNodeNumber = nodesConfig.getNodeNumber(currentNode);

                    topology.defineMasters(servicesDefinition, serviceDef, currentNodeNumber, nodesConfig, kubeServicesConfig);
                }
            }

        } catch (JSONException | FileException | SetupException | SystemException e) {
            logger.error (e, e);
            throw new NodesConfigurationException(e);
        }

        return topology;
    }

    private void defineAdditionalEnvionment (
            ServiceDefinition serviceDef, ServicesDefinition servicesDefinition, String contextPath, int nodeNbr, NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, FileException, SetupException {

        Node node = nodesConfig.getNode (nodeNbr);

        for (String addEnv : serviceDef.getAdditionalEnvironment()) {
            Map<String, String> addEnvForService = additionalEnvironment.computeIfAbsent(serviceDef.toService(), k -> new HashMap<>());

            if (addEnv.equals(SERVICE_NUMBER_0_BASED) || addEnv.equals(SERVICE_NUMBER_1_BASED)) {

                servicesDefinition.executeInEnvironmentLock(persistentEnvironment -> {

                    String variableName = NODE_NBR_PREFIX
                            + serviceDef.toService().getEnv()
                            + "_"
                            + node.getEnv();

                    String varValue = (String) persistentEnvironment.getValueForPath(variableName);
                    if (StringUtils.isBlank(varValue)) {

                        // we just start from 1  and increment it as long as that number is already taken
                        int counter = addEnv.equals(SERVICE_NUMBER_1_BASED) ? 1 : 0;

                        do {
                            boolean alreadyTaken = false;
                            for (String key : persistentEnvironment.getRootKeys()) {
                                if (key.startsWith(NODE_NBR_PREFIX + serviceDef.getName().toUpperCase().toUpperCase().replace("-", "_") + "_")) {
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

                Service serviceToList = Service.from(addEnv.substring(ALL_NODES_LIST_PREFIX.length()));

                String varName = addEnv.replace("-", "_");

                List<Node> allNodeList = new ArrayList<>(nodesConfig.getAllNodesWithService(serviceToList));
                Collections.sort(allNodeList); // THis is absolutely key, the order needs to be predictable
                String allNodes = String.join(",", allNodeList.stream().map(Node::getAddress).toArray(String[]::new));

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

    private void defineMasters(ServicesDefinition servicesDefinition, ServiceDefinition serviceDef, int nodeNbr, NodesConfigWrapper nodesConfig,
                               KubernetesServicesConfigWrapper kubeServicesConfig)
            throws NodesConfigurationException, ServiceDefinitionException {
        for (Dependency dep : serviceDef.getDependencies()) {

            ServiceDefinition masterServiceDef = servicesDefinition.getServiceDefinition(dep.getMasterService());
            if (masterServiceDef.isKubernetes()) {

                if (!serviceDef.isKubernetes()) {
                    throw new ServiceDefinitionException (SERVICE + " " + serviceDef.getName()
                            + " defines a dependency on a kube service " + masterServiceDef.getName() + " which is not supported.");
                }

                if (kubeServicesConfig == null || !kubeServicesConfig.isServiceInstallRequired(masterServiceDef)) {
                    if (dep.isMandatory()) {
                        throw new ServiceDefinitionException(SERVICE + " " + serviceDef.getName()
                                + " defines a dependency on another kube service " + masterServiceDef.getName() + " but that service is not going to be installed.");
                    }
                } else {
                    // XXX Dependeny on Kube-master is enforced already, we're left with checking that the dependency definition is not crazy
                    switch (dep.getMes()) {
                        case SAME_NODE_OR_RANDOM:
                        case RANDOM_NODE_AFTER:
                        case RANDOM_NODE_AFTER_OR_SAME:
                        case SAME_NODE:
                            throw new ServiceDefinitionException(SERVICE + " " + serviceDef.getName()
                                    + " defines a dependency on another kube service " + masterServiceDef.getName() + " if type " + dep.getMes() + " which is not suppored");
                        default:
                            break;
                    }
                }
            } else {
                defineMasters (dep, serviceDef, nodeNbr, nodesConfig);
            }
        }

    }
    private void defineMasters(Dependency dep, ServiceDefinition serviceDef, int nodeNbr,
                               NodesConfigWrapper nodesConfig)
            throws ServiceDefinitionException, NodesConfigurationException {

        Set<Node> otherMasters = new HashSet<>();
        Node node = nodesConfig.getNode(nodeNbr);

        switch (dep.getMes()) {

            case FIRST_NODE:
                for (int i = 1; i <= dep.getNumberOfMasters(); i++) {
                    Node masterNode = findFirstOtherServiceNode(nodesConfig, dep.getMasterService(), otherMasters);
                    handleMissingMaster(nodesConfig, dep, serviceDef, masterNode, i);
                    if (masterNode != null) {
                        definedMasters.put(MASTER_PREFIX + getVariableName(dep) + "_" + i, masterNode);
                        otherMasters.add(masterNode);
                    }
                }
                break;

            case SAME_NODE_OR_RANDOM:
                if ( dep.getNumberOfMasters() > 1) {
                    throw new ServiceDefinitionException (SERVICE + " " + serviceDef.getName() + " defined several master required. This is unsupported for SAME_NODE_OR_RANDOM");
                }

                String multiServiceValue = (String) nodesConfig.getValueForPath(dep.getMasterService().getName() + nodeNbr);
                if (StringUtils.isNotBlank(multiServiceValue)) {
                    definedMasters.put(SELF_MASTER_PREFIX + getVariableName(dep)+"_"+node.getEnv(), node);
                } else {
                    String uniqueServiceNbrString = (String) nodesConfig.getValueForPath(dep.getMasterService().getName());
                    if (StringUtils.isNotBlank(uniqueServiceNbrString)) {
                        definedMasters.put(SELF_MASTER_PREFIX + getVariableName(dep)+"_"+node.getEnv(), node);
                    } else {
                        Node masterNode = findFirstServiceNode(nodesConfig, dep.getMasterService());
                        handleMissingMaster(nodesConfig, dep, serviceDef, masterNode);
                        if (masterNode != null) {
                            definedMasters.put(SELF_MASTER_PREFIX + getVariableName(dep) + "_" + node.getEnv(), masterNode);
                        }
                    }
                }
                break;

            case RANDOM:
                for (int i = 1; i <= dep.getNumberOfMasters(); i++) {
                    Node masterNode = findRandomOtherServiceNode(nodesConfig, dep.getMasterService(), otherMasters);
                    handleMissingMaster(nodesConfig, dep, serviceDef, masterNode, i);
                    if (masterNode != null) {
                        definedMasters.put(MASTER_PREFIX + getVariableName(dep) + "_" + i, masterNode);
                        otherMasters.add(masterNode);
                    }
                }
                break;

            case RANDOM_NODE_AFTER:
            case RANDOM_NODE_AFTER_OR_SAME:
                if (serviceDef.isKubernetes()) {
                    throw new ServiceDefinitionException (SERVICE + " " + serviceDef.getName()
                            + " defines a " + dep.getMes() + " dependency on " + dep.getMasterService()+ ", which is not supported for kubernetes services");
                }
                if (dep.getNumberOfMasters() > 1) {
                    throw new ServiceDefinitionException (SERVICE + " " + serviceDef.getName() + " defined several master required. This is unsupported for RANDOM_NODE_AFTER");
                }

                Node masterNode = findRandomServiceNodeAfter(nodesConfig, dep.getMasterService(), nodeNbr);

                if (dep.getMes().equals(MasterElectionStrategy.RANDOM_NODE_AFTER_OR_SAME) && masterNode == null) {
                    masterNode = findRandomOtherServiceNode(nodesConfig, dep.getMasterService(), otherMasters);
                }
                handleMissingMaster(nodesConfig, dep, serviceDef, masterNode);
                if (masterNode != null) {
                    definedMasters.put(MASTER_PREFIX + getVariableName(dep) + "_" + node.getEnv(), masterNode);
                }
                break;

            case SAME_NODE:
                if (serviceDef.isKubernetes()) {
                    throw new ServiceDefinitionException (SERVICE + " " + serviceDef.getName() + " defines a SAME_NODE dependency on "
                            + dep.getMasterService() + ", which is not supported for kubernetes services");
                }
                break;

            default:
                // do nothing here. WIll be enforced by checker.
                break;
        }

    }

    String getVariableName(Dependency dep) {
        return dep.getMasterService().getEnv();
    }

    private void handleMissingMaster(ConfigurationOwner nodesConfig, Dependency dep, ServiceDefinition serviceDef, Node masterNode, int countOfMaster) throws NodesConfigurationException {
        handleMissingMasterInternal (nodesConfig, dep, masterNode,
                "Dependency " + dep.getMasterService() + " for service " + serviceDef.getName() + " could not found occurence " + countOfMaster);
    }

    private void handleMissingMaster(ConfigurationOwner nodesConfig, Dependency dep, ServiceDefinition serviceDef, Node masterNode) throws NodesConfigurationException {
        handleMissingMasterInternal (nodesConfig, dep, masterNode,
                "Dependency " + dep.getMasterService() + " for service " + serviceDef.getName() + " could not be found");
    }

    private void handleMissingMasterInternal(ConfigurationOwner nodesConfig, Dependency dep, Node masterNode, String message) throws NodesConfigurationException {
        if (masterNode == null && dep.isMandatory(nodesConfig)) {
            throw new NodesConfigurationException(message);
        }
    }


    private Node findFirstServiceNode(NodesConfigWrapper nodesConfig, Service service) throws NodesConfigurationException {
        int nodeNbr = Integer.MAX_VALUE;

        for (int candidateNbr : nodesConfig.getNodeNumbers(service)) {
            if (candidateNbr < nodeNbr) {
                nodeNbr = candidateNbr;
            }
        }

        if (nodeNbr == Integer.MAX_VALUE) {
            return null;
        }

        return findNodeNumberNode(nodesConfig, nodeNbr);
    }

    private Node findFirstOtherServiceNode(
            NodesConfigWrapper nodesConfig, Service service, Set<Node> existingMasters)
            throws NodesConfigurationException {
        int nodeNbr = Integer.MAX_VALUE;

        for (int candidateNbr : nodesConfig.getNodeNumbers(service)) {
            Node otherNode = nodesConfig.getNode(candidateNbr);
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

    private Node findRandomOtherServiceNode(NodesConfigWrapper nodesConfig, Service service, Set<Node> existingMasters) {

        // Try to find any other node running service
        for (int otherNbr : nodesConfig.getNodeNumbers(service)) {
            Node otherNode = nodesConfig.getNode (otherNbr);
            if (!existingMasters.contains(otherNode)) {
                return otherNode;
            }
        }

        return null;
    }

    private Node findRandomServiceNodeAfter(NodesConfigWrapper nodesConfig, Service service, int currentNodeNumber)
            throws NodesConfigurationException {
        int masterNumber = -1;

        for (int otherNbr : nodesConfig.getNodeNumbers(service)) {
            if (otherNbr > currentNodeNumber && (masterNumber == -1 || otherNbr < masterNumber) // try to find closest one (next in a chain)
                    ) {
                masterNumber = otherNbr;
            }
        }

        if (masterNumber == -1) {
            Set<Node> existingMasters = new HashSet<>();
            existingMasters.add (findNodeNumberNode(nodesConfig, currentNodeNumber));
            return findFirstOtherServiceNode(nodesConfig, service, existingMasters);
        } else {
            return findNodeNumberNode(nodesConfig, masterNumber);
        }
    }

    private Node findNodeNumberNode(NodesConfigWrapper nodesConfig, int nodeNumber) throws NodesConfigurationException {
        if (nodeNumber > -1) {
            // return IP address corresponding to master number
            Node node = nodesConfig.getNode(nodeNumber);
            if (node == null) {
                throw new NodesConfigurationException("Inconsistency : could not find IP of " + nodeNumber);
            }

            return node;
        }
        return null;
    }

    public Node[] getMasters(ServiceDefinition serviceDef) {

        String variableName = serviceDef.getName().toUpperCase().replace("-", "_");

        List<Node> retMasters = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Node node = definedMasters.get(MASTER_PREFIX + variableName + "_" + i);
            if (node != null) {
                retMasters.add(node);
            }
        }

        return retMasters.toArray(new Node[0]);
    }

    private void appendExport (StringBuilder sb, String variable, String value) {
        sb.append("export ");
        sb.append(variable);
        sb.append("=");
        sb.append(value);
        sb.append("\n");
    }

    public String getTopologyScript(ServicesInstallStatusWrapper serviceInstallStatus, ServicesDefinition servicesDefinition) {
        StringBuilder sb = new StringBuilder();

        sb.append("#Topology\n");
        definedMasters.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .forEach(master -> appendExport (sb, master, definedMasters.get(master).getAddress()));

        if (!serviceInstallStatus.isEmpty()) {

            sb.append("\n#Eskimo installation status\n");

            serviceInstallStatus.getInstalledServicesFlags()
                    .forEach(serviceInstallStatusFlag -> {
                        Pair<Service, Node> serviceInstall = ServicesInstallStatusWrapper.parseInstallStatusFlag(serviceInstallStatusFlag);
                        if (serviceInstall != null) {
                            Service installedService = serviceInstall.getKey();
                            Node node = serviceInstall.getValue();
                            appendExport(sb,
                                    // FIXME Make it so I get use getEnv() for service here as well
                                    "ESKIMO_INSTALLED_" + installedService.getName().replace("-", "_") + "_" + node.getEnv(),
                                    serviceInstallStatus.getValueForPathAsString(serviceInstallStatusFlag));
                        }
                    });
        }

        String userDef = Arrays.stream(servicesDefinition.listAllServices())
                .map(servicesDefinition::getServiceDefinition)
                .map(ServiceDefinition::getUser)
                .filter(Objects::nonNull)
                .map(Object::toString)
                .distinct()
                .collect(Collectors.joining(","));

        if (StringUtils.isNotBlank(userDef)) {
            sb.append("\n#Eskimo System Users\n");
            appendExport(sb, "ESKIMO_USERS", userDef);
        }

        return sb.toString();
    }

    public String getTopologyScriptForNode(
            NodesConfigWrapper nodesConfig,
            KubernetesServicesConfigWrapper kubeConfig,
            ServicesInstallStatusWrapper serviceInstallStatus,
            ServicesDefinition servicesDefinition,
            MemoryModel memoryModel, int nodeNbr) throws NodesConfigurationException {

        StringBuilder sb = new StringBuilder();
        sb.append(getTopologyScript(serviceInstallStatus, servicesDefinition));

        // find all services on node
        Set<Service> services = new HashSet<>();
        for (String key : nodesConfig.getRootKeys()) {

            ParsedNodesConfigProperty property = NodesConfigWrapper.parseProperty(key);

            if (property == null) {
                throw new NodesConfigurationException("Could not parse service config key " + key);
            }

            int otherNodeNbr = getNodeNbr(key, nodesConfig, property);

            if (otherNodeNbr == nodeNbr) {
                services.add (property.getService());
            }
        }

        sb.append("\n#Additional Environment\n");
        services.stream()
                .sorted(Comparator.naturalOrder())
                .filter(service -> additionalEnvironment.get(service) != null)
                .forEach(service -> {
                    for (String additionalProp : additionalEnvironment.get(service).keySet()) {
                        appendExport (sb, additionalProp, additionalEnvironment.get(service).get(additionalProp));
                    }
                });

        // Add self variable
        sb.append("\n#Self identification\n");
        Node node = nodesConfig.getNode(nodeNbr);
        if (node == null) {
            throw new NodesConfigurationException("No Node Address found for node number " + nodeNbr);
        }
        appendExport(sb, "SELF_IP_ADDRESS", node.getAddress());

        appendExport(sb, "SELF_NODE_NUMBER", ""+nodeNbr);

        appendExport(sb, "ESKIMO_NODE_COUNT", ""+nodesConfig.getAllNodes().size());

        if (StringUtils.isNotBlank(contextPath)) {
            appendExport(sb, CONTEXT_PATH, ""+contextPath);
        }

        List<Node> allNodeList = new ArrayList<>(nodesConfig.getAllNodes());
        Collections.sort(allNodeList); // THis is absolutely key, the order needs to be predictable
        String allNodes = String.join(",", allNodeList.stream().map(Node::getAddress).toArray(String[]::new));

        appendExport(sb, ALL_NODES_LIST, allNodes);

        // Kubernetes Topology
        if (servicesDefinition.getKubeMasterServiceDef() != null
                && nodesConfig.hasServiceConfigured(servicesDefinition.getKubeMasterServiceDef()) &&
                (   nodesConfig.isServiceOnNode(servicesDefinition.getKubeMasterServiceDef(), nodeNbr)
                 || nodesConfig.isServiceOnNode(servicesDefinition.getKubeSlaveServiceDef(), nodeNbr))) {

            sb.append("\n#Kubernetes Topology\n");

            if (kubeConfig != null) {
                for (Service service : kubeConfig.getEnabledServices()) {

                    String cpuSetting = kubeConfig.getCpuSetting(service);
                    if (StringUtils.isNotBlank(cpuSetting)) {
                        appendExport(sb, "ESKIMO_KUBE_REQUEST_" + service.getEnv() + "_CPU", cpuSetting);
                    }

                    String ramSetting = kubeConfig.getRamSetting(service);
                    if (StringUtils.isNotBlank(ramSetting)) {
                        appendExport(sb, "ESKIMO_KUBE_REQUEST_" + service.getEnv() + "_RAM", ramSetting);
                    }

                }
            }
        }

        // memory management
        Map<Service, Long> memorySettings = memoryModel.getModelForNode(nodesConfig, nodeNbr);
        if (memorySettings != null && !memorySettings.isEmpty()) {
            sb.append("\n#Memory Management\n");

            memorySettings.keySet().stream()
                    .sorted()
                    .forEach(service -> appendExport(sb,
                            "MEMORY_" + service.getEnv(), "" + memorySettings.get(service)));
        }

        return sb.toString();
    }

}
