/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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
import ch.niceideas.common.utils.StringUtils;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SystemStatusWrapper extends JsonWrapper implements Serializable {

    private static final Logger logger = Logger.getLogger(SystemStatusWrapper.class);

    public static final String SERVICE_PREFIX = "service_";

    public static final String NODE_ALIVE_FLAG = "node_alive_";

    public SystemStatusWrapper(File statusFile) throws FileException {
        super(FileUtils.readFile(statusFile));
    }

    public static SystemStatusWrapper empty() {
        return new SystemStatusWrapper("{}");
    }

    public SystemStatusWrapper(JSONObject json) {
        super(json);
    }

    public SystemStatusWrapper(Map<String, Object> map) {
        super(new JSONObject(map));
    }

    public SystemStatusWrapper(String jsonString) {
        super(jsonString);
    }

    public static String getServiceName (String serviceStatusFlag) {
        if (StringUtils.isBlank(serviceStatusFlag)) {
            return null;
        }
        if (serviceStatusFlag.startsWith(SERVICE_PREFIX)) {
            return serviceStatusFlag.substring(SERVICE_PREFIX.length(), serviceStatusFlag.indexOf('_', SERVICE_PREFIX.length() + 1));
        } else if (serviceStatusFlag.startsWith(NODE_ALIVE_FLAG)) {
            return "Node Alive";
        }
        return serviceStatusFlag;
    }

    public static String getNodeName (String serviceStatusFlag) {
        if (StringUtils.isBlank(serviceStatusFlag)) {
            return null;
        }
        return serviceStatusFlag.substring(serviceStatusFlag.indexOf('_', SERVICE_PREFIX.length() + 1) + 1);
    }

    public static String buildStatusFlag (String serviceName, String nodeName) {
        return SERVICE_PREFIX + serviceName + "_" + nodeName;
    }

    public Boolean isNodeAlive(String nodeName) {
        String nodeAliveFlag = null;
        try {
            nodeAliveFlag = (String) getValueForPath(NODE_ALIVE_FLAG + nodeName);
        } catch (JSONException e) {
            logger.debug (e, e);
            // NOSONAR
            return null;
        }
        if (StringUtils.isBlank(nodeAliveFlag)) {
            // NOSONAR
            return null;
        }
        return nodeAliveFlag.equals("OK") ? Boolean.TRUE : Boolean.FALSE;
    }

    public boolean isServiceStatusFlagOK(String serviceStatusFlag) {
        try {
            return getValueForPath(serviceStatusFlag) != null
                    && (getValueForPath(serviceStatusFlag).equals("OK")
                    || getValueForPath(serviceStatusFlag).equals("restart"));
        } catch (JSONException e) {
            logger.debug (e, e);
            return false;
        }
    }

    public boolean isServiceOKOnNode(String serviceName, String node) {
        String marathonStatus = getValueForPathAsString(SERVICE_PREFIX + serviceName  + "_" + node.replace(".", "-"));
        return StringUtils.isNotBlank(marathonStatus) && marathonStatus.equals("OK");
    }


    public boolean isServiceAvailableOnNode(String serviceName, String nodeName) {
        // make sure service for node name is found in new status
        String serviceStatus = (String) getValueForPath(SERVICE_PREFIX + serviceName + "_" + nodeName);

        // if OK reset error count
        return StringUtils.isNotBlank(serviceStatus) && !serviceStatus.equals("NA");
    }

    public Set<String> getNodes() {
        return getRootKeys().stream()
                .filter(key -> key.contains(NODE_ALIVE_FLAG))
                .map(key -> key.substring(key.indexOf(NODE_ALIVE_FLAG) + NODE_ALIVE_FLAG.length()))
                .map(key -> key.replace("-", "."))
                .collect(Collectors.toSet());
    }

    public String getFirstNodeName(String service) {
        return getRootKeys().stream()
                .filter(key -> key.startsWith(SERVICE_PREFIX + service + "_"))
                .map(key -> key.substring( (SERVICE_PREFIX + service + "_").length()))
                .findFirst().orElse(null);
    }
}