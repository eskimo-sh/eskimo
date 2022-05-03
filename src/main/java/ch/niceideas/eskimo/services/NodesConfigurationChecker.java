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

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.model.NodesConfigWrapper.ParsedNodesConfigProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class NodesConfigurationChecker {

    private static final Pattern ipAddressCheck = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)?");

    @Autowired
    private ServicesDefinition servicesDefinition;

    void setServicesDefinition (ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }


    public void checkNodesSetup(NodesConfigWrapper nodesConfig) throws NodesConfigurationException {

        // check IP addresses and ranges configuration
        int nodeCount = checkIPAddressesAndRanges(nodesConfig);

        // foolproof bug check : make sure all ids are within node count
        checkIDSWithinNodeRanges(nodesConfig, nodeCount);

        // foolproof bug check : make sure no marathon service can be selected here
        checkNoMarathonServicesSelected(nodesConfig);

        // enforce mandatory services
        enforceMandatoryServices(nodesConfig, nodeCount);

        // enforce dependencies
        enforceDependencies(nodesConfig);
    }

    void enforceDependencies(NodesConfigWrapper nodesConfig) throws NodesConfigurationException {

        // enforce dependencies
        for (String key : nodesConfig.getServiceKeys()) {

            ParsedNodesConfigProperty property = NodesConfigWrapper.parseProperty(key);
            if (property != null && StringUtils.isNotBlank(property.getServiceName())) {

                int nodeNbr = Topology.getNodeNbr(key, nodesConfig, property);

                Service service = servicesDefinition.getService(property.getServiceName());

                for (Dependency dependency : service.getDependencies()) {

                    Service otherService = servicesDefinition.getService(dependency.getMasterService());
                    if (otherService.isKubernetes()) {
                        throw new NodesConfigurationException(
                                "Inconsistency found : Service " + property.getServiceName()
                                        + " is defining a dependency on a kubernetes service :  "
                                        + dependency.getMasterService() + ", which is disallowed");
                    }

                    // I want the dependency on same node if dependency is mandatory
                    if (dependency.getMes().equals(MasterElectionStrategy.SAME_NODE)) {

                        enforceDependencySameNode(nodesConfig, property.getServiceName(), nodeNbr, dependency);
                    }

                    // I want the dependency somewhere
                    else if (dependency.isMandatory(nodesConfig)) {

                        // ensure count of dependencies are available
                        enforceMandatoryDependency(nodesConfig, property.getServiceName(), nodeNbr, dependency);
                    }
                }
            }
        }
    }

    static void enforceMandatoryDependency(
            NodesConfigWrapper nodesConfig, String serviceName, Integer nodeNbr, Dependency dependency)
            throws NodesConfigurationException {

        // ensure count of dependencies are available
        int expectedCount = dependency.getNumberOfMasters();
        int actualCount = 0;

        for (String otherKey : nodesConfig.keySet()) {
            NodesConfigWrapper.ParsedNodesConfigProperty otherProperty = NodesConfigWrapper.parseProperty(otherKey);
            if (otherProperty != null
                    && StringUtils.isNotBlank(otherProperty.getServiceName())
                    && otherProperty.getServiceName().equals(dependency.getMasterService())) {

                // RANDOM_NODE_AFTER wants a different node, I need to check IPs
                if (nodeNbr != null && dependency.getMes().equals(MasterElectionStrategy.RANDOM_NODE_AFTER)) {

                    int otherNodeNbr = Topology.getNodeNbr(otherKey, nodesConfig, otherProperty);
                    if (otherNodeNbr == nodeNbr) {
                        continue;
                    }
                }

                actualCount++;
            }
        }

        if (actualCount < expectedCount) {
            throw new NodesConfigurationException(
                    "Inconsistency found : Service " + serviceName + " expects " + expectedCount
                            + " " + dependency.getMasterService() + " instance(s). " +
                            "But only " + actualCount + " has been found !");
        }
    }

    static void enforceDependencySameNode(
            NodesConfigWrapper nodesConfig, String serviceName, int nodeNbr, Dependency dependency)
            throws NodesConfigurationException {

        boolean serviceFound = false;
        for (String otherKey : nodesConfig.keySet()) {

            NodesConfigWrapper.ParsedNodesConfigProperty otherProperty = NodesConfigWrapper.parseProperty(otherKey);
            if (otherProperty != null
                    && StringUtils.isNotBlank(otherProperty.getServiceName())
                    && otherProperty.getServiceName().equals(dependency.getMasterService())) {

                int otherNodeNbr = Topology.getNodeNbr(otherKey, nodesConfig, otherProperty);
                if (otherNodeNbr == nodeNbr) {

                    serviceFound = true;
                }
            }
        }

        if (!serviceFound && dependency.isMandatory(nodesConfig)) {
            throw new NodesConfigurationException(
                    "Inconsistency found : Service " + serviceName + " was expecting a service " +
                            dependency.getMasterService() + " on same node, but none were found !");
        }
    }

    void enforceMandatoryServices(NodesConfigWrapper nodesConfig, int nodeCount) throws NodesConfigurationException {

        // enforce mandatory services
        for (String mandatoryServiceName : servicesDefinition.listMandatoryServices()) {

            Service mandatoryService = servicesDefinition.getService(mandatoryServiceName);

            ConditionalInstallation conditional = mandatoryService.getConditional();

            if (conditional.equals(ConditionalInstallation.NONE) ||
                    (conditional.equals(ConditionalInstallation.MULTIPLE_NODES) && nodeCount > 1)) {

                int foundNodes = 0;
                // just make sure it is installed on every node
                for (String key : nodesConfig.keySet()) {

                    ParsedNodesConfigProperty property = NodesConfigWrapper.parseProperty(key);

                    if (property != null
                            && StringUtils.isNotBlank(property.getServiceName())
                            && property.getServiceName().equals(mandatoryServiceName)) {

                        foundNodes++;
                    }
                }

                if (foundNodes != nodeCount) {
                    throw new NodesConfigurationException("Inconsistency found : service " + mandatoryServiceName
                            + " is mandatory on all nodes but some nodes are lacking it.");
                }
            }
        }
    }

    @Deprecated /* To be renamed */
    void checkNoMarathonServicesSelected(NodesConfigWrapper nodesConfig) throws NodesConfigurationException {

        // foolproof bug check : make sure no marathon service can be selected here
        for (String key : nodesConfig.keySet()) {

            ParsedNodesConfigProperty property = NodesConfigWrapper.parseProperty(key);
            if (property != null
                    && StringUtils.isNotBlank(property.getServiceName())
                    && !property.getServiceName().equals(NodesConfigWrapper.NODE_ID_FIELD)) {

                Service service = servicesDefinition.getService(property.getServiceName());
                if (service.isKubernetes()) {
                    throw new NodesConfigurationException("Inconsistency found : service " + property.getServiceName()
                            + " is a kubernetes service which should not be selectable here.");
                }
            }
        }
    }

    void checkIDSWithinNodeRanges(NodesConfigWrapper nodesConfig, int nodeCount) throws NodesConfigurationException {

        // foolproof bug check : make sure all ids are within node count
        for (String key : nodesConfig.keySet()) {

            ParsedNodesConfigProperty property = NodesConfigWrapper.parseProperty(key);
            if (property != null) {

                if (property.getNodeNumber() != null) {

                    if (!StringUtils.isNumericValue(property.getNodeNumber())) {
                        throw new NodesConfigurationException("Inconsistency found : got key " + key
                                + " with nbr " + property.getNodeNumber() + " which is not a numeric value");
                    }

                    if (property.getNodeNumber() > nodeCount) {
                        throw new NodesConfigurationException("Inconsistency found : got key " + key
                                + " which is greater than node number " + nodeCount);
                    }
                } else {
                    String nbr = (String) nodesConfig.getValueForPath(key);

                    if (!StringUtils.isNumericValue(nbr)) {
                        throw new NodesConfigurationException("Inconsistency found : got key " + key
                                + " with nbr " + nbr + " which is not a numeric value");
                    }

                    if (Integer.parseInt(nbr) > nodeCount) {
                        throw new NodesConfigurationException("Inconsistency found : got key " + key
                                + " with nbr " + nbr + " which is greater than node number " + nodeCount);
                    }
                }
            }
        }
    }

    int checkIPAddressesAndRanges(NodesConfigWrapper nodesConfig) throws NodesConfigurationException {

        int nodeCount = 0;

        // check IP addresses and ranges configuration
        for (String key : nodesConfig.getNodeAddressKeys()) {
            nodeCount++;
            int nodeNbr = Integer.parseInt(key.substring(NodesConfigWrapper.NODE_ID_FIELD.length()));
            String node = (String) nodesConfig.getValueForPath (key);
            if (StringUtils.isBlank(node)) {
                throw new NodesConfigurationException("Node "
                        + key.substring(NodesConfigWrapper.NODE_ID_FIELD.length()) + " has no IP configured.");
            } else {
                Matcher matcher = ipAddressCheck.matcher(node);
                if (!matcher.matches()) {
                    throw new NodesConfigurationException("Node " + key.substring(NodesConfigWrapper.NODE_ID_FIELD.length())
                            + " has IP configured as " + node + " which is not an IP address or a range.");
                }

                if (StringUtils.isNotBlank(matcher.group(1))) { // then it's a range

                    for (String uniqueServiceName : servicesDefinition.listUniqueServices()) {

                        // just make sure it is installed on every node
                        for (String otherKey : nodesConfig.keySet()) {

                            ParsedNodesConfigProperty otherProperty = NodesConfigWrapper.parseProperty(otherKey);
                            if (otherProperty != null
                                    && otherProperty.getServiceName().equals(uniqueServiceName)) {

                                int otherNodeNbr = Topology.getNodeNbr(otherKey, nodesConfig, otherProperty);
                                if (otherNodeNbr == nodeNbr) {
                                    throw new NodesConfigurationException("Node "
                                            + key.substring(NodesConfigWrapper.NODE_ID_FIELD.length())
                                            + " is a range an declares service " + otherProperty.getServiceName()
                                            + " which is a unique service, hence forbidden on a range.");
                                }
                            }
                        }
                    }
                }
            }
        }
        return nodeCount;
    }

}

