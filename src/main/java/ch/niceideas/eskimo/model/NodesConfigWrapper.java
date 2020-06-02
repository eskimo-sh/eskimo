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

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.FileUtils;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.services.NodesConfigurationException;
import ch.niceideas.eskimo.services.SystemException;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NodesConfigWrapper extends JsonWrapper implements Serializable {

    private static final Logger logger = Logger.getLogger(NodesConfigWrapper.class);

    private static final Pattern nodesConfigPropertyRE = Pattern.compile("([a-zA-Z\\-_]+)([0-9]*)");

    public static final String NODE_ID_FIELD = "node_id";

    public NodesConfigWrapper(File statusFile) throws FileException {
        super(FileUtils.readFile(statusFile));
    }

    public static NodesConfigWrapper empty() {
        return new NodesConfigWrapper("{}");
    }

    public NodesConfigWrapper(JSONObject json) {
        super(json);
    }

    public NodesConfigWrapper(Map<String, Object> map) {
        super(new JSONObject(map));
    }

    public NodesConfigWrapper(String jsonString) {
        super(jsonString);
    }

    public boolean hasServiceConfigured(String serviceName) {
        try {
            return this.keySet().stream()
                    .anyMatch(serviceName::equals);
        } catch (JSONException e) {
            logger.debug (e, e);
            return false;
        }
    }

    public List<String> getIpAddressKeys() {
        return getRootKeys().stream()
                .filter(key -> key.startsWith(NODE_ID_FIELD))
                .collect(Collectors.toList());
    }

    public List<String> getServiceKeys() {
        return getRootKeys().stream()
                .filter(key -> !key.contains(NODE_ID_FIELD))
                .collect(Collectors.toList());
    }

    public String getNodeAddress(int nodeNbr) {
        return (String) getValueForPath(NODE_ID_FIELD + nodeNbr);
    }

    public String getNodeName(int nodeNbr) {
        String nodeAddress = (String) getValueForPath(NODE_ID_FIELD + nodeNbr);
        if (StringUtils.isNotBlank(nodeAddress)) {
            return nodeAddress.replace(".", "-");
        }
        return null;
    }

    public List<String> getAllNodeAddressesWithService(String serviceName) {
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
                .filter(serviceConfig -> serviceConfig.getServiceName().equals(serviceName))
                .map(serviceConfig -> {
                    try {
                        return getNodeAddress (serviceConfig.getNodeNumber());
                    } catch (JSONException e) {
                        logger.debug (e, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Pair<String, String>> getNodeAdresses() {
        List<Pair<String, String>> retList = new ArrayList<>();

        getIpAddressKeys()
                .forEach(key -> {
                    try {
                        retList.add(new Pair<>(key.substring(NODE_ID_FIELD.length()), (String) getValueForPath(key)));
                    } catch (JSONException e) {
                        throw new NodesConfigWrapperException(e);
                    }
                });

        return retList;
    }

    public List<String> getServicesForIpAddress (String ipAddress) throws SystemException {
        return getServicesForNode(getNodeNumber(ipAddress));
    }

    public static ParsedNodesConfigProperty parseProperty (String property) {
        Matcher matcher = nodesConfigPropertyRE.matcher(property);

        if (matcher.matches()) {

            if (matcher.groupCount() >= 2 && StringUtils.isNotBlank(matcher.group(2))) {

                return new ParsedNodesConfigProperty (matcher.group(1), Integer.valueOf (matcher.group(2)));

            } else if (matcher.groupCount() >= 1) {

                return new ParsedNodesConfigProperty (matcher.group(1), null);

            } else {

                return new ParsedNodesConfigProperty (null, null);
            }
        }

        return null;
    }

    public List<String> getServicesForNode (int number) {
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
                .map(ParsedNodesConfigProperty::getServiceName)
                .collect(Collectors.toList());
    }

    public int getNodeNumber(String ipAddress) throws SystemException {
        for (String key : getRootKeys()) {
            if (key.startsWith(NODE_ID_FIELD)) {

                try {
                    if (getValueForPath(key).equals(ipAddress)) {
                        return Integer.parseInt(key.substring(NODE_ID_FIELD.length()));
                    }
                } catch (JSONException e) {
                    logger.error (e, e);
                    throw new SystemException(e);
                }
            }
        }
        throw new SystemException("Impossible to find node number for ipAddress " + ipAddress);
    }

    public boolean shouldInstall(String service, int nodeNbr) {

        boolean shall = false;


        if (
            // First case : unique service
            (getValueForPath(service) != null && getValueForPath(service).equals("" + nodeNbr))
            ||
            // second case multiple service
            (getValueForPath(service + nodeNbr) != null && getValueForPath(service + nodeNbr).equals("on"))) {
            shall = true;
        }

        return shall;
    }

    public List<String> getIpAddresses() {
        return getIpAddressKeys().stream()
                .map(key -> {
                    try {
                        return (String) getValueForPath(key);
                    } catch (JSONException e) {
                        logger.debug (e, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Integer> getNodeNumbers(String service) {
        return getRootKeys().stream()
                .map(key -> {
                    try {
                        return Topology.parseKeyToServiceConfig(key, this);
                    } catch (NodesConfigurationException | JSONException e) {
                        logger.error(e, e);
                        throw new TopologyException(e);
                    }
                })
                .filter(result -> result.getServiceName().equals(service))
                .map(result -> result.getNodeNumber() == null ? -1 : result.getNodeNumber())
                .filter (value -> value != -1)
                .collect(Collectors.toList());
    }

    public String getFirstNodeName(String serviceName) {
        List<String> nodeAddresses = getAllNodeAddressesWithService(serviceName);
        if (!nodeAddresses.isEmpty()) {
            return nodeAddresses.get(0).replace(".", "-");
        }
        return null;
    }

    private static final class NodesConfigWrapperException extends RuntimeException {

        public NodesConfigWrapperException (Throwable cause) {
            super (cause);
        }

    }

    public static final class ParsedNodesConfigProperty {

        private final String serviceName;
        private final Integer nodeNumber;

        public ParsedNodesConfigProperty(String serviceName, Integer nodeNumber) {
            this.serviceName = serviceName;
            this.nodeNumber = nodeNumber;
        }

        public String getServiceName() {
            return serviceName;
        }

        public Integer getNodeNumber() {
            return nodeNumber;
        }
    }
}