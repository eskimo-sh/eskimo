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

package ch.niceideas.eskimo.services;

import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class NodesConfigurationChecker {

    private static Pattern re = Pattern.compile("([a-zA-Z\\-_]+)([0-9]*)");

    private static Pattern ipAddressCheck = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+){0,1}");

    @Autowired
    private ServicesDefinition servicesDefinition;

    void setServicesDefinition (ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }


    public void checkServicesConfig(NodesConfigWrapper nodesConfig) throws NodesConfigurationException {

        int nodeCount = 0;

        // check IP addresses and ranges configuration
        for (String key : nodesConfig.getIpAddressKeys()) {
            nodeCount++;
            int nodeNbr = Integer.parseInt(key.substring("action_id".length()));
            String ipAddress = (String) nodesConfig.getValueForPath (key);
            if (StringUtils.isBlank(ipAddress)) {
                throw new NodesConfigurationException("Node " + key.substring(9) + " has no IP configured.");
            } else {
                Matcher matcher = ipAddressCheck.matcher(ipAddress);
                if (!matcher.matches()) {
                    throw new NodesConfigurationException("Node " + key.substring(9) + " has IP configured as " + ipAddress + " which is not an IP address or a range.");
                }

                if (StringUtils.isNotBlank(matcher.group(1))) { // then it's a range

                    for (String uniqueServiceName : servicesDefinition.listUniqueServices()) {

                        // just make sure it is installed on every node
                        for (String otherKey : nodesConfig.keySet()) {
                            Matcher otherMatcher = re.matcher(otherKey);

                            if (otherMatcher.matches() && otherMatcher.groupCount() >= 1) {

                                String serviceName = otherMatcher.group(1);
                                if (serviceName.equals(uniqueServiceName)) {

                                    int otherNodeNbr = Topology.getNodeNbr(otherKey, nodesConfig, otherMatcher);
                                    if (otherNodeNbr == nodeNbr) {
                                        throw new NodesConfigurationException("Node " + key.substring(9) + " is a range an declares service " + serviceName + " which is a unique service, hence forbidden on a range.");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // foolproof bug check : make sure all ids are within node count
        for (String key : nodesConfig.keySet()) {

            Matcher matcher = re.matcher(key);

            if (matcher.matches()) {

                if (matcher.groupCount() >= 2 && StringUtils.isNotBlank(matcher.group(2))) {

                    if (!StringUtils.isNumericValue(matcher.group(2))) {
                        throw new NodesConfigurationException("Inconsistency found : got key " + key + " with nbr " + matcher.group(2) + " which is not a numeric value");
                    }

                    if (Integer.parseInt(matcher.group(2)) > nodeCount) {
                        throw new NodesConfigurationException("Inconsistency found : got key " + key + " which is greater than node number " + nodeCount);
                    }
                } else {
                    String nbr = (String) nodesConfig.getValueForPath(key);

                    if (!StringUtils.isNumericValue(nbr)) {
                        throw new NodesConfigurationException("Inconsistency found : got key " + key + " with nbr " + nbr + " which is not a numeric value");
                    }

                    if (Integer.parseInt(nbr) > nodeCount) {
                        throw new NodesConfigurationException("Inconsistency found : got key " + key + " with nbr " + nbr + " which is greater than node number " + nodeCount);
                    }
                }
            }
        }

        // enforce mandatory nodes
        for (String mandatoryServiceName : servicesDefinition.listMandatoryServices()) {

            Service mandatoryService = servicesDefinition.getService(mandatoryServiceName);

            ConditionalInstallation conditional = mandatoryService.getConditional();

            if (conditional.equals(ConditionalInstallation.NONE) ||
                    (conditional.equals(ConditionalInstallation.MULTIPLE_NODES) && nodeCount > 1)) {

                int foundNodes = 0;
                // just make sure it is installed on every node
                for (String key : nodesConfig.keySet()) {
                    Matcher matcher = re.matcher(key);

                    if (matcher.matches() && matcher.groupCount() >= 1) {

                        String serviceName = matcher.group(1);
                        if (serviceName.equals(mandatoryServiceName)) {
                            foundNodes++;
                        }
                    }
                }

                if (foundNodes != nodeCount) {
                    throw new NodesConfigurationException("Inconsistency found : service " + mandatoryServiceName + " is mandatory on all nodes but some nodes are lacking it.");
                }
            }
        }

        // enforce dependencies
        for (String key : nodesConfig.getServiceKeys()) {
            Matcher matcher = re.matcher(key);

            if (matcher.matches()) {
                if (matcher.groupCount() >= 1) {

                    String serviceName = matcher.group(1);
                    int nodeNbr = Topology.getNodeNbr(key, nodesConfig, matcher);

                    Service service = servicesDefinition.getService(serviceName);

                    for (Dependency dependency : service.getDependencies()) {

                        // I want the dependency on same node if dependency is mandatory
                        if (dependency.getMes().equals(MasterElectionStrategy.SAME_NODE)) {

                            boolean serviceFound = false;
                            for (String otherKey : nodesConfig.keySet()) {
                                Matcher otherMatcher = re.matcher(otherKey);

                                if (otherMatcher.matches() && otherMatcher.groupCount() >= 1) {

                                    String otherServiceName = otherMatcher.group(1);
                                    if (otherServiceName.equals(dependency.getMasterService())) {

                                        int otherNodeNbr = Topology.getNodeNbr(otherKey, nodesConfig, otherMatcher);
                                        if (otherNodeNbr == nodeNbr) {

                                            serviceFound = true;
                                        }
                                    }
                                }
                            }

                            if (!serviceFound && dependency.isMandatory()) {
                                throw new NodesConfigurationException(
                                        "Inconsistency found : Service " + serviceName + " was expecting a service " +
                                                dependency.getMasterService() + " on same node, but none were found !");
                            }
                        }

                        // I want the dependency somewhere
                        else if (dependency.isMandatory()) {

                            // ensure count of dependencies are available
                            int expectedCount = dependency.getNumberOfMasters();
                            int actualCount = 0;

                            for (String otherKey : nodesConfig.keySet()) {
                                Matcher otherMatcher = re.matcher(otherKey);

                                if (otherMatcher.matches() && otherMatcher.groupCount() >= 1) {

                                    String otherServiceName = otherMatcher.group(1);
                                    if (otherServiceName.equals(dependency.getMasterService())) {

                                        // RANDOM_NODE_AFTER wants a different node, I need to check IPs
                                        if (dependency.getMes().equals(MasterElectionStrategy.RANDOM_NODE_AFTER)) {

                                            int otherNodeNbr = Topology.getNodeNbr(otherKey, nodesConfig, otherMatcher);
                                            if (otherNodeNbr == nodeNbr) {
                                                continue;
                                            }
                                        }

                                        actualCount++;
                                    }
                                }
                            }

                            if (actualCount < expectedCount) {
                                throw new NodesConfigurationException(
                                        "Inconsistency found : Service " + serviceName + " expects " + expectedCount
                                                + " " + dependency.getMasterService() + " instance(s). " +
                                                "But only " + actualCount + " has been found !");
                            }
                        }
                    }
                }
            }
        }
    }

}

