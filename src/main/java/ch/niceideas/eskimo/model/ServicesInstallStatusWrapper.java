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
import ch.niceideas.eskimo.services.MarathonService;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ServicesInstallStatusWrapper extends JsonWrapper implements Serializable {

    private static final Logger logger = Logger.getLogger(ServicesInstallStatusWrapper.class);

    public ServicesInstallStatusWrapper(File statusFile) throws FileException {
        super(FileUtils.readFile(statusFile));
    }

    public static ServicesInstallStatusWrapper empty() {
        return new ServicesInstallStatusWrapper("{}");
    }

    public ServicesInstallStatusWrapper(Map<String, Object> map) {
        super(new JSONObject(map));
    }

    public ServicesInstallStatusWrapper(String jsonString) {
        super(jsonString);
    }

    public boolean isServiceOK(String service, String nodeName) {
        try {
            return ("OK".equals(getValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName)));
        } catch (JSONException e) {
            logger.error(e, e);
            return false;
        }
    }

    public boolean isServiceInstalled(String service) {
        try {
            for (String ipAddress : getIpAddressesAndFlags()) {
                if ("OK".equals(getValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + ipAddress.replace(".", "-")))
                        || "restart".equals(getValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + ipAddress.replace(".", "-")))) {
                    return true;
                }
            }
            return false;
        } catch (JSONException e) {
            logger.error(e, e);
            return false;
        }
    }

    public boolean isServiceInstalled(String service, String nodeName) {
        try {
            return ("OK".equals(getValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName))
                 || "restart".equals(getValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + nodeName)));
        } catch (JSONException e) {
            logger.error(e, e);
            return false;
        }
    }

    public void copyFrom(String flag, ServicesInstallStatusWrapper other) {
        this.setValueForPath(flag, other.getValueForPath(flag));
    }

    public List<String> getInstalledServicesFlags() {
        return keySet().stream()
                .filter (installFlag -> !installFlag.startsWith("node_check_IP"))
                .collect(Collectors.toList());
    }

    public String getServiceInstallation(String serviceInstallStatusFlag) {
        return serviceInstallStatusFlag.substring(0, serviceInstallStatusFlag.indexOf("_installed"));
    }

    public List<String> getAllInstallStatusesExceptServiceOnNode(String service, String nodeName) {
        return keySet().stream()
                .filter(is -> is.startsWith("node_check_IP")
                        || !is.substring(0, is.indexOf("_installed")).equals(service)
                        || !is.contains(nodeName))
                .collect(Collectors.toList());
    }

    public Set<String> getIpAddressesAndFlags() {
        return getRootKeys().stream()
                .filter(key -> key.contains(OperationsCommand.INSTALLED_ON_IP_FLAG))
                .map(key -> key.substring(key.indexOf(OperationsCommand.INSTALLED_ON_IP_FLAG) + OperationsCommand.INSTALLED_ON_IP_FLAG.length()))
                .map(key -> key.replace("-", "."))
                .collect(Collectors.toSet());
    }

    public Set<String> getIpAddresses() {
        return getRootKeys().stream()
                .filter(key -> key.contains(OperationsCommand.INSTALLED_ON_IP_FLAG))
                .map(key -> key.substring(key.indexOf(OperationsCommand.INSTALLED_ON_IP_FLAG) + OperationsCommand.INSTALLED_ON_IP_FLAG.length()))
                .filter(key -> !key.equals(MarathonService.MARATHON_NODE))
                .map(key -> key.replace("-", "."))
                .collect(Collectors.toSet());
    }

    public String getFirstNodeName(String service) {
        List<String> allNodes = getRootKeys().stream()
                .filter(key -> key.contains(OperationsCommand.INSTALLED_ON_IP_FLAG))
                .filter(key -> key.startsWith(service + "_"))
                .map(key -> key.substring(key.indexOf(OperationsCommand.INSTALLED_ON_IP_FLAG) + OperationsCommand.INSTALLED_ON_IP_FLAG.length()))
                .collect(Collectors.toList());
        if (allNodes.size() <= 0) {
            return null;
        }
        return allNodes.get(0);
    }
}