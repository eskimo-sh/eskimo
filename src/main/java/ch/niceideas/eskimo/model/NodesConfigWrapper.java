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
import ch.niceideas.eskimo.services.NodesConfigurationException;
import ch.niceideas.eskimo.services.SystemException;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class NodesConfigWrapper extends JsonWrapper implements Serializable {

    private static final Logger logger = Logger.getLogger(NodesConfigWrapper.class);

    public static final String ACTION_ID_FIELD = "action_id";

    public NodesConfigWrapper(File statusFile) throws FileException, JSONException {
        super(FileUtils.readFile(statusFile));
    }

    public static NodesConfigWrapper empty() throws JSONException{
        return new NodesConfigWrapper("{}");
    }

    public NodesConfigWrapper(JSONObject json) throws JSONException {
        super(json);
    }

    public NodesConfigWrapper(Map<String, Object> map) throws JSONException {
        super(new JSONObject(map));
    }

    public NodesConfigWrapper(String jsonString) throws JSONException {
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
                .filter(key -> key.startsWith(ACTION_ID_FIELD))
                .collect(Collectors.toList());
    }

    public List<String> getServiceKeys() {
        return getRootKeys().stream()
                .filter(key -> !key.contains(ACTION_ID_FIELD))
                .collect(Collectors.toList());
    }

    public String getNodeAddress(int nodeNbr) throws JSONException {
        return (String) getValueForPath(ACTION_ID_FIELD + nodeNbr);
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
                .filter(serviceConfig -> serviceConfig.getKey().equals(serviceName))
                .map(serviceConfig -> {
                    try {
                        return getNodeAddress (serviceConfig.getValue());
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
                        retList.add(new Pair<>(key.substring(ACTION_ID_FIELD.length()), (String) getValueForPath(key)));
                    } catch (JSONException e) {
                        throw new RuntimeException();
                    }
                });

        return retList;
    }

    public List<String> getServicesForIpAddress (String ipAddress) throws SystemException {
        return getServicesForNode(getNodeNumber(ipAddress));
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
                .filter(serviceConfig -> serviceConfig.getValue() == number)
                .map(Pair::getKey)
                .collect(Collectors.toList());
    }

    public int getNodeNumber(String ipAddress) throws SystemException {
        for (String key : getRootKeys()) {
            if (key.startsWith(ACTION_ID_FIELD)) {

                try {
                    if (getValueForPath(key).equals(ipAddress)) {
                        return Integer.parseInt(key.substring(ACTION_ID_FIELD.length()));
                    }
                } catch (JSONException e) {
                    logger.error (e, e);
                    throw new SystemException(e);
                }
            }
        }
        throw new SystemException("Impossible to find node number for ipAddress " + ipAddress);
    }

    public boolean shouldInstall(String service, int nodeNbr) throws JSONException {

        boolean shall = false;

        // First case : unique service
        if (getValueForPath(service) != null && getValueForPath(service).equals("" + nodeNbr)) {
            shall = true;
        }
        // second case multiple service
        else if (getValueForPath(service + nodeNbr) != null && getValueForPath(service + nodeNbr).equals("on")) {
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

    public List<Integer> getNodeNumbers(String service) throws JSONException {
        return getRootKeys().stream()
                .map(key -> {
                    try {
                        return Topology.parseKeyToServiceConfig(key, this);
                    } catch (NodesConfigurationException | JSONException e) {
                        logger.error(e, e);
                        throw new TopologyException(e);
                    }
                })
                .filter(result -> result.getKey().equals(service))
                .map(result -> result.getValue() == null ? -1 : result.getValue())
                .filter (value -> value != -1)
                .collect(Collectors.toList());
    }
}