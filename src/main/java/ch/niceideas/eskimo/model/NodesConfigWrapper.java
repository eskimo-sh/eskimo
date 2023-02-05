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

import ch.niceideas.common.exceptions.CommonRTException;
import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.services.SystemException;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import ch.niceideas.eskimo.types.Node;
import ch.niceideas.eskimo.types.Service;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NodesConfigWrapper extends JsonWrapper implements Serializable, ConfigurationOwner {

    private static final Logger logger = Logger.getLogger(NodesConfigWrapper.class);

    private static final Pattern nodesConfigPropertyRE = Pattern.compile("([a-zA-Z0-9\\-_]*[a-zA-Z\\-_]+)([0-9]*)");

    public static final String NODE_ID_FIELD = "node_id";

    public static NodesConfigWrapper empty() {
        return new NodesConfigWrapper("{}");
    }

    public NodesConfigWrapper(Map<String, Object> map) {
        super(new JSONObject(map));
    }

    public NodesConfigWrapper(String jsonString) {
        super(jsonString);
    }

    public boolean isServiceOnNode(Service service, int nodeNbr) {
        return getNodeNumbers(service).contains(nodeNbr);
    }

    public boolean hasServiceConfigured(Service service) {
        try {
            return this.keySet().stream()
                    .map(NodesConfigWrapper::parseProperty)
                    .filter(Objects::nonNull)
                    .map (ParsedNodesConfigProperty::getService)
                    .anyMatch(propertyService -> propertyService.equals (service));
        } catch (JSONException e) {
            logger.debug (e, e);
            return false;
        }
    }

    public List<String> getNodeAddressKeys() {
        return getRootKeys().stream()
                .filter(key -> key.startsWith(NODE_ID_FIELD))
                .collect(Collectors.toList());
    }

    public long countNodes() {
        return getRootKeys().stream()
                .filter(key -> key.startsWith(NODE_ID_FIELD))
                .count();
    }

    public long countServices() {
        return getRootKeys().stream()
                .filter(key -> !key.contains(NODE_ID_FIELD))
                .map(NodesConfigWrapper::parseProperty)
                .filter(Objects::nonNull)
                .map (ParsedNodesConfigProperty::getService)
                .distinct().count();
    }

    public List<String> getServiceKeys() {
        return getRootKeys().stream()
                .filter(key -> !key.contains(NODE_ID_FIELD))
                .collect(Collectors.toList());
    }

    public Node getNode(int nodeNbr) {
        String nodeAddress = (String) getValueForPath(NODE_ID_FIELD + nodeNbr);
        if (StringUtils.isBlank(nodeAddress)) {
            throw new IllegalArgumentException("No node with number " + nodeNbr);
        }
        return Node.fromAddress(nodeAddress);
    }

    public List<Node> getAllNodesWithService(Service service) {
        return getRootKeys().stream()
                .map (key -> {
                    try {
                        return Topology.parseKeyToServiceConfig(key, this);
                    } catch (NodesConfigurationException | JSONException e) {
                        logger.debug (e, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(serviceConfig -> serviceConfig.getService().equals(service))
                .map(serviceConfig -> {
                    try {
                        return getNode (serviceConfig.getNodeNumber());
                    } catch (JSONException e) {
                        logger.debug (e, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Pair<Integer, Node>> getNodes() {
        List<Pair<Integer, Node>> retList = new ArrayList<>();

        getNodeAddressKeys()
                .forEach(key -> {
                    try {
                        retList.add(new Pair<>(
                                Integer.valueOf(key.substring(NODE_ID_FIELD.length())),
                                Node.fromAddress((String) getValueForPath(key))));
                    } catch (JSONException e) {
                        throw new NodesConfigWrapperException(e);
                    }
                });

        return retList;
    }

    public List<Service> getServicesForNode(Node node) throws SystemException {
        return getServicesForNode(getNodeNumber(node));
    }

    public static ParsedNodesConfigProperty parseProperty (String property) {
        Matcher matcher = nodesConfigPropertyRE.matcher(property);

        if (matcher.matches()) {

            if (matcher.groupCount() >= 2 && StringUtils.isNotBlank(matcher.group(2))) {

                return new ParsedNodesConfigProperty (
                        Service.from(matcher.group(1)),
                        Integer.valueOf (matcher.group(2)));

            } else if (matcher.groupCount() >= 1) {

                return new ParsedNodesConfigProperty (
                        Service.from(matcher.group(1))
                        , null);

            } else {

                return new ParsedNodesConfigProperty (null, null);
            }
        }

        return null;
    }

    public List<Service> getServicesForNode (int number) {
        return getServiceKeys().stream()
                .map (key -> {
                    try {
                        return Topology.parseKeyToServiceConfig(key, this);
                    } catch (NodesConfigurationException | JSONException e) {
                        logger.debug (e, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(serviceConfig -> serviceConfig.getNodeNumber() == number)
                .map(ParsedNodesConfigProperty::getService)
                .collect(Collectors.toList());
    }

    public int getNodeNumber(Node node) throws SystemException {
        for (String key : getRootKeys()) {
            if (key.startsWith(NODE_ID_FIELD)) {

                try {
                    if (getValueForPath(key).equals(node.getAddress())) {
                        return Integer.parseInt(key.substring(NODE_ID_FIELD.length()));
                    }
                } catch (JSONException e) {
                    logger.error (e, e);
                    throw new SystemException(e);
                }
            }
        }
        throw new SystemException("Impossible to find node number for node " + node);
    }

    public boolean shouldInstall(Service service, int nodeNbr) {

        boolean shall = false;


        if (
            // First case : unique service
            (getValueForPath(service.getName()) != null && getValueForPath(service.getName()).equals("" + nodeNbr))
            ||
            // second case multiple service
            (getValueForPath(service.getName() + nodeNbr) != null && getValueForPath(service.getName() + nodeNbr).equals("on"))) {
            shall = true;
        }

        return shall;
    }

    public List<Node> getAllNodes() {
        return getNodeAddressKeys().stream()
                .map(key -> {
                    try {
                        return (String) getValueForPath(key);
                    } catch (JSONException e) {
                        logger.debug (e, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(Node::fromAddress)
                .sorted()
                .collect(Collectors.toList());
    }

    public List<Integer> getNodeNumbers(Service service) {
        return getRootKeys().stream()
                .map(key -> {
                    try {
                        return Topology.parseKeyToServiceConfig(key, this);
                    } catch (NodesConfigurationException | JSONException e) {
                        logger.error(e, e);
                        throw new TopologyException(e);
                    }
                })
                .filter(result -> result.getService().equals(service))
                .map(result -> result.getNodeNumber() == null ? -1 : result.getNodeNumber())
                .filter (value -> value != -1)
                .sorted()
                .collect(Collectors.toList());
    }

    public Node getFirstNode(Service service) {
        List<Node> nodeAddresses = getAllNodesWithService(service);
        if (!nodeAddresses.isEmpty()) {
            return nodeAddresses.get(0);
        }
        return null;
    }

    private static final class NodesConfigWrapperException extends CommonRTException {

        public NodesConfigWrapperException (Throwable cause) {
            super (cause);
        }

    }

    public static final class ParsedNodesConfigProperty {

        private final Service service;
        private final Integer nodeNumber;

        public ParsedNodesConfigProperty(Service service, Integer nodeNumber) {
            this.service = service;
            this.nodeNumber = nodeNumber;
        }

        public Service getService() {
            return service;
        }

        public Integer getNodeNumber() {
            return nodeNumber;
        }
    }
}