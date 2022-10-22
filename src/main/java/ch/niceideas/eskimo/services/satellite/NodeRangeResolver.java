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

package ch.niceideas.eskimo.services.satellite;

import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper.ParsedNodesConfigProperty;
import ch.niceideas.eskimo.model.Topology;
import ch.niceideas.eskimo.services.NodesConfigurationException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class NodeRangeResolver  {

    private static final Logger logger = Logger.getLogger(NodeRangeResolver.class);

    public static final String NODE_ID_FLAG = NodesConfigWrapper.NODE_ID_FIELD;

    public NodesConfigWrapper resolveRanges(NodesConfigWrapper rawNodesConfig) throws NodesConfigurationException {

        NodesConfigWrapper retNodesConfig = NodesConfigWrapper.empty();

        // This is used to check there is no overlap
        Set<String> nodes = new HashSet<>();

        // 1. build set of Numbers that have to be resolved
        Map<Integer, List<String>> toBeResolvedNumbers = new HashMap<>();

        Integer maxNodeNbr = 0;

        for (String key : rawNodesConfig.getNodeAddressKeys()) {
            String value = (String) rawNodesConfig.getValueForPath(key);
            Integer nodeNbr = Integer.valueOf(key.substring(NODE_ID_FLAG.length()));
            if (nodeNbr.compareTo(maxNodeNbr) > 0) {
                maxNodeNbr = nodeNbr;
            }

            if (value.contains("-")) { // it's a range
                toBeResolvedNumbers.put(nodeNbr, new ArrayList<>());
            }
        }

        // 2. Generate retConfig
        for (String key : rawNodesConfig.getRootKeys()) {
            ParsedNodesConfigProperty property = Topology.parseKeyToServiceConfig(key, rawNodesConfig);

            String value = (String) rawNodesConfig.getValueForPath(key);

            if (toBeResolvedNumbers.containsKey(property.getNodeNumber())) {
                if (key.startsWith(NODE_ID_FLAG)) {

                    Map<String, String> generatedConfig = generateRange(rawNodesConfig, property.getNodeNumber(), maxNodeNbr, value);

                    if (generatedConfig.size() <= 0) {
                        throw new NodesConfigurationException("Range resolves to empty address set : " + value);
                    }

                    for (Map.Entry<String, String> entry: generatedConfig.entrySet()) {
                        String generatedKey = entry.getKey();
                        String generatedValue = entry.getValue();

                        if (generatedKey.startsWith(NODE_ID_FLAG)) {
                            checkNode(nodes, generatedValue);
                            Integer newNnodeNbr = Integer.valueOf(generatedKey.substring(NODE_ID_FLAG.length()));
                            if (newNnodeNbr.compareTo(maxNodeNbr) > 0) {
                                maxNodeNbr = newNnodeNbr;
                            }
                        }

                        retNodesConfig.setValueForPath(generatedKey, generatedValue);
                    }
                }
            } else {
                if (key.startsWith(NODE_ID_FLAG)) {
                    checkNode(nodes, value);
                }

                retNodesConfig.setValueForPath(key, value);
            }
        }

        return retNodesConfig;
    }

    void checkNode(Set<String> nodes, String value) throws NodesConfigurationException {
        if (nodes.contains(value)) {
            throw new NodesConfigurationException("Configuration is illegal. Node Address " + value + " is referenced by multiple ranges / nodes");
        } else {
            nodes.add (value);
        }
    }

    private Map<String, String> generateRange(
            NodesConfigWrapper rawNodesConfig, Integer rangeNodeNbr, Integer maxNodeNbr, String rangeValue)
            throws NodesConfigurationException {

        Map<String, String> generatedConfig = new HashMap<>();

        for (String key : rawNodesConfig.getRootKeys()) {
            ParsedNodesConfigProperty property = Topology.parseKeyToServiceConfig(key, rawNodesConfig);

            String value = (String) rawNodesConfig.getValueForPath(key);

            if (property.getNodeNumber().equals(rangeNodeNbr)) {

                List<String> rangeIps = generateRangeIps (rangeValue);

                boolean first = true;
                int actualNbr = -1;
                for (String node : rangeIps) {
                    if (first) {
                        actualNbr = rangeNodeNbr;
                    } else {
                        actualNbr++;
                    }

                    String newKey = key.endsWith(rangeNodeNbr.toString()) ? property.getServiceName() + actualNbr : property.getServiceName();
                    String newValue;
                    if (key.endsWith(rangeNodeNbr.toString())) {
                        newValue = key.startsWith(NODE_ID_FLAG) ? node : value;
                    } else {
                        newValue = ""+actualNbr;
                    }
                    generatedConfig.put(newKey, newValue);

                    if (first) {
                        first = false;
                        actualNbr = maxNodeNbr;
                    }
                }
            }
        }

        return generatedConfig;
    }

    List<String> generateRangeIps(String rangeValue) {

        List<String> retList = new ArrayList<>();

        String startIp = rangeValue.substring(0, rangeValue.indexOf('-'));
        String endIp = rangeValue.substring(rangeValue.indexOf('-') + 1);

        int startIpInt = ipToInt(startIp);
        int endIpInt = ipToInt(endIp);

        for (int i = startIpInt; i <= endIpInt; i++) {
            retList.add (toInetAddress(i));
        }

        return retList;
    }

    static int ipToInt(String ipAddr) {
        int compacted = 0;
        byte[] bytes = new byte[0];
        try {
            bytes = InetAddress.getByName(ipAddr).getAddress();
        } catch (UnknownHostException e) {
            logger.error (e, e);
            throw new NodeRangeResolverException(e);
        }
        for (int i=0 ; i<bytes.length ; i++) {
            compacted += (bytes[i] * Math.pow(256.0,(double)4-i-1));
        }
        return compacted;
    }

    private static String toInetAddress(int ip) {
        return (( (ip >> 24) & 0xFF ) + 1) + "." + ( ( ip >> 16 ) & 0xFF ) + "." + ( ( ip >> 8 ) & 0xFF ) + "." + (ip & 0xFF );
    }


    public static class NodeRangeResolverException extends RuntimeException {

        static final long serialVersionUID = -3317632123124229248L;

        NodeRangeResolverException(Throwable cause) {
            super(cause);
        }
    }
}
